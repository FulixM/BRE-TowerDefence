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


internal data class WavePlan(val enemies: List<EnemyType>, val intervals: List<Long>) {
    val description: String = enemies.groupingBy { it }.eachCount().let { counts ->
        EnemyType.entries.mapNotNull { type -> counts[type]?.let { "$it ${type.title}" } }.joinToString(" / ")
    }
}
