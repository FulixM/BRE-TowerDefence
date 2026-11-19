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
    val tremorModule: TremorModule = TremorModule.STANDARD,
    val railModule: RailModule = RailModule.STANDARD,
    private val researchReloadFactor: Double = 1.0,
    private val terrain: BattleTerrain = BattleTerrain.FLAT,
) {
    val height = terrain.heightAt(x, y)
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
    var cannonSpecialization = CannonSpecialization.STANDARD
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
        (initialAttackInterval * (1.0 - (level - 1) * 0.08) * cannonSpecialization.attackIntervalFactor *
            railModule.intervalFactor(type) * researchReloadFactor).toLong().coerceAtLeast(1L)

    internal fun upgrade() {
        val upgrade = checkNotNull(nextUpgrade)
        totalInvestment += upgrade.cost
        level++
    }

    internal fun specializeCannon(specialization: CannonSpecialization): Boolean {
        if (type != TowerType.CANNON || level != MAX_LEVEL ||
            cannonSpecialization != CannonSpecialization.STANDARD || specialization == CannonSpecialization.STANDARD
        ) return false

        cannonSpecialization = specialization
        return true
    }

    internal fun checkpoint() =
        TowerCheckpoint(id, type, x, y, level, targetPriority, nextAttackTick, cannonSpecialization)

    internal fun restore(checkpoint: TowerCheckpoint) {
        check(level == 1) { "Restore requires a fresh tower" }
        require(checkpoint.id == id && checkpoint.type == type && checkpoint.x == x && checkpoint.y == y)
        require(checkpoint.level in 1..MAX_LEVEL && checkpoint.nextAttackTick >= 0L)

        repeat(checkpoint.level - 1) { upgrade() }
        if (checkpoint.cannonSpecialization != CannonSpecialization.STANDARD) {
            require(specializeCannon(checkpoint.cannonSpecialization))
        }
        targetPriority = checkpoint.priority
        nextAttackTick = checkpoint.nextAttackTick
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
            .filter { it.canBeHit && inRange(it) }
            .minWithOrNull(comparator)
    }

    fun update(currentTick: Long, enemies: List<Enemy>): Shot? {
        if (currentTick < nextAttackTick) return null
        val target = selectTarget(enemies) ?: return null

        val impactRadius = when (type) {
            TowerType.CANNON -> cannonSpecialization.radius
            TowerType.TREMOR -> tremorModule.radius
            TowerType.INFERNO -> range
            else -> 0.0
        }
        val hits = when (type) {
            TowerType.CANNON, TowerType.TREMOR -> enemies.filter { it.canBeHit && within(it, target, impactRadius) }
            TowerType.INFERNO -> enemies.filter { it.canBeHit && inRange(it) }
            TowerType.ARC -> chainFrom(target, enemies)
            else -> listOf(target)
        }

        for ((index, enemy) in hits.withIndex()) {
            applyTowerHit(type, enemy, damage, index, tremorModule, railModule)
        }
        val hitEnemyIds = hits.map { it.id }
        nextAttackTick = currentTick + attackIntervalTicks
        return Shot(
            x, y, target.x, target.y, type, currentTick, hitEnemyIds,
            arcJumps = if (type == TowerType.ARC) hits.drop(1).map { Waypoint(it.x, it.y) } else emptyList(),
            areaRadius = impactRadius,
            tremorModule = tremorModule,
            railModule = railModule,
        )
    }

    private fun chainFrom(target: Enemy, enemies: List<Enemy>): List<Enemy> = buildList {
        add(target)
        repeat(ARC_DAMAGE_FACTORS.size - 1) {
            val previous = last()
            val next = enemies.asSequence()
                .filter { it.canBeHit && it !in this && within(it, previous, ARC_JUMP_RANGE) }
                .minWithOrNull(compareBy({ separationSquared(it, previous) }, { it.id }))
                ?: return@buildList
            add(next)
        }
    }

    private val Enemy.canBeHit: Boolean get() = isAlive && !hasReachedGoal

    private fun within(enemy: Enemy, source: Enemy, radius: Double) =
        separationSquared(enemy, source) <= radius * radius

    private fun separationSquared(enemy: Enemy, source: Enemy): Double {
        val dx = enemy.x - source.x
        val dy = enemy.y - source.y
        return dx * dx + dy * dy
    }

    private fun inRange(enemy: Enemy) = terrain.canReach(x, y, height, range, enemy.x, enemy.y)

    private fun distanceSquaredTo(enemy: Enemy): Double {
        val dx = enemy.x - x
        val dy = enemy.y - y
        return dx * dx + dy * dy
    }

    companion object {
        const val MAX_LEVEL = 3
        const val SPLASH_RADIUS = 58.0
        private const val ARC_JUMP_RANGE = 76.0

    }
}

