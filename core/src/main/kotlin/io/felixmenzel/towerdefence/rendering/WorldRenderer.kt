package io.felixmenzel.towerdefence.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import io.felixmenzel.towerdefence.simulation.Enemy
import io.felixmenzel.towerdefence.simulation.EnemyType
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Procedural artwork only: every visual effect follows the simulation clock, including pause. */
class WorldRenderer : Disposable {
    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val viewportBuffer = BufferUtils.newIntBuffer(4)
    private val scissorBuffer = BufferUtils.newIntBuffer(4)
    private val projectedPoint = Vector3()

    fun render(
        simulation: GameSimulation,
        alpha: Double,
        projection: Matrix4,
        selectedTower: Tower? = null,
        buildType: TowerType? = null,
        cursorX: Double? = null,
        cursorY: Double? = null,
    ) {
        shapes.projectionMatrix = projection
        batch.projectionMatrix = projection
        val hadBlend = Gdx.gl.glIsEnabled(GL20.GL_BLEND)
        val hadScissor = beginBoardClip(projection)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawBoard(simulation)
        selectedTower?.let { drawRange(it.x.toFloat(), it.y.toFloat(), it.range.toFloat(), CYAN) }
        for (tower in simulation.towers) drawTower(tower, simulation, tower === selectedTower)
        for (enemy in simulation.enemies) drawEnemy(enemy, simulation, alpha)
        drawShots(simulation)
        if (buildType != null && cursorX != null && cursorY != null) {
            drawPreview(simulation, buildType, cursorX.toFloat(), cursorY.toFloat())
        }
        shapes.end()
        batch.begin()
        drawLabels(simulation)
        batch.end()
        if (hadBlend) Gdx.gl.glEnable(GL20.GL_BLEND) else Gdx.gl.glDisable(GL20.GL_BLEND)
        endBoardClip(hadScissor)
    }

    private fun drawBoard(simulation: GameSimulation) {
        shapes.color = BOARD
        shapes.rect(0f, 0f, WIDTH, HEIGHT)
        shapes.color = GRID
        for (x in 0..900 step 30) shapes.rect(x.toFloat(), 0f, 1f, HEIGHT)
        for (y in 0..560 step 30) shapes.rect(0f, y.toFloat(), WIDTH, 1f)
        shapes.color = GRID_MAJOR
        for (x in 60..870 step 120) {
            for (y in 60..540 step 120) {
                shapes.rect(x - 3f, y.toFloat(), 7f, 1f)
                shapes.rect(x.toFloat(), y - 3f, 1f, 7f)
            }
        }
        // Each layer has round joints, so the original orthogonal path remains readable at bends.
        drawPath(simulation, 48f, PATH_EDGE)
        drawPath(simulation, 42f, PATH)
        drawPath(simulation, 26f, PATH_INNER)
        shapes.color = PATH_MARK
        val path = simulation.path
        for (index in 0 until path.lastIndex) {
            val start = path[index]
            val end = path[index + 1]
            val dx = (end.x - start.x).toFloat()
            val dy = (end.y - start.y).toFloat()
            val length = sqrt(dx * dx + dy * dy)
            if (length < 1f) continue
            val ux = dx / length
            val uy = dy / length
            var position = 42f
            while (position < length - 24f) {
                val x = start.x.toFloat() + ux * position
                val y = start.y.toFloat() + uy * position
                shapes.rectLine(x - ux * 5f - uy * 4f, y - uy * 5f + ux * 4f, x, y, 1.5f)
                shapes.rectLine(x - ux * 5f + uy * 4f, y - uy * 5f - ux * 4f, x, y, 1.5f)
                position += 54f
            }
        }
        path.firstOrNull()?.let { drawGate(it.x.toFloat(), it.y.toFloat(), CORAL, false, simulation.tick) }
        path.lastOrNull()?.let { drawGate(it.x.toFloat(), it.y.toFloat(), CYAN, true, simulation.tick) }
        // Subtle edge rails and corner brackets frame the play area.
        shapes.color = BORDER
        shapes.rect(0f, 0f, WIDTH, 2f)
        shapes.rect(0f, HEIGHT - 2f, WIDTH, 2f)
        for (x in listOf(10f, WIDTH - 10f)) {
            for (y in listOf(10f, HEIGHT - 10f)) {
                val dx = if (x < WIDTH / 2) 20f else -20f
                val dy = if (y < HEIGHT / 2) 20f else -20f
                shapes.rectLine(x, y, x + dx, y, 2f)
                shapes.rectLine(x, y, x, y + dy, 2f)
            }
        }
    }

