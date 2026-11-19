package io.felixmenzel.towerdefence.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Terrace
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The top edges follow the simulation radii. Cliff faces extend visually down-screen. */
internal class TerraceRenderer(private val shapes: ShapeRenderer) {
    private data class Stone(val lit: Color, val shade: Color, val cliff: Color, val rim: Color)
    private val stones = mapOf(
        BattleMap.CORRIDOR to Stone(Color.valueOf("3B5C55"), Color.valueOf("253C3C"), Color.valueOf("121F29"), Color.valueOf("628A7B")),
        BattleMap.SWITCHBACK to Stone(Color.valueOf("6D5841"), Color.valueOf("46392E"), Color.valueOf("221D20"), Color.valueOf("AA8760")),
        BattleMap.CROSSWIND to Stone(Color.valueOf("47677B"), Color.valueOf("2B414F"), Color.valueOf("172436"), Color.valueOf("7795A8")),
        BattleMap.TWIN_LOCKS to Stone(Color.valueOf("5C5275"), Color.valueOf("38324C"), Color.valueOf("201B31"), Color.valueOf("9384AC")),
    )
    private val edge = Array(64) { i ->
        val angle = 2 * PI * i / 64
        UnitPoint(cos(angle).toFloat(), sin(angle).toFloat())
    }
    private val top = Color()
    private val bottom = Color()
    private val center = Color()
    private val facet = Color()

    fun draw(map: BattleMap, terraces: List<Terrace>) {
        val stone = stones.getValue(map)
        for (terrace in terraces) {
            val x = terrace.x.toFloat()
            val y = terrace.y.toFloat()
            tier(x, y, terrace.radius.toFloat(), 11f, 0.22f, stone)
            tier(x, y, terrace.summitRadius.toFloat(), 8f, 0.62f, stone)
        }
    }

    private fun tier(x: Float, y: Float, radius: Float, depth: Float, light: Float, stone: Stone) {
        for (i in edge.size / 2 until edge.size) {
            val a = edge[i]
            val b = edge[(i + 1) % edge.size]
            val ax = x + a.x * radius
            val ay = y + a.y * radius
            val bx = x + b.x * radius
            val by = y + b.y * radius
            top.set(stone.cliff).lerp(stone.lit, 0.32f + (i % 5) * 0.048f)
            bottom.set(stone.cliff).lerp(stone.shade, (i % 3) * 0.065f)
            shapes.triangle(ax, ay, bx, by, bx, by - depth, top, top, bottom)
            shapes.triangle(ax, ay, bx, by - depth, ax, ay - depth, top, bottom, bottom)
        }
        center.set(stone.shade).lerp(stone.lit, light)
        for (i in edge.indices) {
            val a = edge[i]
            val b = edge[(i + 1) % edge.size]
            val ax = x + a.x * radius
            val ay = y + a.y * radius
            val bx = x + b.x * radius
            val by = y + b.y * radius
            facet.set(stone.shade).lerp(stone.lit, (light + a.y * 0.20f + (i / 8 % 3) * 0.055f).coerceIn(0f, 1f))
            shapes.triangle(x, y, ax, ay, bx, by, center, facet, facet)
            shapes.color = top.set(stone.shade).lerp(stone.rim, 0.45f + a.y * 0.23f + light * 0.25f)
            shapes.rectLine(ax, ay, bx, by, 1.2f)
        }

        // Broken striations suggest layered rock without adding clutter along the lane.
        for (i in 0 until edge.size step 8) {
            val a = edge[(i + (x.toInt() + y.toInt()) % 5) % edge.size]
            val b = edge[(i + 2 + (x.toInt() + y.toInt()) % 5) % edge.size]
            val inset = radius * (0.78f + i % 3 * 0.055f)
            shapes.color = top.set(stone.shade).lerp(stone.rim, 0.31f + light * 0.16f)
            shapes.rectLine(x + a.x * inset, y + a.y * inset, x + b.x * inset, y + b.y * inset, 1.1f)
        }
    }

    private data class UnitPoint(val x: Float, val y: Float)
}
