package io.felixmenzel.towerdefence.simulation

class GameSimulation {
    var tick: Long = 0
        private set

    private val mutableEnemies = mutableListOf<Enemy>()
    val enemies: List<Enemy> = mutableEnemies

    private var nextEnemyId = 1L
    private var spawnedEnemyCount = 0

    fun update() {
        tick++

        for (enemy in mutableEnemies) {
            if (enemy.isAlive) {
                enemy.update(STEP_SECONDS)
            }
        }
        if (
            tick % SPAWN_INTERVAL_TICKS == 0L &&
            spawnedEnemyCount < MAX_ENEMIES
        ) {
            spawnEnemy()
        }
    }

    private fun spawnEnemy() {
        mutableEnemies +=
            Enemy(
                id = nextEnemyId++,
                startX = 100.0,
                startY = 160.0,
                speed = 50.0,
                maxHealth = 100.0,
            )

        spawnedEnemyCount++
    }

    companion object {
        const val UPS = 60
        const val STEP_SECONDS = 1.0 / UPS

        private const val SPAWN_INTERVAL_SECONDS = 2
        private const val SPAWN_INTERVAL_TICKS = UPS.toLong() * SPAWN_INTERVAL_SECONDS
        private const val MAX_ENEMIES = 50
    }
}
