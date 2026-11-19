package io.felixmenzel.towerdefence.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import io.felixmenzel.towerdefence.rendering.EnemyDefenseColors.ARMOR
import io.felixmenzel.towerdefence.rendering.EnemyDefenseColors.HEALTH
import io.felixmenzel.towerdefence.rendering.EnemyDefenseColors.REGENERATION
import io.felixmenzel.towerdefence.rendering.EnemyDefenseColors.SHIELD
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Bastion
import io.felixmenzel.towerdefence.simulation.Marshal
import io.felixmenzel.towerdefence.simulation.Enemy
import io.felixmenzel.towerdefence.simulation.EnemyType
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import io.felixmenzel.towerdefence.simulation.Waypoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Procedural artwork only: every visual effect follows the simulation clock, including pause. */
class WorldRenderer(private val fonts: ScreenFonts) : Disposable {
    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val viewportBuffer = BufferUtils.newIntBuffer(4)
    private val scissorBuffer = BufferUtils.newIntBuffer(4)
    private val projectedPoint = Vector3()
    private val hitColor = Color()
    private var pixelsPerUnit = 1f
    private val boardClipBuffer = BufferUtils.newIntBuffer(4)
    private val sampleBuffer = BufferUtils.newIntBuffer(1)
    private val boardProjection = Matrix4().setToOrtho2D(0f, 0f, WIDTH, HEIGHT)
    private var fallbackBuffer: FrameBuffer? = null
    private var fallbackRegion: TextureRegion? = null

