package io.felixmenzel.towerdefence.simulation

import kotlin.math.hypot
import kotlin.math.min

/** All positions use the centre of an enemy in world coordinates. */
class Enemy(
    val id: Long,
    path: List<Waypoint>,
    val speed: Double,
    val maxHealth: Double,
    val type: EnemyType = EnemyType.NORMAL,
    val bounty: Int = type.bounty,
) {
    private val path = path.toList()

    init {
        require(path.isNotEmpty()) { "Enemy path must contain at least one waypoint" }
        require(speed.isFinite() && speed >= 0.0) { "Enemy speed must be finite and non-negative" }
        require(maxHealth.isFinite() && maxHealth > 0.0) { "Enemy health must be finite and positive" }
        require(bounty >= 0)
    }

    var previousX = path.first().x
        private set
    var previousY = path.first().y
        private set
    var x = path.first().x
        private set
    var y = path.first().y
        private set
    var distanceTravelled = 0.0
        private set
    private var nextWaypointIndex = 1
    val hasReachedGoal: Boolean get() = nextWaypointIndex >= path.size
    var health = maxHealth
        private set
    val isAlive: Boolean get() = health > 0.0
    var slowFactor = 1.0
        private set
    private var slowSeconds = 0.0

    fun update(stepSeconds: Double) {
        require(stepSeconds.isFinite() && stepSeconds >= 0.0)
        previousX = x
        previousY = y
        if (!isAlive || hasReachedGoal) return

        // Only the portion of this step still covered by the slow uses reduced speed.
        val slowedSeconds = min(stepSeconds, slowSeconds)
        var remainingMovement = speed * (slowedSeconds * slowFactor + stepSeconds - slowedSeconds)
        slowSeconds = (slowSeconds - stepSeconds).coerceAtLeast(0.0)
        if (slowSeconds == 0.0) slowFactor = 1.0
        while (!hasReachedGoal) {
            val target = path[nextWaypointIndex]
            val deltaX = target.x - x
            val deltaY = target.y - y
            val distance = hypot(deltaX, deltaY)
            if (distance <= remainingMovement) {
                x = target.x
                y = target.y
                distanceTravelled += distance
                remainingMovement -= distance
                nextWaypointIndex++
            } else {
                if (remainingMovement <= 0.0) break
                x += deltaX / distance * remainingMovement
                y += deltaY / distance * remainingMovement
                distanceTravelled += remainingMovement
                break
            }
        }
    }

    fun takeDamage(damage: Double) {
        require(damage.isFinite() && damage >= 0.0) { "Damage must be finite and non-negative" }
        health = (health - damage).coerceAtLeast(0.0)
    }

    /** Slows never multiply; repeated shots refresh the strongest active slow. */
    fun applySlow(factor: Double, durationSeconds: Double) {
        require(factor.isFinite() && factor > 0.0 && factor <= 1.0)
        require(durationSeconds.isFinite() && durationSeconds >= 0.0)
        if (durationSeconds == 0.0) return
        slowFactor = min(slowFactor, factor)
        slowSeconds = maxOf(slowSeconds, durationSeconds)
    }

    fun interpolatedX(alpha: Double): Double = previousX + (x - previousX) * alpha.coerceIn(0.0, 1.0)
    fun interpolatedY(alpha: Double): Double = previousY + (y - previousY) * alpha.coerceIn(0.0, 1.0)
}

enum class EnemyType(val title: String, val baseHealth: Double, val baseSpeed: Double, val bounty: Int) {
    NORMAL("Drifter", 80.0, 50.0, 9),
    FAST("Runner", 55.0, 86.0, 10),
    // This is a high-health enemy, not a damage-resistance mechanic.
    ARMORED("Bulwark", 190.0, 36.0, 16),
}
