package io.felixmenzel.towerdefence.simulation

class GameSimulation {
    var tick: Long = 0
        private set

    var enemyX: Double = 0.0
        private set

    private val enemySpeedPerSecond = 50.0

    fun update() {
        tick++

        // Jeder Aufruf entspricht IMMER genau 1/60 Sekunde.
        enemyX += enemySpeedPerSecond * STEP_SECONDS
    }

    val elapsedSeconds: Double
        get() = tick * STEP_SECONDS

    companion object {
        const val UPS = 60
        const val STEP_SECONDS = 1.0 / UPS
    }
}
