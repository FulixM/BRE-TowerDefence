package io.felixmenzel.towerdefence.simulation

import io.felixmenzel.towerdefence.simulation.EnemyType.ARMORED
import io.felixmenzel.towerdefence.simulation.EnemyType.FAST
import io.felixmenzel.towerdefence.simulation.EnemyType.NORMAL
import io.felixmenzel.towerdefence.simulation.EnemyType.PLATED
import io.felixmenzel.towerdefence.simulation.EnemyType.REGENERATING
import io.felixmenzel.towerdefence.simulation.EnemyType.SHIELDED
import io.felixmenzel.towerdefence.simulation.EnemyType.VANGUARD
import kotlin.math.roundToInt
import kotlin.random.Random

internal object Waves {
    private const val OPENING_WAVES = 10

    private data class Assault(
        val name: String,
        val intervalTicks: Long,
        val groups: Int,
        val squad: List<EnemyType>,
    )

    private val lateWaves = listOf(
        Assault("Shield rush", 27, 5, listOf(SHIELDED, FAST, FAST, NORMAL, SHIELDED, FAST)),
        Assault("Armored column", 27, 5, listOf(ARMORED, PLATED, PLATED, NORMAL, ARMORED, FAST)),
        Assault("Reaver packs", 25, 4, listOf(REGENERATING, FAST, REGENERATING, NORMAL, FAST, REGENERATING, SHIELDED, FAST)),
        Assault("Siege line", 25, 4, listOf(SHIELDED, PLATED, ARMORED, FAST, SHIELDED, PLATED, REGENERATING, VANGUARD)),
        Assault("Final assault", 24, 4, listOf(ARMORED, SHIELDED, PLATED, FAST, REGENERATING, NORMAL, VANGUARD, PLATED, FAST)),
    )

    val total: Int = OPENING_WAVES + lateWaves.size

    private fun assault(wave: Int): Assault? = lateWaves.getOrNull(wave - OPENING_WAVES - 1)

    fun name(wave: Int): String? = assault(wave)?.name

    fun enemyCount(wave: Int): Int = assault(wave)?.let { it.groups * it.squad.size } ?: (8 + (wave - 1) * 2)

    fun intervalTicks(wave: Int): Long = assault(wave)?.intervalTicks ?:
        ((0.85 - (wave - 1) * 0.045).coerceAtLeast(0.45) * GameSimulation.UPS).roundToInt().toLong()

    fun plan(wave: Int, seed: Int?): WavePlan {
        val random = seed?.let { Random(it.toLong() + wave * 1009L) }
        val enemies = List(enemyCount(wave)) { enemyType(wave, it) }
        val interval = intervalTicks(wave)
        return if (random == null) WavePlan(enemies, List(enemies.size) { interval })
        else WavePlan(enemies.shuffled(random), List(enemies.size) { interval + random.nextInt(-4, 5) })
    }

    fun healthMultiplier(wave: Int): Double {
        val openingIndex = minOf(wave, OPENING_WAVES) - 1
        val openingHealth = 1.0 + openingIndex * 0.22 + openingIndex * openingIndex * 0.045
        // Late difficulty comes from the squads as well as health. Avoid extending the quadratic curve.
        val assaultIndex = (wave - OPENING_WAVES).coerceAtLeast(0)
        return openingHealth + 0.60 * assaultIndex
    }

    fun enemyType(wave: Int, index: Int): EnemyType {
        val squad = assault(wave)?.squad
        if (squad != null) return squad[index % squad.size]
        return when {
            wave >= REGENERATING.firstWave && index % 9 == 7 -> REGENERATING
            wave >= SHIELDED.firstWave && index % 7 == 5 -> SHIELDED
            wave >= PLATED.firstWave && index % 7 == 3 -> PLATED
            wave >= ARMORED.firstWave && index % 5 == 4 -> ARMORED
            wave >= FAST.firstWave && index % 3 == 2 -> FAST
            else -> NORMAL
        }
    }
}


enum class MissionMode(val title: String) {
    CAMPAIGN("Campaign"), QUICK("Quick mission");

    val waveCount: Int get() = if (this == QUICK) quickWaves.size else Waves.total

    fun startingGold(difficulty: Difficulty): Int = if (this == QUICK) 330 else difficulty.startingGold

    internal fun plan(wave: Int, seed: Int?): WavePlan =
        if (this == QUICK) quickWaves[wave - 1].plan else Waves.plan(wave, seed)

    internal fun waveName(wave: Int): String? =
        if (this == QUICK) quickWaves.getOrNull(wave - 1)?.name else Waves.name(wave)

    internal fun healthMultiplier(wave: Int): Double =
        if (this == QUICK) quickWaves[wave - 1].health else Waves.healthMultiplier(wave)

    internal fun supports(map: BattleMap, seed: Int?): Boolean =
        this == CAMPAIGN || map == BattleMap.CORRIDOR && seed == null
}

private class QuickWave(
    val name: String, val health: Double, interval: Long, groups: Int, vararg squad: EnemyType,
) {
    val plan = WavePlan(List(squad.size * groups) { squad[it % squad.size] }, List(squad.size * groups) { interval })
}

private val quickWaves = listOf(
    QuickWave("Scout rush", 1.0, 38, 2, NORMAL, FAST, NORMAL, NORMAL),
    QuickWave("Heavy escort", 1.4, 35, 3, FAST, NORMAL, ARMORED, NORMAL),
    QuickWave("Shield crews", 2.0, 32, 3, PLATED, NORMAL, FAST, ARMORED, SHIELDED),
    QuickWave("Reaver chase", 2.7, 30, 3, REGENERATING, NORMAL, FAST, PLATED, ARMORED, SHIELDED),
    QuickWave("Breach teams", 3.5, 28, 4, FAST, VANGUARD, PLATED, SHIELDED, FAST),
    QuickWave("Final surge", 4.4, 26, 4, ARMORED, FAST, VANGUARD, SHIELDED, REGENERATING, PLATED),
)

internal data class WavePlan(val enemies: List<EnemyType>, val intervals: List<Long>) {
    val description: String = describe()

    fun describe(replaceBulwarkWithBoss: Boolean = false): String {
        val counts = enemies.groupingBy { it }.eachCount()
        val groups = EnemyType.entries.mapNotNull { type ->
            val count = (counts[type] ?: 0) - if (replaceBulwarkWithBoss && type == ARMORED) 1 else 0
            if (count > 0) "$count ${type.title}" else null
        }
        val labels = if (replaceBulwarkWithBoss) listOf("Bastion") + groups else groups
        return labels.joinToString(" / ")
    }
}
