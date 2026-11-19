package io.felixmenzel.towerdefence.simulation

import java.util.Collections

/** Pure Kotlin game rules. Rendering and input never own gold, health, or damage. */
class GameSimulation(
    val battleMap: BattleMap = BattleMap.CORRIDOR,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val seed: Int? = null,
    val fieldRank: Int = 1,
    tremorModule: TremorModule = TremorModule.STANDARD,
) {
    val tremorModule = if (fieldRank >= TremorModule.REQUIRED_RANK) tremorModule else TremorModule.STANDARD
    var useUpperRoute = false
        private set
    val path: List<Waypoint> get() = if (useUpperRoute) checkNotNull(battleMap.alternatePath) else battleMap.path
    val inactivePath: List<Waypoint>? get() = if (useUpperRoute) battleMap.path else battleMap.alternatePath
    val challenge: RemixChallenge? = seed?.takeIf { it in RemixChallenge.SEED_RANGE }?.let {
        RemixChallenge(battleMap, difficulty, it)
    }
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableTowers = mutableListOf<Tower>()
    private val mutableShots = mutableListOf<Shot>()
    val enemies: List<Enemy> = Collections.unmodifiableList(mutableEnemies)
    val towers: List<Tower> = Collections.unmodifiableList(mutableTowers)
    val shots: List<Shot> = Collections.unmodifiableList(mutableShots)
    var tick = 0L
        private set
    var gold = difficulty.startingGold
        private set
    var lives = difficulty.startingLives
        private set
    var wave = 0
        private set
    var kills = 0
        private set
    var leakedEnemies = 0
        private set
    val totalWaves = TOTAL_WAVES
    private val waves = List(totalWaves) { Waves.plan(it + 1, seed) }
    var state = GameState.PREPARING
        private set
    var remainingToSpawn = 0
        private set
    val waveReward: Int get() = 25 + wave * 5
    val nextWaveDescription: String get() = waveDescription((wave + 1).coerceAtMost(totalWaves))
    private var nextEnemyId = 1L
    private var nextTowerId = 1L
    private var spawnedThisWave = 0
    private var nextSpawnTick = 0L
    internal val echo = WaveEcho()

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
            leakedEnemies += escaped
            lives = (lives - escaped).coerceAtLeast(0)
            mutableEnemies.removeAll { it.isAlive && it.hasReachedGoal }
        }
        if (lives == 0) {
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
        echo.replay(tick, mutableEnemies, mutableShots)
        collectDefeatedEnemies()
        if (remainingToSpawn == 0 && mutableEnemies.isEmpty()) {
            gold += waveReward
            state = if (wave == totalWaves) GameState.WON else GameState.PREPARING
            if (state == GameState.WON) echo.reset() else echo.finishWave()
        }
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
        echo.startWave(tick)
        spawnedThisWave = 0
        remainingToSpawn = waves[wave - 1].enemies.size
        nextSpawnTick = tick + 1L
        state = GameState.RUNNING
        return ActionResult(true, "Wave $wave incoming")
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
        val tower = Tower(x, y, type.baseRange, type.baseDamage, type.baseAttackIntervalTicks,
            nextTowerId++, type, if (type == TowerType.TREMOR) tremorModule else TremorModule.STANDARD)
        mutableTowers += tower
        gold -= type.cost
        return ActionResult(true, "${type.title} built", tower.id)
    }

    fun upgradeTower(id: Long): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to upgrade again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        val cost = tower.upgradeCost ?: return ActionResult(false, "Already at maximum level", id)
        if (gold < cost) return ActionResult(false, "Need $cost gold to upgrade", id)
        gold -= cost
        tower.upgrade()
        return ActionResult(true, "${tower.type.title} upgraded to level ${tower.level}", id)
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

    fun waveDescription(waveNumber: Int): String {
        require(waveNumber in 1..totalWaves)
        return waves[waveNumber - 1].description
    }

    fun restart() {
        mutableEnemies.clear()
        mutableTowers.clear()
        mutableShots.clear()
        echo.reset()
        tick = 0L
        gold = difficulty.startingGold
        lives = difficulty.startingLives
        wave = 0
        useUpperRoute = false
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
        val type = waves[wave - 1].enemies[spawnedThisWave]
        val health = type.baseHealth * Waves.healthMultiplier(wave) * difficulty.healthMultiplier
        mutableEnemies += Enemy(nextEnemyId++, path, type.baseSpeed, health, type)
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
