package io.felixmenzel.towerdefence.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.felixmenzel.towerdefence.simulation.BattleTerrain
import io.felixmenzel.towerdefence.simulation.BuildGrid
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.RouteGrade
import io.felixmenzel.towerdefence.simulation.Waypoint
import io.felixmenzel.towerdefence.simulation.routeGrades

internal class BoardGuides(private val shapes: ShapeRenderer) {
    private var terrain: BattleTerrain? = null
    private var path: List<Waypoint>? = null
    private var grades = emptyList<RouteGrade>()

    fun drawBuildHints(simulation: GameSimulation) {
        for (center in BuildGrid.centers) {
            val x = center.x.toFloat()
            val y = center.y.toFloat()
            if (simulation.placementError(center.x, center.y) != null) {
                shapes.setColor(0.68f, 0.73f, 0.80f, 0.28f)
                for (offset in 5..15 step 5) {
                    shapes.rectLine(x + offset, y + 9f, x + offset + 4f, y + 15f, 1.2f)
                }
                continue
            }

            shapes.setColor(0.55f, 0.87f, 0.72f, 0.48f)
            for (sideX in -1..1 step 2) for (sideY in -1..1 step 2) {
                val cornerX = x + sideX * 17f
                val cornerY = y + sideY * 17f
                shapes.rectLine(cornerX, cornerY, cornerX - sideX * 6f, cornerY, 1.2f)
                shapes.rectLine(cornerX, cornerY, cornerX, cornerY - sideY * 6f, 1.2f)
            }
        }
    }

    fun drawGrades(simulation: GameSimulation) {
        val activePath = simulation.path
        if (terrain !== simulation.terrain || path !== activePath) {
            terrain = simulation.terrain
            path = activePath
            grades = simulation.terrain.routeGrades(activePath)
        }
        for (grade in grades) {
            val acrossX = -grade.dy
            val acrossY = grade.dx
            // Place the sign beside the centre stripe and point back to the exact boundary.
            val x = grade.x + acrossX * 13f
            val y = grade.y + acrossY * 13f
            val color = if (grade.uphill) UPHILL else DOWNHILL
            shapes.setColor(color.r, color.g, color.b, 0.52f)
            shapes.rectLine(grade.x + acrossX * 5f, grade.y + acrossY * 5f,
                grade.x + acrossX * 20f, grade.y + acrossY * 20f, 1.6f)
            shapes.color = SIGN_BACK
            shapes.circle(x, y, 7f, 20)
            shapes.color = color
            shapes.rectLine(x - 3.5f, y, x + 3.5f, y, 1.6f)
            if (grade.uphill) shapes.rectLine(x, y - 3.5f, x, y + 3.5f, 1.6f)
        }
    }

    companion object {
        private val SIGN_BACK = Color.valueOf("101925")
        val UPHILL = Color.valueOf("FFC477")
        val DOWNHILL = Color.valueOf("7ADADD")
    }
}
