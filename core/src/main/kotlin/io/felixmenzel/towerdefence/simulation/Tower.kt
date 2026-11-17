package io.felixmenzel.towerdefence.simulation

import kotlin.math.roundToInt

class Tower(
    val x: Double,
    val y: Double,
    range: Double,
    damage: Double,
    attackIntervalTicks: Long,
    val id: Long = 0L,
    val type: TowerType = TowerType.RAPID,
) {
    private val initialRange = range
    private val initialDamage = damage
    private val initialAttackInterval = attackIntervalTicks

    init {
        require(x.isFinite() && y.isFinite())
        require(range.isFinite() && range >= 0.0)
        require(damage.isFinite() && damage >= 0.0)
        require(attackIntervalTicks > 0L)
    }

    var level = 1
        private set
    private var totalInvestment = type.cost
    val range: Double get() = initialRange + (level - 1) * 14.0
    val damage: Double get() = initialDamage * (1.0 + (level - 1) * 0.65)
    val attackIntervalTicks: Long get() = (initialAttackInterval * (1.0 - (level - 1) * 0.08)).toLong().coerceAtLeast(1L)
    val upgradeCost: Int? get() = if (level >= MAX_LEVEL) null else (type.cost * (0.8 + (level - 1) * 0.5)).roundToInt()
    val sellValue: Int get() = (totalInvestment * 0.65).toInt()
    private var nextAttackTick = 0L

    internal fun upgrade() {
        val cost = checkNotNull(upgradeCost)
        totalInvestment += cost
        level++
    }

    fun update(currentTick: Long, enemies: List<Enemy>): Shot? {
        if (currentTick < nextAttackTick) return null
        val target = enemies.asSequence()
            .filter { it.isAlive && !it.hasReachedGoal && distanceSquaredTo(it) <= range * range }
            .minWithOrNull(compareBy<Enemy>({ -it.distanceTravelled }, { it.id })) ?: return null

        if (type == TowerType.CANNON) {
            for (enemy in enemies) {
                val dx = enemy.x - target.x
                val dy = enemy.y - target.y
                if (enemy.isAlive && !enemy.hasReachedGoal && dx * dx + dy * dy <= SPLASH_RADIUS * SPLASH_RADIUS) {
                    enemy.takeDamage(damage)
                }
            }
        } else {
            target.takeDamage(damage)
            if (type == TowerType.FROST && target.isAlive) target.applySlow(0.55, 1.5)
        }
        nextAttackTick = currentTick + attackIntervalTicks
        return Shot(x, y, target.x, target.y, type, currentTick)
    }

    private fun distanceSquaredTo(enemy: Enemy): Double {
        val dx = enemy.x - x
        val dy = enemy.y - y
        return dx * dx + dy * dy
    }

    companion object {
        const val MAX_LEVEL = 3
        const val SPLASH_RADIUS = 58.0
    }
}

enum class TowerType(
    val title: String,
    val cost: Int,
    val description: String,
    val baseRange: Double,
    val baseDamage: Double,
    val baseAttackIntervalTicks: Long,
) {
    RAPID("Pulse", 75, "Fast single-target fire", 135.0, 12.0, 18L),
    CANNON("Cannon", 120, "Area damage against clusters", 155.0, 34.0, 66L),
    FROST("Frost", 90, "Slows targets by 45%", 125.0, 6.0, 42L),
}

/** A visual trace of an already resolved hit, not a second source of damage. */
data class Shot(val fromX: Double, val fromY: Double, val toX: Double, val toY: Double, val type: TowerType, val tick: Long)
