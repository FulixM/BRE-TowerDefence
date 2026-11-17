package io.felixmenzel.towerdefence.simulation

import java.util.Collections
import kotlin.math.roundToInt

/** Pure Kotlin game rules. Rendering and input never own gold, health, or damage. */
class GameSimulation {
    val path: List<Waypoint> = Collections.unmodifiableList(
        listOf(
            Waypoint(50.0, 160.0), Waypoint(300.0, 160.0), Waypoint(300.0, 350.0),
            Waypoint(600.0, 350.0), Waypoint(600.0, 100.0), Waypoint(850.0, 100.0),
        ),
    )
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableTowers = mutableListOf<Tower>()
    private val mutableShots = mutableListOf<Shot>()
    val enemies: List<Enemy> = Collections.unmodifiableList(mutableEnemies)
    val towers: List<Tower> = Collections.unmodifiableList(mutableTowers)
    val shots: List<Shot> = Collections.unmodifiableList(mutableShots)
    var tick = 0L
        private set
    var gold = START_GOLD
        private set
    var lives = START_LIVES
        private set
    var wave = 0
        private set
    val totalWaves = TOTAL_WAVES
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

    fun update() {
        tick++
        mutableShots.removeAll { tick - it.tick > SHOT_LIFETIME_TICKS }
        if (state != GameState.RUNNING) return

        if (remainingToSpawn > 0 && tick >= nextSpawnTick) {
            spawnEnemy()
            nextSpawnTick = tick + spawnIntervalTicks(wave)
        }
        for (enemy in mutableEnemies) enemy.update(STEP_SECONDS)
        // A leak is resolved before towers fire: reaching the exit is definitive.
        val escaped = mutableEnemies.count { it.hasReachedGoal }
        if (escaped > 0) {
            lives = (lives - escaped).coerceAtLeast(0)
            mutableEnemies.removeAll { it.hasReachedGoal }
        }
        if (lives == 0) {
            state = GameState.LOST
            remainingToSpawn = 0
            mutableShots.clear()
            return
        }
        for (tower in mutableTowers) {
            tower.update(tick, mutableEnemies)?.let(mutableShots::add)
        }
        gold += mutableEnemies.filter { !it.isAlive }.sumOf { it.bounty }
        mutableEnemies.removeAll { !it.isAlive }
        if (remainingToSpawn == 0 && mutableEnemies.isEmpty()) {
            gold += waveReward
            state = if (wave == totalWaves) GameState.WON else GameState.PREPARING
        }
    }

    fun startWave(): ActionResult {
        if (state != GameState.PREPARING) return ActionResult(false, "Finish the current wave first")
        wave++
        spawnedThisWave = 0
        remainingToSpawn = enemyCount(wave)
        nextSpawnTick = tick + 1L
        state = GameState.RUNNING
        return ActionResult(true, "Wave $wave incoming")
    }

    fun buildTower(type: TowerType, x: Double, y: Double): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to build again")
        placementError(x, y)?.let { return ActionResult(false, it) }
        if (gold < type.cost) return ActionResult(false, "Need ${type.cost} gold for ${type.title}")
        val tower = Tower(x, y, type.baseRange, type.baseDamage, type.baseAttackIntervalTicks, nextTowerId++, type)
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

    fun sellTower(id: Long): ActionResult {
        if (!canManageTowers()) return ActionResult(false, "Restart to sell again")
        val tower = mutableTowers.firstOrNull { it.id == id } ?: return ActionResult(false, "Select a tower first")
        gold += tower.sellValue
        mutableTowers.remove(tower)
        return ActionResult(true, "Sold for ${tower.sellValue} gold")
    }

    /** Shared by commands and the placement preview, so invalid previews cannot spend money. */
    fun placementError(x: Double, y: Double): String? {
        if (!x.isFinite() || !y.isFinite()) return "Choose a position on the map"
        if (x < BUILD_MARGIN || x > WORLD_WIDTH - BUILD_MARGIN || y < BUILD_MARGIN || y > WORLD_HEIGHT - BUILD_MARGIN) {
            return "Keep the tower inside the map"
        }
        for ((start, end) in path.zipWithNext()) {
            if (distanceToSegmentSquared(x, y, start, end) < PATH_CLEARANCE * PATH_CLEARANCE) return "Keep the route clear"
        }
        if (mutableTowers.any { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < TOWER_SPACING * TOWER_SPACING }) {
            return "Too close to another tower"
        }
        return null
    }

    fun waveDescription(waveNumber: Int): String {
        require(waveNumber in 1..totalWaves)
        val counts = (0 until enemyCount(waveNumber)).groupingBy { enemyType(waveNumber, it) }.eachCount()
        return EnemyType.entries.mapNotNull { type -> counts[type]?.let { "$it ${type.title}" } }.joinToString(" / ")
    }

    fun restart() {
        mutableEnemies.clear()
        mutableTowers.clear()
        mutableShots.clear()
        tick = 0L
        gold = START_GOLD
        lives = START_LIVES
        wave = 0
        state = GameState.PREPARING
        remainingToSpawn = 0
        nextEnemyId = 1L
        nextTowerId = 1L
        spawnedThisWave = 0
        nextSpawnTick = 0L
    }

    private fun canManageTowers() = state == GameState.PREPARING || state == GameState.RUNNING

    private fun spawnEnemy() {
        val type = enemyType(wave, spawnedThisWave)
        val difficulty = 1.0 + (wave - 1) * 0.22 + (wave - 1) * (wave - 1) * 0.045
        mutableEnemies += Enemy(nextEnemyId++, path, type.baseSpeed, type.baseHealth * difficulty, type)
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
        const val TOTAL_WAVES = 10
        const val SHOT_LIFETIME_TICKS = 12L
        const val BUILD_MARGIN = 24.0
        const val PATH_CLEARANCE = 36.0
        const val TOWER_SPACING = 44.0

        fun enemyCount(wave: Int): Int = 8 + (wave - 1) * 2
        fun spawnIntervalTicks(wave: Int): Long = ((0.85 - (wave - 1) * 0.045).coerceAtLeast(0.45) * UPS).roundToInt().toLong()
        fun enemyType(wave: Int, index: Int): EnemyType = when {
            wave >= 5 && index % 5 == 4 -> EnemyType.ARMORED
            wave >= 3 && index % 3 == 2 -> EnemyType.FAST
            else -> EnemyType.NORMAL
        }

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
