package io.felixmenzel.towerdefence.simulation

import java.util.Collections
import kotlin.random.Random

/** Terraces allow building and walking. They affect direct reach without blocking line of sight. */
class BattleTerrain private constructor(terraces: List<Terrace>) {
    val terraces: List<Terrace> = Collections.unmodifiableList(terraces)

    fun heightAt(x: Double, y: Double): Int = terraces.maxOfOrNull { it.heightAt(x, y) } ?: 0

    fun baseRange(range: Double): Double = if (terraces.isEmpty()) range else range * 0.94

    fun rangeTowards(range: Double, sourceHeight: Int, targetHeight: Int): Double =
        range * (1.0 + (sourceHeight - targetHeight) * 0.10)

    fun canReach(x: Double, y: Double, sourceHeight: Int, range: Double, targetX: Double, targetY: Double): Boolean {
        val dx = targetX - x
        val dy = targetY - y
        val reach = rangeTowards(range, sourceHeight, heightAt(targetX, targetY))
        return dx * dx + dy * dy <= reach * reach
    }

    companion object {
        val FLAT = BattleTerrain(emptyList())

        internal fun forMap(map: BattleMap, seed: Int?): BattleTerrain {
            val terraces = when (map) {
                BattleMap.CORRIDOR -> listOf(Terrace(140, 166, 96), Terrace(450, 295, 106), Terrace(729, 174, 96))
                BattleMap.SWITCHBACK -> listOf(Terrace(155, 356, 87), Terrace(386, 196, 103), Terrace(642, 356, 103))
                BattleMap.CROSSWIND -> listOf(Terrace(165, 177, 101), Terrace(470, 306, 108), Terrace(725, 198, 103))
                BattleMap.TWIN_LOCKS -> listOf(Terrace(160, 322, 92), Terrace(333, 154, 87),
                    Terrace(333, 448, 72), Terrace(665, 352, 98))
            }
            // Use a terrain-specific stream so generation cannot change the wave RNG.
            val random = seed?.let { Random(it.toLong() * 41L + map.code.single().code * 101L) }
            return BattleTerrain(if (random == null) terraces else terraces.map { terrace ->
                terrace.copy(x = terrace.x + random.nextInt(-24, 25), y = terrace.y + random.nextInt(-18, 19),
                    radius = terrace.radius + random.nextInt(-10, 11))
            })
        }
    }
}

data class Terrace(val x: Int, val y: Int, val radius: Int) {
    val summitRadius: Int get() = radius * 5 / 9

    internal fun heightAt(targetX: Double, targetY: Double): Int {
        val dx = targetX - x
        val dy = targetY - y
        val distance = dx * dx + dy * dy
        return when {
            distance <= summitRadius * summitRadius -> 2
            distance <= radius * radius -> 1
            else -> 0
        }
    }
}
