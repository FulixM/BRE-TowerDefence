package io.felixmenzel.towerdefence.simulation

import java.util.Collections

/** Pure Kotlin game rules. Rendering and input never own gold, health, or damage. */
class GameSimulation(
    val battleMap: BattleMap = BattleMap.CORRIDOR,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val seed: Int? = null,
    val fieldRank: Int = 1,
    tremorModule: TremorModule = TremorModule.STANDARD,
    val mode: MissionMode = MissionMode.CAMPAIGN,
    val railModule: RailModule = RailModule.STANDARD,
    research: ResearchLoadout = ResearchLoadout.EMPTY,
    val elevatedTerrain: Boolean = mode == MissionMode.CAMPAIGN,
) {
    init {
        require(mode.supports(battleMap, seed) && (!elevatedTerrain || mode == MissionMode.CAMPAIGN))
    }

    val research = if (ResearchLoadout.appliesTo(mode, seed)) research else ResearchLoadout.EMPTY
    val terrain = if (elevatedTerrain) BattleTerrain.forMap(battleMap, seed) else BattleTerrain.FLAT
    fun baseRange(type: TowerType): Double = terrain.baseRange(research.rangeFor(type))
    val startingGold = mode.startingGold(difficulty) + this.research.startingGoldBonus
    val tremorModule = if (fieldRank >= TremorModule.REQUIRED_RANK) tremorModule else TremorModule.STANDARD
    var useUpperRoute = false
        private set
    val path: List<Waypoint> get() = if (useUpperRoute) checkNotNull(battleMap.alternatePath) else battleMap.path
    val inactivePath: List<Waypoint>? get() = if (useUpperRoute) battleMap.path else battleMap.alternatePath
    val challenge: RemixChallenge? = seed?.takeIf { it in RemixChallenge.SEED_RANGE }?.let {
        RemixChallenge(battleMap, difficulty, it, elevatedTerrain)
    }
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableTowers = mutableListOf<Tower>()
    private val mutableShots = mutableListOf<Shot>()
    val enemies: List<Enemy> = Collections.unmodifiableList(mutableEnemies)
    val towers: List<Tower> = Collections.unmodifiableList(mutableTowers)
    val shots: List<Shot> = Collections.unmodifiableList(mutableShots)
    private val mutableWaveReports = mutableListOf<WaveReport>()
    val waveReports: List<WaveReport> = Collections.unmodifiableList(mutableWaveReports)
    var tick = 0L
        private set
    var gold = startingGold
        private set
    var lives = difficulty.startingLives
        private set
    var wave = 0
        private set
    var kills = 0
        private set
    var leakedEnemies = 0
        private set
    val totalWaves = mode.waveCount
    private val corridorCampaign = mode == MissionMode.CAMPAIGN && battleMap == BattleMap.CORRIDOR
    private val waves = List(totalWaves) { index ->
        val number = index + 1
        val plan = mode.plan(number, seed)
        if (corridorCampaign && number == Marshal.WAVE) plan.withMarshal() else plan
    }
    private val bossSlot = if (corridorCampaign) {
        waves[Bastion.WAVE - 1].enemies.indexOfLast { it == EnemyType.ARMORED }
    } else -1
    val bossWindowWave: Int? get() {
        if (!corridorCampaign) return null
        val number = when (state) {
            GameState.PREPARING -> wave + 1
            GameState.RUNNING -> wave
            else -> return null
        }
        return number.takeIf { it == Bastion.WAVE || it == Marshal.WAVE }
    }
    val showBossWindows: Boolean get() = bossWindowWave != null
    var bossBreached = false
        private set
    val activeBoss: Enemy? get() = enemies.firstOrNull { it.isBoss && it.isAlive && !it.hasReachedGoal }
    private val bossWaveDescription = if (bossSlot >= 0) waves[Bastion.WAVE - 1].describe(replaceBulwarkWithBoss = true) else null
    var state = GameState.PREPARING
        private set
    var remainingToSpawn = 0
        private set
    var eliteOffered = false
        private set
    var eliteActive = false
        private set
    val canOfferElite: Boolean get() = mode == MissionMode.CAMPAIGN && state == GameState.PREPARING && wave + 1 == ELITE_WAVE
    val waveReward: Int get() = rewardFor(wave, eliteActive)
    val nextWaveReward: Int get() = rewardFor(wave + 1, eliteOffered)
    private var waveLeaks = 0
    private var waveEchoHits = 0
    val nextWaveDescription: String get() = waveDescription((wave + 1).coerceAtMost(totalWaves))
    private var nextEnemyId = 1L
    private var nextTowerId = 1L
    private var spawnedThisWave = 0
    private var nextSpawnTick = 0L
    internal val echo = WaveEcho(terrain)

    fun update() {
        tick++
        mutableShots.removeAll { tick - it.tick > SHOT_LIFETIME_TICKS }
        if (state != GameState.RUNNING) return

        if (remainingToSpawn > 0 && tick >= nextSpawnTick) {
            spawnEnemy()
            nextSpawnTick = tick + waves[wave - 1].intervals[spawnedThisWave - 1]
        }
        for (enemy in mutableEnemies) enemy.update(STEP_SECONDS)
        collectDefeatedEnemies()
        // A leak is resolved before towers fire: reaching the exit is definitive.
        val escaped = mutableEnemies.count { it.isAlive && it.hasReachedGoal }
        if (escaped > 0) {
            bossBreached = mutableEnemies.any { it.isBoss && it.isAlive && it.hasReachedGoal }
            leakedEnemies += escaped
            waveLeaks += escaped
            lives = (lives - escaped).coerceAtLeast(0)
            mutableEnemies.removeAll { it.isAlive && it.hasReachedGoal }
        }
        if (lives == 0 || bossBreached) {
            recordWave(cleared = false)
            state = GameState.LOST
            remainingToSpawn = 0
            mutableShots.clear()
            echo.reset()
            return
        }
        for (tower in mutableTowers) {
            val shot = tower.update(tick, mutableEnemies) ?: continue
            mutableShots.add(shot)
            echo.record(tower, shot)
        }
        waveEchoHits += echo.replay(tick, mutableEnemies, mutableShots)
        collectDefeatedEnemies()
        if (remainingToSpawn == 0 && mutableEnemies.isEmpty()) {
            gold += waveReward
            recordWave(cleared = true)
            state = if (wave == totalWaves) GameState.WON else GameState.PREPARING
            if (state == GameState.WON) echo.reset() else echo.finishWave()
        }
    }

    private fun recordWave(cleared: Boolean) {
        mutableWaveReports += WaveReport(wave, waveLeaks, waveEchoHits, eliteActive, cleared)
    }

    private fun collectDefeatedEnemies() {
        mutableEnemies.removeAll { enemy ->
            if (enemy.isAlive) return@removeAll false
            kills++
            gold += enemy.bounty
            true
        }
    }

    fun startWave(): ActionResult {
        if (state != GameState.PREPARING) return ActionResult(false, "Finish the current wave first")
        wave++
        eliteActive = eliteOffered
        eliteOffered = false
        waveLeaks = 0
        waveEchoHits = 0
        echo.startWave(tick)
        spawnedThisWave = 0
        remainingToSpawn = waves[wave - 1].enemies.size
        nextSpawnTick = tick + 1L
        state = GameState.RUNNING
        return ActionResult(true, "Wave $wave incoming")
    }

    fun toggleElite(): ActionResult {
        if (!canOfferElite) return ActionResult(false, "Choose Elite while preparing wave $ELITE_WAVE")
        eliteOffered = !eliteOffered
        return ActionResult(true, if (eliteOffered) "Elite accepted: clear wave $ELITE_WAVE for +${ELITE_REWARD}g"
            else "Elite declined: standard wave $ELITE_WAVE")
    }

    fun switchRoute(): ActionResult {
        if (battleMap.alternatePath == null) return ActionResult(false, "This map has a single route")
        if (state != GameState.PREPARING) return ActionResult(false, "Switch routes between waves")
        useUpperRoute = !useUpperRoute
        return ActionResult(true, if (useUpperRoute) "Next wave uses the upper lock" else "Next wave uses the lower lock")
    }

    fun isUnlocked(type: TowerType): Boolean = fieldRank >= type.requiredRank

    fun buildTower(type: TowerType, x: Double, y: Double): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to build again")
        if (!isUnlocked(type)) return ActionResult(false, "${type.title} unlocks at field rank ${type.requiredRank}")
        placementError(x, y)?.let { return ActionResult(false, it) }
        if (gold < type.cost) return ActionResult(false, "Need ${type.cost} gold for ${type.title}")
        val tower = createTower(type, x, y, nextTowerId++)
        mutableTowers += tower
        gold -= type.cost
        return ActionResult(true, "${type.title} built", tower.id)
    }

    private fun createTower(type: TowerType, x: Double, y: Double, id: Long): Tower =
        Tower(x, y, baseRange(type), research.damageFor(type), type.baseAttackIntervalTicks,
            id, type, if (type == TowerType.TREMOR) tremorModule else TremorModule.STANDARD,
            if (type == TowerType.RAIL) railModule else RailModule.STANDARD, research.reloadFactor, terrain)

    fun upgradeTower(id: Long): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to upgrade again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        val cost = tower.upgradeCost ?: return ActionResult(false, "Already at maximum level", id)
        if (gold < cost) return ActionResult(false, "Need $cost gold to upgrade", id)
        gold -= cost
        tower.upgrade()
        return ActionResult(true, "${tower.type.title} upgraded to level ${tower.level}", id)
    }

    fun specializeCannon(id: Long, specialization: CannonSpecialization): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to specialize again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a Cannon first")
        if (!tower.specializeCannon(specialization)) return ActionResult(false, "Choose one specialization on a level 3 Cannon")
        return ActionResult(true, "Cannon specialized: ${specialization.title}", id)
    }

    fun cycleTargetPriority(id: Long): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to change targeting again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        tower.cycleTargetPriority()
        return ActionResult(true, "${tower.type.title} targets ${tower.targetPriority.title.lowercase()}", id)
    }

    fun selectEchoTower(id: Long): ActionResult {
        if (state != GameState.PREPARING || wave >= totalWaves - 1) {
            return ActionResult(false, "Choose a recorder before a wave that has a following wave")
        }
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        val armed = echo.toggleTower(tower.id)
        val message = if (armed) "${tower.type.title} will record wave ${wave + 1} and echo in wave ${wave + 2}"
            else "Echo recording disarmed"
        return ActionResult(true, message, id)
    }

    fun sellTower(id: Long): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to sell again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        gold += tower.sellValue
        mutableTowers.remove(tower)
        echo.onTowerSold(id)
        return ActionResult(true, "Sold for ${tower.sellValue} gold")
    }

    /** Shared by commands and the placement preview, so invalid previews cannot spend money. */
    fun placementError(x: Double, y: Double): String? {
        if (!x.isFinite() || !y.isFinite()) return "Choose a position on the map"
        if (x < BUILD_MARGIN || x > WORLD_WIDTH - BUILD_MARGIN || y < BUILD_MARGIN || y > WORLD_HEIGHT - BUILD_MARGIN) {
            return "Keep the tower inside the map"
        }
        for ((start, end) in battleMap.buildSegments) {
            if (distanceToSegmentSquared(x, y, start, end) < PATH_CLEARANCE * PATH_CLEARANCE) return "Keep the route clear"
        }
        if (mutableTowers.any { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < TOWER_SPACING * TOWER_SPACING }) {
            return "Too close to another tower"
        }
        return null
    }

    fun hasCommanders(waveNumber: Int): Boolean =
        waves.getOrNull(waveNumber - 1)?.commanderSlots?.isNotEmpty() == true

    fun waveName(waveNumber: Int): String? =
        when {
            corridorCampaign && waveNumber == Bastion.WAVE -> "Bastion"
            corridorCampaign && waveNumber == Marshal.WAVE -> "Marshal"
            else -> mode.waveName(waveNumber)
        }

    fun waveDescription(waveNumber: Int): String {
        require(waveNumber in 1..totalWaves)
        return if (waveNumber == Bastion.WAVE && bossWaveDescription != null) bossWaveDescription
        else waves[waveNumber - 1].description
    }

    internal fun checkpoint(): MissionCheckpoint? {
        if (state != GameState.PREPARING || wave == 0 && nextTowerId == 1L && !useUpperRoute) return null
        return MissionCheckpoint(
            battleMap, difficulty, seed, fieldRank, tremorModule, useUpperRoute,
            tick, wave, gold, lives, kills, leakedEnemies, nextEnemyId, nextTowerId, eliteOffered,
            mutableTowers.map(Tower::checkpoint), echo.checkpoint(), waveReports.toList(), mode, railModule, research, elevatedTerrain,
        )
    }

    fun restart() {
        mutableEnemies.clear()
        mutableTowers.clear()
        mutableShots.clear()
        mutableWaveReports.clear()
        echo.reset()
        eliteOffered = false
        eliteActive = false
        waveLeaks = 0
        waveEchoHits = 0
        tick = 0L
        gold = startingGold
        lives = difficulty.startingLives
        wave = 0
        useUpperRoute = false
        bossBreached = false
        kills = 0
        leakedEnemies = 0
        state = GameState.PREPARING
        remainingToSpawn = 0
        nextEnemyId = 1L
        nextTowerId = 1L
        spawnedThisWave = 0
        nextSpawnTick = 0L
    }

    private fun canManageTowers() = state == GameState.PREPARING || state == GameState.RUNNING

    private fun spawnEnemy() {
        val plan = waves[wave - 1]
        val type = plan.enemies[spawnedThisWave]
        val elite = eliteActive && spawnedThisWave < ELITE_COUNT
        val boss = wave == Bastion.WAVE && spawnedThisWave == bossSlot
        val marshal = plan.marshalFormation && spawnedThisWave == Marshal.SLOT
        val relay = plan.marshalFormation && spawnedThisWave in Marshal.relaySlots
        val commandSlot = plan.commanderFor(spawnedThisWave)
        val isCommander = commandSlot == spawnedThisWave
        val escortCommander = if (commandSlot != null && !isCommander) {
            val commanderId = nextEnemyId - (spawnedThisWave - commandSlot)
            mutableEnemies.firstOrNull { it.id == commanderId }
        } else null
        val healthFactor = when {
            elite -> ELITE_HEALTH_MULTIPLIER
            marshal -> Marshal.HEALTH_MULTIPLIER
            boss -> Bastion.HEALTH_MULTIPLIER
            isCommander -> Commander.HEALTH_MULTIPLIER
            else -> 1.0
        }
        val health = type.baseHealth * mode.healthMultiplier(wave) * difficulty.healthMultiplier * healthFactor
        val relays = if (marshal) mutableEnemies.filter { it.isMarshalRelay } else null
        val speed = if (relay) EnemyType.ARMORED.baseSpeed else type.baseSpeed
        mutableEnemies += Enemy(nextEnemyId++, path, speed, health, type,
            isElite = elite, isBoss = boss || marshal, isCommander = isCommander, escortCommander = escortCommander,
            isMarshalRelay = relay, marshalRelays = relays)
        spawnedThisWave++
        remainingToSpawn--
    }

    companion object {
        const val UPS = 60
        const val STEP_SECONDS = 1.0 / UPS
        const val WORLD_WIDTH = 900.0
        const val WORLD_HEIGHT = 560.0
        const val START_GOLD = 220
        const val START_LIVES = 20
        val TOTAL_WAVES: Int get() = Waves.total
        const val SHOT_LIFETIME_TICKS = 12L
        const val BUILD_MARGIN = 24.0
        const val PATH_CLEARANCE = 36.0
        const val TOWER_SPACING = 44.0
        const val ELITE_WAVE = 8
        const val ELITE_COUNT = 3
        const val ELITE_REWARD = 90
        const val ELITE_HEALTH_MULTIPLIER = 1.6

        private fun rewardFor(wave: Int, elite: Boolean): Int =
            25 + wave * 5 + if (elite) ELITE_REWARD else 0

        internal fun restore(save: MissionCheckpoint): GameSimulation? {
            if (!save.mode.supports(save.map, save.seed) || save.elevatedTerrain && save.mode != MissionMode.CAMPAIGN || save.wave !in 0 until save.mode.waveCount || save.tick !in 0..Long.MAX_VALUE / 2 ||
                save.gold < 0 || save.lives !in 1..save.difficulty.startingLives ||
                save.leaked != save.difficulty.startingLives - save.lives || save.kills < 0 || save.fieldRank < 1 ||
                save.useUpperRoute && save.map.alternatePath == null || save.eliteOffered && (save.mode != MissionMode.CAMPAIGN || save.wave + 1 != ELITE_WAVE)
            ) return null

            val resolvedEnemies = (1..save.wave).sumOf { save.mode.plan(it, save.seed).enemies.size }
            if (save.kills != resolvedEnemies - save.leaked || save.nextEnemyId != resolvedEnemies + 1L ||
                save.nextTowerId <= 0L || save.nextTowerId > Long.MAX_VALUE / 2 ||
                save.reports.size != save.wave || save.reports.sumOf { it.leaked } != save.leaked ||
                save.reports.withIndex().any { (index, report) ->
                    report.wave != index + 1 || !report.cleared || report.leaked < 0 || report.echoHits < 0 ||
                        report.elite && (save.mode != MissionMode.CAMPAIGN || report.wave != ELITE_WAVE)
                }
            ) return null

            val game = GameSimulation(save.map, save.difficulty, save.seed, save.fieldRank, save.tremorModule, save.mode,
                save.railModule, save.research, save.elevatedTerrain)
            if (game.tremorModule != save.tremorModule || game.research != save.research) return null
            var previousTowerId = 0L
            for (tower in save.towers) {
                if (tower.id <= previousTowerId || tower.id >= save.nextTowerId || tower.level !in 1..Tower.MAX_LEVEL ||
                    tower.nextAttackTick !in 0..(save.tick + save.railModule.maxAttackInterval(tower.type)) ||
                    tower.cannonSpecialization != CannonSpecialization.STANDARD &&
                        (tower.type != TowerType.CANNON || tower.level != Tower.MAX_LEVEL) ||
                    !game.isUnlocked(tower.type) || game.placementError(tower.x, tower.y) != null
                ) return null
                game.mutableTowers += game.createTower(tower.type, tower.x, tower.y, tower.id).also { it.restore(tower) }
                previousTowerId = tower.id
            }
            val armedId = save.echo.armedTowerId
            if (armedId != null && (save.wave >= game.totalWaves - 1 || game.towers.none { it.id == armedId })) return null

            game.tick = save.tick
            game.wave = save.wave
            game.gold = save.gold
            game.lives = save.lives
            game.kills = save.kills
            game.leakedEnemies = save.leaked
            game.nextEnemyId = save.nextEnemyId
            game.nextTowerId = save.nextTowerId
            game.useUpperRoute = save.useUpperRoute
            game.eliteOffered = save.eliteOffered
            game.eliteActive = save.reports.lastOrNull()?.elite == true
            game.mutableWaveReports.addAll(save.reports)
            game.echo.restore(save.echo)
            return game
        }

        fun enemyCount(wave: Int): Int = Waves.enemyCount(wave)
        fun spawnIntervalTicks(wave: Int): Long = Waves.intervalTicks(wave)
        fun enemyType(wave: Int, index: Int): EnemyType = Waves.enemyType(wave, index)

        private fun distanceToSegmentSquared(x: Double, y: Double, start: Waypoint, end: Waypoint): Double {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((x - start.x) * dx + (y - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val nearestX = start.x + t * dx
            val nearestY = start.y + t * dy
            return (x - nearestX) * (x - nearestX) + (y - nearestY) * (y - nearestY)
        }
    }
}

enum class GameState { PREPARING, RUNNING, WON, LOST }
data class ActionResult(val success: Boolean, val message: String, val towerId: Long? = null)


data class WaveReport(val wave: Int, val leaked: Int, val echoHits: Int, val elite: Boolean, val cleared: Boolean)
