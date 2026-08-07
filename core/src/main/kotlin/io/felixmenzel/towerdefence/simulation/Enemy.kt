package io.felixmenzel.towerdefence.simulation

class Enemy(
    val id: Long,
    startX: Double,
    startY: Double,
    val speed: Double,
    val maxHealth: Double,
) {
    var previousX: Double = startX
        private set

    var previousY: Double = startY
        private set

    var x: Double = startX
        private set

    var y: Double = startY
        private set

    var health: Double = maxHealth
        private set

    val isAlive: Boolean
        get() = health > 0.0

    fun update(stepSeconds: Double) {
        previousX = x
        previousY = y

        // Vorerst bewegt sich der Gegner nur nach rechts
        x += speed * stepSeconds
    }

    fun takeDamage(damage: Double) {
        require(damage >= 0.0) {
            "Damage must not be negative"
        }

        health = (health - damage).coerceAtLeast(0.0)
    }

    fun interpolatedX(alpha: Double): Double {
        return previousX + (x - previousX) * alpha
    }

    fun interpolatedY(alpha: Double): Double {
        return previousY + (y - previousY) * alpha
    }
}
