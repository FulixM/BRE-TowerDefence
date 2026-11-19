package io.felixmenzel.towerdefence.simulation

import kotlin.math.abs
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
    val isElite: Boolean = false,
    val isBoss: Boolean = false,
    val isCommander: Boolean = false,
    private val escortCommander: Enemy? = null,
    val isMarshalRelay: Boolean = false,
    private val marshalRelays: List<Enemy>? = null,
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
    val isMarshal: Boolean get() = marshalRelays != null
    val supportingRelays: Sequence<Enemy> get() = marshalRelays.orEmpty().asSequence()
        .filter { it.isAlive && !it.hasReachedGoal }
    val bossExposed: Boolean get() = isBoss && if (isMarshal) {
        supportingRelays.none() || Marshal.windows.any { distanceTravelled in it }
    } else Bastion.windows.any { distanceTravelled in it }
    private val bossDamageMultiplier: Double get() = when {
        !isBoss || bossExposed -> 1.0
        isMarshal -> Marshal.PROTECTED_DAMAGE_MULTIPLIER
        else -> Bastion.PROTECTED_DAMAGE_MULTIPLIER
    }
    var health = maxHealth
        private set
    val isAlive: Boolean get() = health > 0.0
    val protectingCommander: Enemy? get() = escortCommander?.takeIf {
        it.isCommander && it.isAlive && !it.hasReachedGoal &&
            abs(it.distanceTravelled - distanceTravelled) <= Commander.PROTECTION_RANGE
    }
    var slowFactor = 1.0
        private set
    private var slowSeconds = 0.0
    val maxShield = maxHealth * type.shieldFraction
    var shield = maxShield
        private set
    var markedSeconds = 0.0
        private set
    val isPoisoned: Boolean get() = poisonSeconds > 0.0
    private var poisonSeconds = 0.0
    private var poisonPerSecond = 0.0
    private var recoveryDelay = 0.0
    val isRegenerating: Boolean get() = type.regenerationFraction > 0.0 &&
        isAlive && !hasReachedGoal && health < maxHealth && recoveryDelay == 0.0

    fun update(stepSeconds: Double) {
        require(stepSeconds.isFinite() && stepSeconds >= 0.0)
        previousX = x
        previousY = y
        if (!isAlive || hasReachedGoal) return

        if (poisonSeconds > 0.0) {
            takeDamage(poisonPerSecond * min(stepSeconds, poisonSeconds))
            poisonSeconds = (poisonSeconds - stepSeconds).coerceAtLeast(0.0)
            if (!isAlive) return
        }
        val recoverySeconds = (stepSeconds - recoveryDelay).coerceAtLeast(0.0)
        recoveryDelay = (recoveryDelay - stepSeconds).coerceAtLeast(0.0)
        health = (health + maxHealth * type.regenerationFraction * recoverySeconds).coerceAtMost(maxHealth)
        markedSeconds = (markedSeconds - stepSeconds).coerceAtLeast(0.0)

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

    fun takeDamage(damage: Double, piercesArmor: Boolean = false) {
        applyDamage(damage, piercesArmor, shieldMultiplier = 1.0)
    }

    internal fun takeShieldbreakingDamage(damage: Double) {
        applyDamage(damage, piercesArmor = false, shieldMultiplier = 3.0)
    }

    internal fun takeRailDamage(damage: Double, module: RailModule) {
        val relayFactor = if (isMarshalRelay) module.relayDamageFactor else 1.0
        applyDamage(damage * relayFactor, piercesArmor = true, shieldMultiplier = module.shieldDamageFactor)
    }

    private fun applyDamage(damage: Double, piercesArmor: Boolean, shieldMultiplier: Double) {
        require(damage.isFinite() && damage >= 0.0) { "Damage must be finite and non-negative" }
        if (damage == 0.0 || !isAlive) return
        recoveryDelay = 1.5
        val incoming = damage * (if (markedSeconds > 0.0) 1.25 else 1.0) *
            (if (piercesArmor) 1.0 else type.damageMultiplier) *
            bossDamageMultiplier *
            (if (protectingCommander != null) Commander.ESCORT_DAMAGE_MULTIPLIER else 1.0)
        val absorbed = min(shield, incoming * shieldMultiplier)
        shield -= absorbed
        // Only damage spent against the shield gains the bonus; overflow uses the normal rate.
        health = (health - (incoming - absorbed / shieldMultiplier)).coerceAtLeast(0.0)
    }

    /** Slows never multiply; repeated shots refresh the strongest active slow. */
    fun applySlow(factor: Double, durationSeconds: Double) {
        require(factor.isFinite() && factor > 0.0 && factor <= 1.0)
        require(durationSeconds.isFinite() && durationSeconds >= 0.0)
        if (durationSeconds == 0.0) return
        slowFactor = min(slowFactor, factor)
        slowSeconds = maxOf(slowSeconds, durationSeconds)
    }

    internal fun applyPoison(damagePerSecond: Double, durationSeconds: Double) {
        if (poisonSeconds == 0.0 || damagePerSecond >= poisonPerSecond) {
            poisonPerSecond = damagePerSecond
            poisonSeconds = durationSeconds
        }
    }

    internal fun mark() {
        markedSeconds = 2.5
    }

    fun interpolatedX(alpha: Double): Double = previousX + (x - previousX) * alpha.coerceIn(0.0, 1.0)
    fun interpolatedY(alpha: Double): Double = previousY + (y - previousY) * alpha.coerceIn(0.0, 1.0)
}

enum class EnemyType(
    val title: String, val baseHealth: Double, val baseSpeed: Double, val bounty: Int,
    val firstWave: Int, val description: String,
    val damageMultiplier: Double = 1.0, val shieldFraction: Double = 0.0, val regenerationFraction: Double = 0.0,
) {
    NORMAL("Drifter", 80.0, 50.0, 9, 1, "Standard enemy"),
    FAST("Runner", 55.0, 86.0, 10, 3, "Fast with low health"),
    // This is a high-health enemy, not a damage-resistance mechanic.
    ARMORED("Bulwark", 190.0, 36.0, 16, 5, "Slow with high health"),
    PLATED("Ironclad", 73.0, 43.0, 15, 6, "Takes 40% less damage. Rail ignores armor.", damageMultiplier = 0.6),
    SHIELDED("Warden", 74.0, 49.0, 15, 7, "Shield absorbs damage before health.", shieldFraction = 0.45),
    REGENERATING("Reaver", 95.0, 56.0, 16, 8, "Heals after 1.5s without damage. Keep it under fire.", regenerationFraction = 0.08),
    VANGUARD(
        "Vanguard", 105.0, 39.0, 20, 14, "Sunder breaks its shield faster. Rail bypasses its armor.",
        damageMultiplier = 0.75, shieldFraction = 0.30,
    ),
}


internal object Commander {
    const val HEALTH_MULTIPLIER = 1.12
    const val PROTECTION_RANGE = 105.0
    const val ESCORT_DAMAGE_MULTIPLIER = 0.50
}