    fun render(
        simulation: GameSimulation,
        alpha: Double,
        projection: Matrix4,
        selectedTower: Tower? = null,
        buildType: TowerType? = null,
        cursorX: Double? = null,
        cursorY: Double? = null,
    ) {
        batch.projectionMatrix = projection
        val hadBlend = Gdx.gl.glIsEnabled(GL20.GL_BLEND)
        val hadScissor = beginBoardClip(projection)
        val nativeDensity = abs(projection.`val`[Matrix4.M00]) * viewportBuffer.get(2) / 2f
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
            GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sampleBuffer.clear()
        Gdx.gl.glGetIntegerv(GL20.GL_SAMPLES, sampleBuffer)
        val supersample = sampleBuffer.get(0) < 4
        if (supersample) {
            val target = supersampledBoard(nativeDensity)
            boardClipBuffer.clear()
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, boardClipBuffer)
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
            target.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            pixelsPerUnit = target.width / WIDTH
            shapes.projectionMatrix = boardProjection
        } else {
            pixelsPerUnit = nativeDensity
            shapes.projectionMatrix = projection
        }
        drawGeometry(simulation, alpha, selectedTower, buildType, cursorX, cursorY)
        if (supersample) {
            fallbackBuffer!!.end()
            Gdx.gl.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3))
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(boardClipBuffer.get(0), boardClipBuffer.get(1), boardClipBuffer.get(2), boardClipBuffer.get(3))
        }
        batch.begin()
        if (supersample) batch.draw(fallbackRegion!!, 0f, 0f, WIDTH, HEIGHT)
        // Text stays in the native framebuffer, including on the supersampling fallback.
        drawLabels(simulation)
        batch.end()
        if (hadBlend) Gdx.gl.glEnable(GL20.GL_BLEND) else Gdx.gl.glDisable(GL20.GL_BLEND)
        endBoardClip(hadScissor)
    }

    /** Use 2x supersampling when the window system cannot supply a multisampled buffer. */
    private fun supersampledBoard(nativeDensity: Float): FrameBuffer {
        sampleBuffer.clear()
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, sampleBuffer)
        val limit = minOf(sampleBuffer.get(0), 4096).toFloat()
        val density = minOf(nativeDensity * 2f, limit / WIDTH, limit / HEIGHT)
        val width = ceil(WIDTH * density).toInt().coerceAtLeast(1)
        val height = ceil(HEIGHT * density).toInt().coerceAtLeast(1)
        val current = fallbackBuffer
        if (current != null && current.width == width && current.height == height) return current
        current?.dispose()
        val target = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        target.colorBufferTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        fallbackBuffer = target
        fallbackRegion = TextureRegion(target.colorBufferTexture).apply { flip(false, true) }
        return target
    }

    private fun drawGeometry(simulation: GameSimulation, alpha: Double, selectedTower: Tower?,
        buildType: TowerType?, cursorX: Double?, cursorY: Double?) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawBoard(simulation)
        selectedTower?.let { drawRange(it.x.toFloat(), it.y.toFloat(), it.range.toFloat(), CYAN) }
        for (tower in simulation.towers) drawTower(tower, simulation, tower === selectedTower)
        shapes.color = COMMAND_LINK
        for (escort in simulation.enemies) {
            val commander = escort.protectingCommander ?: continue
            shapes.rectLine(
                commander.interpolatedX(alpha).toFloat(), commander.interpolatedY(alpha).toFloat(),
                escort.interpolatedX(alpha).toFloat(), escort.interpolatedY(alpha).toFloat(), 2.5f,
            )
        }
        val marshal = simulation.activeBoss?.takeIf { it.isMarshal && !it.bossExposed }
        if (marshal != null) {
            shapes.color = MARSHAL_LINK
            for (relay in marshal.supportingRelays) {
                shapes.rectLine(marshal.interpolatedX(alpha).toFloat(), marshal.interpolatedY(alpha).toFloat(),
                    relay.interpolatedX(alpha).toFloat(), relay.interpolatedY(alpha).toFloat(), 3f)
            }
        }
        for (enemy in simulation.enemies) drawEnemy(enemy, simulation, alpha)
        simulation.echo.nextShot(simulation.state == GameState.PREPARING)?.let { shot ->
            ring(shot.fromX.toFloat(), shot.fromY.toFloat(), 30f, 1.5f, ECHO, 0.7f)
            ring(shot.toX.toFloat(), shot.toY.toFloat(), 8f, 1.5f, ECHO, 0.65f)
        }
        if (simulation.state == GameState.PREPARING && simulation.inactivePath != null) {
            for (shot in simulation.echo.recordedImpacts()) {
                val x = if (shot.type == TowerType.INFERNO) shot.fromX else shot.toX
                val y = if (shot.type == TowerType.INFERNO) shot.fromY else shot.toY
                shapes.color = ECHO
                diamond(x.toFloat(), y.toFloat(), 3f)
                for (jump in shot.arcJumps) diamond(jump.x.toFloat(), jump.y.toFloat(), 3f)
            }
        }
        val recordedTowerId = simulation.echo.recordingTowerId ?: simulation.echo.armedTowerId
        simulation.towers.firstOrNull { it.id == recordedTowerId }?.let { tower ->
            ring(tower.x.toFloat(), tower.y.toFloat(), 33f, 2f, ECHO)
        }
        drawShots(simulation)
        for (enemy in simulation.enemies) drawEnemyDefenses(enemy, alpha)
        if (buildType != null && cursorX != null && cursorY != null) {
            drawPreview(simulation, buildType, cursorX.toFloat(), cursorY.toFloat())
        }
        shapes.end()
    }

    private fun drawBoard(simulation: GameSimulation) {
        val palette = when (simulation.battleMap) {
            BattleMap.CORRIDOR -> DEFENSE_PALETTE
            BattleMap.SWITCHBACK -> RELAY_PALETTE
            BattleMap.CROSSWIND -> CROSSWIND_PALETTE
            BattleMap.TWIN_LOCKS -> LOCKS_PALETTE
        }
        shapes.color = palette.background
        shapes.rect(0f, 0f, WIDTH, HEIGHT)
        shapes.color = palette.grid
        for (x in 0..900 step 30) shapes.rect(x.toFloat(), 0f, 1f, HEIGHT)
        for (y in 0..560 step 30) shapes.rect(0f, y.toFloat(), WIDTH, 1f)
        shapes.color = palette.majorGrid
        for (x in 60..870 step 120) {
            for (y in 60..540 step 120) {
                shapes.rect(x - 3f, y.toFloat(), 7f, 1f)
                shapes.rect(x.toFloat(), y - 3f, 1f, 7f)
            }
        }
        if (simulation.battleMap == BattleMap.SWITCHBACK) drawRelayTrim()
        simulation.inactivePath?.let { alternate ->
            drawPath(alternate, 48f, palette.majorGrid)
            drawPath(alternate, 42f, palette.grid)
        }
        drawPath(simulation.path, 48f, palette.laneEdge)
        drawPath(simulation.path, 42f, palette.lane)
        drawPath(simulation.path, 26f, palette.laneCenter)
        when (simulation.bossWindowWave) {
            Bastion.WAVE -> drawBossWindows(simulation.path, Bastion.windows)
            Marshal.WAVE -> drawBossWindows(simulation.path, Marshal.windows)
        }
        shapes.color = palette.laneMark
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

    /** Cosmetic edge markings distinguish the relay map without creating fake build obstacles. */
    private fun drawRelayTrim() {
        shapes.color = RELAY_TRIM
        shapes.rect(10f, HEIGHT - 10f, WIDTH - 20f, 2f)
        shapes.rect(10f, 8f, WIDTH - 20f, 2f)
        for (x in 640..860 step 18) {
            shapes.rectLine(x.toFloat(), 514f, x + 7f, 526f, 2f)
        }
        shapes.color = RELAY_PALETTE.majorGrid
        shapes.rectLine(630f, 504f, 874f, 504f, 1f)
        shapes.rectLine(874f, 504f, 874f, 535f, 1f)
    }

    private fun drawBossWindows(path: List<Waypoint>, windows: List<ClosedFloatingPointRange<Double>>) {
        var offset = 0.0
        shapes.color = BOSS_EXPOSED
        for ((from, to) in path.zipWithNext()) {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = sqrt(dx * dx + dy * dy)
            if (length == 0.0) continue
            for (window in windows) {
                val start = maxOf(0.0, window.start - offset)
                val end = minOf(length, window.endInclusive - offset)
                if (end <= start) continue
                val ux = dx / length
                val uy = dy / length
                for (side in -1..1 step 2) {
                    val x = from.x - uy * 16 * side
                    val y = from.y + ux * 16 * side
                    shapes.rectLine((x + start * ux).toFloat(), (y + start * uy).toFloat(),
                        (x + end * ux).toFloat(), (y + end * uy).toFloat(), 2.5f)
                }
            }
            offset += length
        }
    }

    private fun drawPath(path: List<Waypoint>, width: Float, color: Color) {
        shapes.color = color
        path.forEach { circle(it.x.toFloat(), it.y.toFloat(), width / 2f) }
        for (index in 0 until path.lastIndex) {
            val start = path[index]
            val end = path[index + 1]
            shapes.rectLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), width)
        }
    }

    private fun drawGate(x: Float, y: Float, color: Color, core: Boolean, tick: Long) {
        shapes.color = SHADOW
        circle(x + 2f, y - 3f, 28f)
        shapes.color = PANEL
        circle(x, y, 27f)
        ring(x, y, 26f, 2f, color)
        shapes.color = color
        if (core) {
            hexagon(x, y, 13f)
            shapes.color = DEFENSE_PALETTE.background
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
        circle(x, y, range)
        ring(x, y, range, 1.3f, color, 0.55f)
    }

    private fun drawTower(tower: Tower, simulation: GameSimulation, selected: Boolean) {
        val x = tower.x.toFloat()
        val y = tower.y.toFloat()
        val color = towerColor(tower.type)
        shapes.color = SHADOW
        circle(x + 2f, y - 4f, 23f)
        shapes.color = BORDER
        hexagon(x, y, 22f)
        shapes.color = PANEL
        hexagon(x, y, 18f)
        ring(x, y, 15f, 1.5f, color, 0.5f)
        if (selected) ring(x, y, 26f, 1.5f, CYAN)
        // The aiming preview follows the same selectable priority as the resolved combat hit.
        val target = tower.selectTarget(simulation.enemies)
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
                circle(x, y, 9f)
                shapes.rectLine(x, y, x + dx * 23f, y + dy * 23f, 6f)
                shapes.color = PANEL
                circle(x, y, 4f)
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
            TowerType.RAIL -> {
                hexagon(x, y, 9f)
                for (side in listOf(-1f, 1f)) {
                    val ox = -dy * 4f * side
                    val oy = dx * 4f * side
                    shapes.rectLine(x + ox, y + oy, x + ox + dx * 28f, y + oy + dy * 28f, 3f)
                }
            }
            TowerType.ARC -> {
                for (index in 0..2) {
                    val a = angle + index * TWO_PI / 3f
                    val px = x + cos(a) * 11f
                    val py = y + sin(a) * 11f
                    shapes.rectLine(x, y, px, py, 3f)
                    circle(px, py, 5f)
                }
            }
            TowerType.VENOM -> {
                circle(x, y, 11f)
                shapes.rectLine(x, y, x + dx * 22f, y + dy * 22f, 4f)
                shapes.color = PANEL
                circle(x, y, 6f)
                shapes.color = color
                diamond(x, y, 3f)
            }
            TowerType.INFERNO -> {
                ring(x, y, 12f, 4f, color)
                shapes.color = color
                for (index in 0..2) {
                    val a = angle + index * TWO_PI / 3f
                    shapes.rectLine(x + cos(a) * 10f, y + sin(a) * 10f,
                        x + cos(a) * 20f, y + sin(a) * 20f, 5f)
                }
            }
            TowerType.TREMOR -> {
                ring(x, y, 15f, 3f, color)
                hexagon(x, y, 9f)
                shapes.color = PANEL
                diamond(x, y, 6f)
                shapes.color = color
                shapes.rectLine(x, y, x + dx * 21f, y + dy * 21f, 6f)
            }
            TowerType.SUNDER -> {
                hexagon(x, y, 10f)
                for (side in listOf(-1f, 1f)) {
                    val px = x - dy * side * 11f
                    val py = y + dx * side * 11f
                    shapes.rectLine(x, y, px + dx * 11f, py + dy * 11f, 4f)
                    shapes.rectLine(px + dx * 11f, py + dy * 11f, x - dy * side * 5f + dx * 21f,
                        y + dx * side * 5f + dy * 21f, 4f)
                }
                shapes.color = PANEL
                diamond(x, y, 4f)
            }
            TowerType.BEACON -> {
                diamond(x, y, 12f)
                ring(x, y, 16f, 2f, color)
                shapes.color = PANEL
                diamond(x, y, 7f)
                shapes.color = color
                shapes.rectLine(x, y - 4f, x, y + 4f, 2f)
                shapes.rectLine(x - 4f, y, x + 4f, y, 2f)
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
        if (enemy.isPoisoned) ring(x, y, 16f, 1.5f, towerColor(TowerType.VENOM), 0.8f)
        if (enemy.markedSeconds > 0.0) {
            shapes.color = towerColor(TowerType.BEACON)
            shapes.triangle(x - 4f, y - 19f, x + 4f, y - 19f, x, y - 13f)
        }
        val radius = if (enemy.isMarshal) 24f else if (enemy.isBoss) 20f else if (enemy.isCommander) 16f else when (enemy.type) {
            EnemyType.ARMORED, EnemyType.PLATED, EnemyType.VANGUARD -> 13f
            EnemyType.FAST -> 9f
            else -> 11f
        }
        val baseColor = if (enemy.isBoss) {
            if (enemy.bossExposed) BOSS_EXPOSED else protectedBossColor(enemy)
        } else if (enemy.isCommander) COMMAND_LINK else when (enemy.type) {
            EnemyType.ARMORED -> ORANGE
            EnemyType.FAST -> MAGENTA
            EnemyType.NORMAL -> CORAL
            EnemyType.PLATED -> PLATE_COLOR
            EnemyType.SHIELDED -> SHIELD
            EnemyType.VANGUARD -> ARMOR
            EnemyType.REGENERATING -> REGENERATION
        }
        // A brief white tint is derived from the bounded hit history, with no event ownership here.
        val color = hitColor.set(baseColor).lerp(Color.WHITE, recentHitStrength(enemy, simulation) * 0.85f)
        shapes.color = SHADOW
        circle(x + 2f, y - 3f, radius + 2f)
        shapes.color = color
        when (enemy.type) {
            EnemyType.ARMORED -> {
                hexagon(x, y, radius)
                shapes.color = PANEL
                hexagon(x, y, radius - 4f)
                shapes.color = color
                if (enemy.isBoss) {
                    diamond(x, y, 10f)
                    if (!enemy.bossExposed) ring(x, y, radius + 3f, 2f, protectedBossColor(enemy))
                } else shapes.rect(x - 4f, y - 4f, 8f, 8f)
            }
            EnemyType.FAST -> drawRunner(x, y, enemy, simulation)
            EnemyType.NORMAL -> {
                diamond(x, y, radius)
                shapes.color = PANEL
                diamond(x, y, 4f)
            }
            EnemyType.PLATED -> {
                shapes.rect(x - 10f, y - 10f, 20f, 20f)
                shapes.color = PANEL
                shapes.rect(x - 6f, y - 6f, 12f, 12f)
                shapes.color = color
                shapes.rectLine(x - 5f, y - 4f, x + 5f, y + 4f, 2f)
            }
            EnemyType.VANGUARD -> {
                hexagon(x, y, radius)
                shapes.color = PANEL
                diamond(x, y, 9f)
                shapes.color = color
                shapes.rectLine(x - 4f, y, x + 4f, y, 3f)
                if (enemy.shield > 0.0) ring(x, y, radius + 4f, 2f, SHIELD)
            }
            EnemyType.SHIELDED -> {
                circle(x, y, radius)
                shapes.color = PANEL
                diamond(x, y, 7f)
                if (enemy.shield > 0.0) ring(x, y, radius + 4f, 2f, SHIELD)
            }
            EnemyType.REGENERATING -> {
                circle(x, y, radius)
                shapes.color = PANEL
                shapes.rect(x - 2f, y - 7f, 4f, 14f)
                shapes.rect(x - 7f, y - 2f, 14f, 4f)
            }
        }
    }

    private fun drawEnemyDefenses(enemy: Enemy, alpha: Double) {
        if (!enemy.isAlive || enemy.hasReachedGoal) return
        val x = enemy.interpolatedX(alpha).toFloat()
        val y = enemy.interpolatedY(alpha).toFloat()
        val health = (enemy.health / enemy.maxHealth).toFloat().coerceIn(0f, 1f)
        val healthColor = if (health < 0.3f) CORAL else HEALTH
        if (enemy.isBoss) {
            val phaseColor = if (enemy.bossExposed) BOSS_EXPOSED else protectedBossColor(enemy)
            val barY = if (enemy.isMarshal) 31f else 27f
            drawDefenseMeter(x - 15f, y + barY, 5f, health, healthColor, phaseColor)
        } else drawDefenseMeter(x - 15f, y + 19f, 4f, health, healthColor, BORDER)

        val hasShield = enemy.maxShield > 0.0
        if (hasShield) {
            val shield = (enemy.shield / enemy.maxShield).toFloat().coerceIn(0f, 1f)
            drawDefenseMeter(x - 15f, y + 26f, 3f, shield, SHIELD, SHIELD_TRACK_EDGE)
        }
        if (enemy.type.damageMultiplier < 1.0) {
            // Fixed plates signify passive resistance. Only health and shield have a fill level.
            val armorY = y + if (hasShield) 33f else 26f
            shapes.color = DEFENSE_TRACK
            shapes.rect(x - 10f, armorY - 1f, 20f, 5f)
            shapes.color = ARMOR
            for (segment in -1..1) {
                shapes.rect(x + segment * 7f - 2f, armorY, 4f, 3f)
            }
        }
        if (enemy.isRegenerating) {
            shapes.color = DEFENSE_TRACK
            shapes.rect(x + 17f, y + 16f, 10f, 10f)
            shapes.color = REGENERATION
            shapes.rect(x + 21f, y + 17f, 2f, 8f)
            shapes.rect(x + 18f, y + 20f, 8f, 2f)
        }
        if (enemy.isElite) drawEliteBrackets(x, y)
        if (enemy.isMarshalRelay) {
            shapes.color = MARSHAL_LINK
            diamond(x, y - 22f, 5f)
        }
        if (enemy.isCommander) {
            shapes.color = COMMAND_LINK
            diamond(x, y - 22f, 5f)
        } else if (enemy.protectingCommander != null) {
            shapes.color = COMMAND_LINK
            shapes.rect(x - 6f, y - 18f, 12f, 2f)
        }
    }

    private fun protectedBossColor(enemy: Enemy) = if (enemy.isMarshal) MARSHAL_LINK else BOSS_PROTECTED

    private fun drawEliteBrackets(x: Float, y: Float) {
        shapes.color = ELITE
        for (side in -1..1 step 2) {
            val outer = x + side * 24f
            val inner = x + side * 19f
            shapes.rectLine(inner, y - 11f, outer, y - 6f, 2.5f)
            shapes.rectLine(outer, y - 6f, outer, y + 6f, 2.5f)
            shapes.rectLine(outer, y + 6f, inner, y + 11f, 2.5f)
        }
    }

    private fun drawDefenseMeter(x: Float, y: Float, height: Float, fraction: Float, fill: Color, edge: Color) {
        shapes.color = edge
        shapes.rect(x - 1f, y - 1f, 32f, height + 2f)
        shapes.color = DEFENSE_TRACK
        shapes.rect(x, y, 30f, height)
        if (fraction > 0f) {
            shapes.color = fill
            shapes.rect(x, y, 30f * fraction, height)
        }
    }

    private fun recentHitStrength(enemy: Enemy, simulation: GameSimulation): Float {
        var strength = 0f
        for (shot in simulation.shots) {
            val age = simulation.tick - shot.tick
            if (age !in 0L until HIT_FLASH_TICKS) continue
            if (enemy.id in shot.hitEnemyIds) {
                strength = maxOf(strength, 1f - age.toFloat() / HIT_FLASH_TICKS)
            }
        }
        return strength
    }

    private fun drawRunner(x: Float, y: Float, enemy: Enemy, simulation: GameSimulation) {
        var dx = enemy.x - enemy.previousX
        var dy = enemy.y - enemy.previousY
        if (dx * dx + dy * dy < 0.000001) {
            val path = simulation.path
            dx = path[1].x - path[0].x
            dy = path[1].y - path[0].y
        }
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.000001)
        val ux = (dx / length).toFloat()
        val uy = (dy / length).toFloat()
        shapes.triangle(x - ux * 10f + uy * 8f, y - uy * 10f - ux * 8f,
            x - ux * 2f, y - uy * 2f,
            x - ux * 10f - uy * 8f, y - uy * 10f + ux * 8f)
        shapes.triangle(x - ux * 2f + uy * 8f, y - uy * 2f - ux * 8f,
            x + ux * 10f, y + uy * 10f,
            x - ux * 2f - uy * 8f, y - uy * 2f + ux * 8f)
    }

    private fun drawShots(simulation: GameSimulation) {
        // The simulation retains only twelve ticks of shots. This bounds cost and freezes on pause.
        for (shot in simulation.shots) {
            val age = (simulation.tick - shot.tick).toFloat().coerceAtLeast(0f) / GameSimulation.SHOT_LIFETIME_TICKS.toFloat()
            if (age >= 1f) continue
            val color = if (shot.isEcho) ECHO else towerColor(shot.type)
            val fromX = shot.fromX.toFloat()
            val fromY = shot.fromY.toFloat()
            val toX = shot.toX.toFloat()
            val toY = shot.toY.toFloat()
            val opacity = 1f - age
            if (shot.type == TowerType.INFERNO) {
                ring(fromX, fromY, shot.areaRadius.toFloat() * (0.6f + age * 0.4f),
                    3f, color, opacity * 0.85f)
                continue
            }
            shapes.setColor(color.r, color.g, color.b, opacity * 0.18f)
            shapes.rectLine(fromX, fromY, toX, toY, if (shot.type == TowerType.CANNON) 9f else 5f)
            shapes.setColor(color.r, color.g, color.b, opacity)
            shapes.rectLine(fromX, fromY, toX, toY, if (shot.type == TowerType.CANNON) 3f else 1.6f)
            circle(fromX, fromY, 4f * opacity)
            var jumpX = toX
            var jumpY = toY
            for (jump in shot.arcJumps) {
                shapes.rectLine(jumpX, jumpY, jump.x.toFloat(), jump.y.toFloat(), 2.5f)
                jumpX = jump.x.toFloat()
                jumpY = jump.y.toFloat()
            }
            val radius = when (shot.type) {
                TowerType.CANNON -> 8f + age * (shot.cannonSplashRadius.toFloat() - 8f)
                TowerType.FROST -> 5f + age * 17f
                TowerType.RAIL -> 4f + age * 13f
                TowerType.BEACON -> 8f + age * 10f
                TowerType.SUNDER -> 6f + age * 13f
                TowerType.TREMOR -> 6f + age * (shot.areaRadius.toFloat() - 6f)
                else -> 3f + age * 9f
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
        drawRange(x, y, simulation.research.rangeFor(type).toFloat(), color)
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
        var font = fonts.get(0.9f)
        font.color = MUTED
        val gridName = when (simulation.battleMap) {
            BattleMap.CORRIDOR -> "DEFENSE GRID"
            BattleMap.SWITCHBACK -> "RELAY GRID"
            BattleMap.CROSSWIND -> "CROSSWIND RIDGE"
            BattleMap.TWIN_LOCKS -> "FLOOD CONTROL GRID"
        }
        font.draw(batch, "BRE / $gridName", 32f, 490f)
        font = fonts.get(0.75f)
        font.color = DIM
        val sector = (simulation.battleMap.ordinal + 1).toString().padStart(2, '0')
        font.draw(batch, "SECTOR $sector    /    ${simulation.battleMap.title.uppercase(Locale.ROOT)}", 32f, 470f)
        font.draw(batch, "BUILD OFF THE ROUTE  /  PROTECT THE CORE", 32f, 28f)
        simulation.path.firstOrNull()?.let {
            font.color = CORAL
            val labelY = if (it.y > 350.0) it.y.toFloat() - 42f else it.y.toFloat() + 49f
            font.draw(batch, "ENTRY", it.x.toFloat() - 18f, labelY)
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

    /** Target a 0.2-pixel chord error with bounded geometry cost. */
    private fun curveSegments(radius: Float): Int {
        val pixelRadius = (radius * pixelsPerUnit).coerceAtLeast(1f)
        return ceil(kotlin.math.PI / acos((1.0 - 0.2 / pixelRadius).coerceIn(-1.0, 1.0)))
            .toInt().coerceIn(32, 256)
    }

    private fun circle(x: Float, y: Float, radius: Float) {
        shapes.circle(x, y, radius, curveSegments(radius))
    }

    private fun ring(x: Float, y: Float, radius: Float, width: Float, color: Color, alpha: Float = 1f) {
        shapes.setColor(color.r, color.g, color.b, alpha)
        val count = curveSegments(radius)
        val inner = (radius - width / 2f).coerceAtLeast(0f)
        val outer = radius + width / 2f
        // A closed triangle strip avoids gaps and dark overlapping caps between line segments.
        for (index in 0 until count) {
            val a = index * TWO_PI / count
            val b = (index + 1) * TWO_PI / count
            val ax = cos(a)
            val ay = sin(a)
            val bx = cos(b)
            val by = sin(b)
            shapes.triangle(x + ax * inner, y + ay * inner, x + ax * outer, y + ay * outer,
                x + bx * outer, y + by * outer)
            shapes.triangle(x + ax * inner, y + ay * inner, x + bx * outer, y + by * outer,
                x + bx * inner, y + by * inner)
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

    override fun dispose() {
        fallbackBuffer?.dispose()
        batch.dispose()
        shapes.dispose()
    }

    private data class BoardPalette(
        val background: Color, val grid: Color, val majorGrid: Color,
        val laneEdge: Color, val lane: Color, val laneCenter: Color, val laneMark: Color,
    )

    private companion object {
        const val WIDTH = 900f
        const val HEIGHT = 560f
        const val HIT_FLASH_TICKS = 7L
        const val TWO_PI = 6.2831855f
        const val PI_THIRD = 1.0471976f
        val PANEL = Color.valueOf("151e2e")
        val BORDER = Color.valueOf("304257")
        val DEFENSE_PALETTE = BoardPalette(
            Color.valueOf("0b101c"), Color.valueOf("111a29"), Color.valueOf("223347"),
            Color.valueOf("34485b"), Color.valueOf("202e40"), Color.valueOf("26364a"), Color.valueOf("4a627a"),
        )
        val RELAY_PALETTE = BoardPalette(
            Color.valueOf("171711"), Color.valueOf("202219"), Color.valueOf("373b27"),
            Color.valueOf("6e633e"), Color.valueOf("3b3929"), Color.valueOf("48432e"), Color.valueOf("93875e"),
        )
        val CROSSWIND_PALETTE = BoardPalette(
            Color.valueOf("101923"), Color.valueOf("182837"), Color.valueOf("294253"),
            Color.valueOf("476678"), Color.valueOf("293d4d"), Color.valueOf("314a5a"), Color.valueOf("71939d"),
        )
        val LOCKS_PALETTE = BoardPalette(
            Color.valueOf("171326"), Color.valueOf("201b32"), Color.valueOf("3e325b"),
            Color.valueOf("6d5d91"), Color.valueOf("38304f"), Color.valueOf("473c63"), Color.valueOf("9278bd"),
        )
        val RELAY_TRIM = Color.valueOf("6b5b32")
        val SHADOW = Color(0.005f, 0.009f, 0.02f, 0.75f)
        val CYAN = Color.valueOf("59d8ea")
        val ORANGE = Color.valueOf("ffba6b")
        val ICE = Color.valueOf("a8bfff")
        val CORAL = Color.valueOf("ff7d83")
        val MAGENTA = Color.valueOf("e28cdb")
        val ELITE = Color.valueOf("ffe08a")
        val MARSHAL_LINK = Color.valueOf("6bcfff")
        val COMMAND_LINK = Color.valueOf("75eeb9")
        val BOSS_EXPOSED = Color.valueOf("ffcb6e")
        val BOSS_PROTECTED = Color.valueOf("e5a0fc")
        val DEFENSE_TRACK = Color.valueOf("080d17")
        val SHIELD_TRACK_EDGE = Color.valueOf("3c7383")
        val MUTED = Color.valueOf("9aafc6")
        val DIM = Color.valueOf("5e768f")
    }
}

private val towerColors = mapOf(
    TowerType.RAPID to Color.valueOf("59d8ea"),
    TowerType.CANNON to Color.valueOf("ffba6b"),
    TowerType.FROST to Color.valueOf("a8bfff"),
    TowerType.RAIL to Color.valueOf("f8eb91"),
    TowerType.ARC to Color.valueOf("b899ff"),
    TowerType.VENOM to Color.valueOf("89de81"),
    TowerType.INFERNO to Color.valueOf("ff775c"),
    TowerType.BEACON to Color.valueOf("f69bd6"),
    TowerType.SUNDER to Color.valueOf("6df7c6"),
    TowerType.TREMOR to Color.valueOf("f3a775"),
)

internal fun towerColor(type: TowerType): Color = towerColors.getValue(type)

private val ECHO = Color.valueOf("c9bbff")
private val PLATE_COLOR = Color.valueOf("c5d0db")