enum class TowerType(
    val title: String,
    val cost: Int,
    val description: String,
    val baseRange: Double,
    val baseDamage: Double,
    val baseAttackIntervalTicks: Long,
    val requiredRank: Int = 1,
) {
    RAPID("Pulse", 75, "Fast single-target fire", 135.0, 12.0, 18L),
    CANNON("Cannon", 120, "Area damage against clusters", 155.0, 34.0, 66L),
    FROST("Frost", 90, "Slows its target by 45%", 125.0, 6.0, 42L),
    RAIL("Rail", 140, "Long-range shot that ignores armor", 220.0, 92.0, 115L),
    ARC("Arc", 125, "Chains to two nearby enemies", 135.0, 32.0, 54L),
    VENOM("Venom", 100, "Poisons for 3s. Strongest dose wins.", 145.0, 11.0, 60L),
    INFERNO("Inferno", 110, "Hits every enemy in its short reach", 84.0, 12.0, 24L),
    BEACON("Beacon", 85, "Marks a target for 25% more damage", 150.0, 8.0, 56L),
    SUNDER("Sunder", 115, "Deals triple damage to shields", 145.0, 22.0, 36L),
    TREMOR("Tremor", 135, "Slows groups around the impact", 128.0, 10.0, 45L, requiredRank = 2),
}

enum class CannonSpecialization(val title: String, val radius: Double, val attackIntervalFactor: Double) {
    STANDARD("Standard", Tower.SPLASH_RADIUS, 1.0),
    WIDE("Wide", 76.0, 1.15),
    RAPID("Rapid", 44.0, 0.75),
}

enum class TremorModule(val title: String, val radius: Double, val speedFactor: Double) {
    STANDARD("Standard", 44.0, 0.70),
    WIDE("Wide", 60.0, 0.80),
    FOCUSED("Focused", 32.0, 0.55);

    val slowPercent: Int get() = ((1.0 - speedFactor) * 100).roundToInt()

    companion object {
        const val REQUIRED_RANK = 3
    }
}

enum class RailModule(
    val title: String,
    private val attackIntervalFactor: Double,
    val shieldDamageFactor: Double,
    val relayDamageFactor: Double,
) {
    STANDARD("Standard", 1.0, 1.0, 1.0),
    RELAY_HUNTER("Relay Hunter", 1.25, 2.0, 1.25);

    fun intervalFactor(type: TowerType): Double = if (type == TowerType.RAIL) attackIntervalFactor else 1.0

    internal fun maxAttackInterval(type: TowerType): Long =
        (type.baseAttackIntervalTicks * intervalFactor(type)).toLong()
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
    val arcJumps: List<Waypoint> = emptyList(),
    val areaRadius: Double = 0.0,
    val isEcho: Boolean = false,
    val tremorModule: TremorModule = TremorModule.STANDARD,
    val railModule: RailModule = RailModule.STANDARD,
) {
    // Older recordings did not store the Cannon radius.
    internal val cannonSplashRadius: Double get() = if (areaRadius > 0.0) areaRadius else Tower.SPLASH_RADIUS
}

/** Exact next-level values, calculated by the same rules as the active tower stats. */
data class TowerUpgrade(val cost: Int, val range: Double, val damage: Double, val attackIntervalTicks: Long)

enum class TargetPriority(val title: String) {
    FIRST("First"),
    STRONGEST("Strongest"),
    NEAREST("Nearest"),
}

private val ARC_DAMAGE_FACTORS = doubleArrayOf(1.0, 0.65, 0.4)

internal fun applyTowerHit(
    type: TowerType,
    enemy: Enemy,
    damage: Double,
    impactIndex: Int = 0,
    tremorModule: TremorModule = TremorModule.STANDARD,
    railModule: RailModule = RailModule.STANDARD,
) {
    val strength = if (type == TowerType.ARC) ARC_DAMAGE_FACTORS[impactIndex] else 1.0
    enemy.takeTowerDamage(type, damage * strength, railModule)
    if (!enemy.isAlive) return
    when (type) {
        TowerType.FROST -> enemy.applySlow(0.55, 1.5)
        TowerType.TREMOR -> enemy.applySlow(tremorModule.speedFactor, 1.1)
        TowerType.VENOM -> enemy.applyPoison(damage, 3.0)
        TowerType.BEACON -> enemy.mark()
        else -> Unit
    }
}
