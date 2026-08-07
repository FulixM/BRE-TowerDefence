package io.felixmenzel.towerdefence.simulation

import kotlin.math.sqrt

class Enemy(
    val id: Long,
    private val path: List<Waypoint>,
    val speed: Double,
    val maxHealth: Double,
) {
    init {
        require(path.isNotEmpty()) {
            "Enemy path must contain at least one waypoint"
        }

        require(speed >= 0.0) {
            "Enemy speed must not be negative"
        }

        require(maxHealth > 0.0) {
            "Enemy maxHealth must be greater than zero"
        }
    }

    var previousX: Double = path.first().x
        private set

    var previousY: Double = path.first().y
        private set

    var x: Double = path.first().x
        private set

    var y: Double = path.first().y
        private set

    private var nextWaypointIndex = 1
    val hasReachedGoal: Boolean
        get() = nextWaypointIndex >= path.size

    var health: Double = maxHealth
        private set

    val isAlive: Boolean
        get() = health > 0.0

    fun update(stepSeconds: Double) {
        previousX = x
        previousY = y

        var remainingMovement = speed * stepSeconds
        while (remainingMovement > 0.0 && !hasReachedGoal) {
            val target = path[nextWaypointIndex]

            val deltaX = target.x - x
            val deltaY = target.y - y
            val distanceToTarget =
                sqrt(
                    deltaX * deltaX + deltaY * deltaY,
                )

            if (distanceToTarget <= remainingMovement) {
                // Wegpunkt exakt erreichen
                x = target.x
                y = target.y

                remainingMovement -= distanceToTarget
                nextWaypointIndex++
            } else {
                // Nur einen Teil der Strecke zurücklegen
                val directionX = deltaX / distanceToTarget
                val directionY = deltaY / distanceToTarget

                x += directionX * remainingMovement
                y += directionY * remainingMovement

                remainingMovement = 0.0
            }
            x += speed * stepSeconds
        }
    }

    fun takeDamage(damage: Double) {
        require(damage >= 0.0) {
            "Damage must not be negative"
        }

        health = (health - damage).coerceAtLeast(0.0)
    }

    fun interpolatedX(alpha: Double): Double = previousX + (x - previousX) * alpha

    fun interpolatedY(alpha: Double): Double = previousY + (y - previousY) * alpha
}
