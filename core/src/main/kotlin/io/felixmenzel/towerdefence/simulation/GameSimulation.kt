package io.felixmenzel.towerdefence.simulation

class GameSimulation {
    var tick: Long = 0
        private set

    var previousEnemyX: Double = 100.0
        private set
    var enemyX: Double = 0.0
        private set

    fun update() {
        tick++

        previousEnemyX = enemyX
        // Jeder Aufruf entspricht IMMER genau 1/60 Sekunde.
        enemyX += ENEMY_SPEED_PER_SECOND * STEP_SECONDS
    }

    fun interpolatedEnemyX(alpha: Double): Double {
        return previousEnemyX +
            (enemyX - previousEnemyX) * alpha
    }

    companion object {
        const val UPS = 60
        const val STEP_SECONDS = 1.0 / UPS
        const val ENEMY_SPEED_PER_SECOND = 50.0
    }
}
