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
    var targetPriority = TargetPriority.FIRST
        private set
    private var totalInvestment = type.cost
    val range: Double get() = rangeAtLevel(level)
    val damage: Double get() = damageAtLevel(level)
    val attackIntervalTicks: Long get() = attackIntervalAtLevel(level)
    val nextUpgrade: TowerUpgrade?
        get() = if (level >= MAX_LEVEL) null else TowerUpgrade(
            cost = (type.cost * (0.8 + (level - 1) * 0.5)).roundToInt(),
            range = rangeAtLevel(level + 1),
            damage = damageAtLevel(level + 1),
            attackIntervalTicks = attackIntervalAtLevel(level + 1),
        )
    val upgradeCost: Int? get() = nextUpgrade?.cost
    val sellValue: Int get() = (totalInvestment * 0.65).toInt()
    private var nextAttackTick = 0L

    private fun rangeAtLevel(level: Int) = initialRange + (level - 1) * 14.0
    private fun damageAtLevel(level: Int) = initialDamage * (1.0 + (level - 1) * 0.65)
    private fun attackIntervalAtLevel(level: Int) =
        (initialAttackInterval * (1.0 - (level - 1) * 0.08)).toLong().coerceAtLeast(1L)

    internal fun upgrade() {
        val upgrade = checkNotNull(nextUpgrade)
        totalInvestment += upgrade.cost
        level++
    }

    internal fun cycleTargetPriority() {
        val priorities = TargetPriority.entries
        targetPriority = priorities[(targetPriority.ordinal + 1) % priorities.size]
    }

    /** The renderer and combat share exactly one eligible-target and priority rule. */
    fun selectTarget(enemies: List<Enemy>): Enemy? {
        val comparator = when (targetPriority) {
            TargetPriority.FIRST -> compareBy<Enemy>({ -it.distanceTravelled }, { it.id })
            TargetPriority.STRONGEST -> compareBy<Enemy>({ -it.health }, { it.id })
            TargetPriority.NEAREST -> compareBy<Enemy>({ distanceSquaredTo(it) }, { it.id })
        }
        return enemies.asSequence()
            .filter { it.isAlive && !it.hasReachedGoal && distanceSquaredTo(it) <= range * range }
            .minWithOrNull(comparator)
    }

    fun update(currentTick: Long, enemies: List<Enemy>): Shot? {
        if (currentTick < nextAttackTick) return null
        val target = selectTarget(enemies) ?: return null

        val hitEnemyIds = if (type == TowerType.CANNON) {
            buildList {
                for (enemy in enemies) {
                    val dx = enemy.x - target.x
                    val dy = enemy.y - target.y
                    if (enemy.isAlive && !enemy.hasReachedGoal && dx * dx + dy * dy <= SPLASH_RADIUS * SPLASH_RADIUS) {
                        enemy.takeDamage(damage)
                        add(enemy.id)
                    }
                }
            }
        } else {
            target.takeDamage(damage)
            if (type == TowerType.FROST && target.isAlive) target.applySlow(0.55, 1.5)
            listOf(target.id)
        }
        nextAttackTick = currentTick + attackIntervalTicks
        return Shot(x, y, target.x, target.y, type, currentTick, hitEnemyIds)
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
data class Shot(
    val fromX: Double,
    val fromY: Double,
    val toX: Double,
    val toY: Double,
    val type: TowerType,
    val tick: Long,
    val hitEnemyIds: List<Long>,
)

/** Exact next-level values, calculated by the same rules as the active tower stats. */
data class TowerUpgrade(val cost: Int, val range: Double, val damage: Double, val attackIntervalTicks: Long)

enum class TargetPriority(val title: String) {
    FIRST("First"),
    STRONGEST("Strongest"),
    NEAREST("Nearest"),
}