    private fun drawPath(simulation: GameSimulation, width: Float, color: Color) {
        shapes.color = color
        simulation.path.forEach { shapes.circle(it.x.toFloat(), it.y.toFloat(), width / 2f, 24) }
        for (index in 0 until simulation.path.lastIndex) {
            val start = simulation.path[index]
            val end = simulation.path[index + 1]
            shapes.rectLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), width)
        }
    }

    private fun drawGate(x: Float, y: Float, color: Color, core: Boolean, tick: Long) {
        shapes.color = SHADOW
        shapes.circle(x + 2f, y - 3f, 28f, 24)
        shapes.color = PANEL
        shapes.circle(x, y, 27f, 24)
        ring(x, y, 26f, 2f, color)
        shapes.color = color
        if (core) {
            hexagon(x, y, 13f)
            shapes.color = BOARD
            hexagon(x, y, 8f)
            shapes.color = color
            shapes.rect(x - 3f, y - 3f, 6f, 6f)
        } else {
            for (offset in -1..1) {
                val left = x - 10f + offset * 7f
                shapes.rectLine(left, y - 10f, left + 6f, y, 2f)
                shapes.rectLine(left + 6f, y, left, y + 10f, 2f)
            }
        }
        val pulse = (tick % 120L).toFloat() / 120f
        ring(x, y, 29f + pulse * 8f, 1f, color, (1f - pulse) * 0.25f)
    }

    private fun drawRange(x: Float, y: Float, range: Float, color: Color) {
        shapes.setColor(color.r, color.g, color.b, 0.055f)
        shapes.circle(x, y, range, 80)
        ring(x, y, range, 1.3f, color, 0.55f)
    }

    private fun drawTower(tower: Tower, simulation: GameSimulation, selected: Boolean) {
        val x = tower.x.toFloat()
        val y = tower.y.toFloat()
        val color = towerColor(tower.type)
        shapes.color = SHADOW
        shapes.circle(x + 2f, y - 4f, 23f, 24)
        shapes.color = BORDER
        hexagon(x, y, 22f)
        shapes.color = PANEL
        hexagon(x, y, 18f)
        ring(x, y, 15f, 1.5f, color, 0.5f)
        if (selected) ring(x, y, 26f, 1.5f, CYAN)
        var target: Enemy? = null
        for (enemy in simulation.enemies) {
            if (!enemy.isAlive || enemy.hasReachedGoal) continue
            val distance = (enemy.x - tower.x) * (enemy.x - tower.x) + (enemy.y - tower.y) * (enemy.y - tower.y)
            if (distance > tower.range * tower.range) continue
            val previous = target
            if (previous == null || enemy.distanceTravelled > previous.distanceTravelled ||
                (enemy.distanceTravelled == previous.distanceTravelled && enemy.id < previous.id)) {
                target = enemy
            }
        }
        val lastShot = simulation.shots.lastOrNull { it.fromX == tower.x && it.fromY == tower.y }
        val angle = when {
            lastShot != null -> atan2(lastShot.toY - tower.y, lastShot.toX - tower.x).toFloat()
            target != null -> atan2(target.y - tower.y, target.x - tower.x).toFloat()
            else -> 0.75f
        }
        drawTurret(tower.type, x, y, angle, color)
        for (level in 0 until tower.level) {
            shapes.color = color
            shapes.rect(x - (tower.level * 3f) + level * 6f, y - 28f, 4f, 3f)
        }
    }

    private fun drawTurret(type: TowerType, x: Float, y: Float, angle: Float, color: Color) {
        val dx = cos(angle)
        val dy = sin(angle)
        shapes.color = color
        when (type) {
            TowerType.RAPID -> {
                shapes.circle(x, y, 9f, 20)
                shapes.rectLine(x, y, x + dx * 23f, y + dy * 23f, 6f)
                shapes.color = PANEL
                shapes.circle(x, y, 4f, 16)
            }
            TowerType.CANNON -> {
                hexagon(x, y, 11f)
                shapes.rectLine(x, y, x + dx * 24f, y + dy * 24f, 12f)
                shapes.color = PANEL
                shapes.rectLine(x + dx * 16f, y + dy * 16f, x + dx * 26f, y + dy * 26f, 5f)
            }
            TowerType.FROST -> {
                diamond(x, y, 12f)
                shapes.color = PANEL
                diamond(x, y, 6f)
                for (index in 0..5) {
                    val a = index * PI_THIRD
                    shapes.color = color
                    shapes.rectLine(x + cos(a) * 15f, y + sin(a) * 15f, x + cos(a) * 20f, y + sin(a) * 20f, 2f)
                }
            }
        }
    }

    private fun drawEnemy(enemy: Enemy, simulation: GameSimulation, alpha: Double) {
        if (!enemy.isAlive || enemy.hasReachedGoal) return
        val x = enemy.interpolatedX(alpha).toFloat()
        val y = enemy.interpolatedY(alpha).toFloat()
        if (enemy.slowFactor < 1.0) {
            ring(x, y, 18f, 1.5f, ICE, 0.65f)
            for (index in 0..5) {
                val angle = index * PI_THIRD + (simulation.tick % 360L).toFloat() * 0.008f
                shapes.color = ICE
                shapes.rectLine(x + cos(angle) * 16f, y + sin(angle) * 16f,
                    x + cos(angle) * 21f, y + sin(angle) * 21f, 1.5f)
            }
        }
        val radius = when (enemy.type) { EnemyType.ARMORED -> 13f; EnemyType.FAST -> 9f; EnemyType.NORMAL -> 11f }
        val color = when (enemy.type) { EnemyType.ARMORED -> ORANGE; EnemyType.FAST -> MAGENTA; EnemyType.NORMAL -> CORAL }
        shapes.color = SHADOW
        shapes.circle(x + 2f, y - 3f, radius + 2f, 16)
        shapes.color = color
        when (enemy.type) {
            EnemyType.ARMORED -> {
                hexagon(x, y, radius)
                shapes.color = PANEL
                hexagon(x, y, radius - 4f)
                shapes.color = color
                shapes.rect(x - 4f, y - 4f, 8f, 8f)
            }
            EnemyType.FAST -> {
                shapes.triangle(x - 10f, y - 8f, x - 2f, y, x - 10f, y + 8f)
                shapes.triangle(x - 2f, y - 8f, x + 10f, y, x - 2f, y + 8f)
            }
            EnemyType.NORMAL -> {
                diamond(x, y, radius)
                shapes.color = PANEL
                diamond(x, y, 4f)
            }
        }
        val width = radius * 2f + 4f
        shapes.color = SHADOW
        shapes.rect(x - width / 2f - 1f, y + radius + 7f, width + 2f, 5f)
        val ratio = (enemy.health / enemy.maxHealth).toFloat().coerceIn(0f, 1f)
        shapes.color = if (ratio < 0.3f) CORAL else HEALTH
        shapes.rect(x - width / 2f, y + radius + 8f, width * ratio, 3f)
    }

    private fun drawShots(simulation: GameSimulation) {
        // The simulation retains only twelve ticks of shots. This bounds cost and freezes on pause.
        for (shot in simulation.shots) {
            val age = (simulation.tick - shot.tick).toFloat().coerceAtLeast(0f) / GameSimulation.SHOT_LIFETIME_TICKS.toFloat()
            if (age >= 1f) continue
            val color = towerColor(shot.type)
            val fromX = shot.fromX.toFloat()
            val fromY = shot.fromY.toFloat()
            val toX = shot.toX.toFloat()
            val toY = shot.toY.toFloat()
            val opacity = 1f - age
            shapes.setColor(color.r, color.g, color.b, opacity * 0.18f)
            shapes.rectLine(fromX, fromY, toX, toY, if (shot.type == TowerType.CANNON) 9f else 5f)
            shapes.setColor(color.r, color.g, color.b, opacity)
            shapes.rectLine(fromX, fromY, toX, toY, if (shot.type == TowerType.CANNON) 3f else 1.6f)
            shapes.circle(fromX, fromY, 4f * opacity, 12)
            val radius = when (shot.type) {
                TowerType.CANNON -> 8f + age * 42f
                TowerType.FROST -> 5f + age * 17f
                TowerType.RAPID -> 3f + age * 9f
            }
            ring(toX, toY, radius, 2f * opacity + 0.5f, color, opacity)
            for (index in 0..3) {
                val angle = index * TWO_PI / 4f + 0.4f
                shapes.setColor(color.r, color.g, color.b, opacity)
                shapes.rectLine(toX + cos(angle) * radius * 0.6f, toY + sin(angle) * radius * 0.6f,
                    toX + cos(angle) * (radius + 3f), toY + sin(angle) * (radius + 3f), 1.4f)
            }
        }
    }

    private fun drawPreview(simulation: GameSimulation, type: TowerType, x: Float, y: Float) {
        val valid = simulation.placementError(x.toDouble(), y.toDouble()) == null &&
            simulation.gold >= type.cost &&
            (simulation.state == GameState.PREPARING || simulation.state == GameState.RUNNING)
        val color = if (valid) HEALTH else CORAL
        drawRange(x, y, type.baseRange.toFloat(), color)
        shapes.setColor(color.r, color.g, color.b, 0.25f)
        hexagon(x, y, 22f)
        ring(x, y, 23f, 2f, color, 0.8f)
        drawTurret(type, x, y, 0.75f, color)
        if (!valid) {
            shapes.color = CORAL
            shapes.rectLine(x - 7f, y - 7f, x + 7f, y + 7f, 2f)
            shapes.rectLine(x - 7f, y + 7f, x + 7f, y - 7f, 2f)
        }
    }

    private fun drawLabels(simulation: GameSimulation) {
        font.data.setScale(0.9f)
        font.color = MUTED
        font.draw(batch, "BRE / DEFENSE GRID", 32f, 490f)
        font.data.setScale(0.75f)
        font.color = DIM
        font.draw(batch, "SECTOR 01    /    ZIGZAG CORRIDOR", 32f, 470f)
        font.draw(batch, "BUILD OFF THE ROUTE  /  PROTECT THE CORE", 32f, 28f)
        simulation.path.firstOrNull()?.let {
            font.color = CORAL
            font.draw(batch, "ENTRY", it.x.toFloat() - 18f, it.y.toFloat() + 49f)
        }
        simulation.path.lastOrNull()?.let {
            font.color = CYAN
            font.draw(batch, "CORE", it.x.toFloat() - 15f, it.y.toFloat() + 49f)
        }
    }

    /** Clip ranges/effects to the board even when a selected tower stands by the HUD. */
    private fun beginBoardClip(projection: Matrix4): Boolean {
        val hadScissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
        if (hadScissor) {
            scissorBuffer.clear()
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, scissorBuffer)
        }
        viewportBuffer.clear()
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportBuffer)
        val vx = viewportBuffer.get(0)
        val vy = viewportBuffer.get(1)
        val vw = viewportBuffer.get(2)
        val vh = viewportBuffer.get(3)
        projectedPoint.set(0f, 0f, 0f).prj(projection)
        var left = floor(vx + (projectedPoint.x + 1f) * vw / 2f).toInt()
        var bottom = floor(vy + (projectedPoint.y + 1f) * vh / 2f).toInt()
        projectedPoint.set(WIDTH, HEIGHT, 0f).prj(projection)
        var right = ceil(vx + (projectedPoint.x + 1f) * vw / 2f).toInt()
        var top = ceil(vy + (projectedPoint.y + 1f) * vh / 2f).toInt()
        if (hadScissor) {
            left = maxOf(left, scissorBuffer.get(0))
            bottom = maxOf(bottom, scissorBuffer.get(1))
            right = minOf(right, scissorBuffer.get(0) + scissorBuffer.get(2))
            top = minOf(top, scissorBuffer.get(1) + scissorBuffer.get(3))
        }
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(left, bottom, (right - left).coerceAtLeast(0), (top - bottom).coerceAtLeast(0))
        return hadScissor
    }

    private fun endBoardClip(hadScissor: Boolean) {
        if (hadScissor) {
            Gdx.gl.glScissor(scissorBuffer.get(0), scissorBuffer.get(1), scissorBuffer.get(2), scissorBuffer.get(3))
        } else {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        }
    }

    private fun ring(x: Float, y: Float, radius: Float, width: Float, color: Color, alpha: Float = 1f) {
        shapes.setColor(color.r, color.g, color.b, alpha)
        val count = if (radius > 40f) 72 else 36
        for (index in 0 until count) {
            val a = index * TWO_PI / count
            val b = (index + 1) * TWO_PI / count
            shapes.rectLine(x + cos(a) * radius, y + sin(a) * radius, x + cos(b) * radius, y + sin(b) * radius, width)
        }
    }

    private fun diamond(x: Float, y: Float, radius: Float) {
        shapes.triangle(x - radius, y, x, y + radius, x + radius, y)
        shapes.triangle(x - radius, y, x, y - radius, x + radius, y)
    }

    private fun hexagon(x: Float, y: Float, radius: Float) {
        for (index in 0..5) {
            val a = index * PI_THIRD
            val b = (index + 1) * PI_THIRD
            shapes.triangle(x, y, x + cos(a) * radius, y + sin(a) * radius, x + cos(b) * radius, y + sin(b) * radius)
        }
    }

    private fun towerColor(type: TowerType): Color = when (type) {
        TowerType.RAPID -> CYAN
        TowerType.CANNON -> ORANGE
        TowerType.FROST -> ICE
    }

    override fun dispose() {
        font.dispose()
        batch.dispose()
        shapes.dispose()
    }

    private companion object {
        const val WIDTH = 900f
        const val HEIGHT = 560f
        const val TWO_PI = 6.2831855f
        const val PI_THIRD = 1.0471976f
        val BOARD = Color.valueOf("0b101c")
        val PANEL = Color.valueOf("151e2e")
        val GRID = Color.valueOf("111a29")
        val GRID_MAJOR = Color.valueOf("223347")
        val BORDER = Color.valueOf("304257")
        val PATH_EDGE = Color.valueOf("34485b")
        val PATH = Color.valueOf("202e40")
        val PATH_INNER = Color.valueOf("26364a")
        val PATH_MARK = Color.valueOf("4a627a")
        val SHADOW = Color(0.005f, 0.009f, 0.02f, 0.75f)
        val CYAN = Color.valueOf("59d8ea")
        val ORANGE = Color.valueOf("ffba6b")
        val ICE = Color.valueOf("a8bfff")
        val CORAL = Color.valueOf("ff7d83")
        val MAGENTA = Color.valueOf("e28cdb")
        val HEALTH = Color.valueOf("8ae0b4")
        val MUTED = Color.valueOf("9aafc6")
        val DIM = Color.valueOf("5e768f")
    }
}
