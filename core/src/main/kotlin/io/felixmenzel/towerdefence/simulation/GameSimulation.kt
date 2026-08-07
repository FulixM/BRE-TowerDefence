package io.felixmenzel.towerdefence.simulation

class GameSimulation {
    var tick: Long = 0
        private set

    val enemy = Enemy(
        id = 1L,
        startX = 100.0,
        startY = 160.0,
        speed = 50.0,
        maxHealth = 100.0,
    )

    fun update() {
        tick++

        if (enemy.isAlive) {
            enemy.update(STEP_SECONDS)
        }
    }

    companion object {
        const val UPS = 60
        const val STEP_SECONDS = 1.0 / UPS
    }
}
