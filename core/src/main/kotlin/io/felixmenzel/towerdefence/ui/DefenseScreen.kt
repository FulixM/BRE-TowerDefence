package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import io.felixmenzel.towerdefence.rendering.WorldRenderer
import io.felixmenzel.towerdefence.simulation.ActionResult
import io.felixmenzel.towerdefence.simulation.FixedStepLoop
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import ktx.app.KtxScreen
import kotlin.math.roundToInt

/** Owns presentation and input; all game rules remain in GameSimulation. */
class DefenseScreen : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(WIDTH, HEIGHT, camera)
    private val renderer = WorldRenderer()
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont()
    private val glyph = GlyphLayout()
    private val worldProjection = Matrix4()
    private val pointer = Vector2()
    private val simulation = GameSimulation()
    private val loop = FixedStepLoop(simulation::update)
    private var selectedTowerId: Long? = null
    private var buildType: TowerType? = null
    private var paused = false
    private var doubleSpeed = false
    private var restartConfirmation = false
    private var message = "Choose a tower, place it beside the route, then start wave 1."
    private var messageError = false
    private var messageTime = 0f
    private val buttons = mutableListOf<Button>()
    private val labels = mutableListOf<Label>()
    private var modalButtonStart = 0

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (button == Input.Buttons.RIGHT) {
                buildType = null
                selectedTowerId = null
                return true
            }
            if (button != Input.Buttons.LEFT) return false
            val point = uiPoint(screenX, screenY) ?: return false
            val eligible = if (isModal()) buttons.drop(modalButtonStart) else buttons
            eligible.lastOrNull { it.bounds.contains(point) }?.let {
                if (it.enabled) it.action()
                return true
            }
            if (isModal()) return true
            if (point.x in 0f..BOARD_WIDTH && point.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT) {
                clickBoard(point.x.toDouble(), (point.y - BOARD_Y).toDouble())
                return true
            }
            return false
        }

        override fun keyDown(keycode: Int): Boolean {
            if (restartConfirmation) {
                when (keycode) {
                    Input.Keys.R, Input.Keys.ENTER -> restart()
                    Input.Keys.ESCAPE -> restartConfirmation = false
                }
                return true
            }
            if (isFinished()) {
                if (keycode == Input.Keys.R || keycode == Input.Keys.ENTER) restart()
                return true
            }
            when (keycode) {
                Input.Keys.P -> togglePause()
                Input.Keys.ESCAPE -> {
                    if (buildType != null || selectedTowerId != null) {
                        buildType = null
                        selectedTowerId = null
                    } else togglePause()
                }
                Input.Keys.R -> restartConfirmation = true
                Input.Keys.SPACE -> {
                    if (paused) togglePause()
                    else if (simulation.state == GameState.PREPARING) report(simulation.startWave())
                    else togglePause()
                }
                Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> if (!paused) choose(TowerType.RAPID)
                Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> if (!paused) choose(TowerType.CANNON)
                Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> if (!paused) choose(TowerType.FROST)
                Input.Keys.U -> if (!paused) selectedTower()?.let { report(simulation.upgradeTower(it.id)) }
                Input.Keys.F -> if (!paused) doubleSpeed = !doubleSpeed
                else -> return false
            }
            return true
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = input
        Gdx.graphics.setTitle("BRE Tower Defence")
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    override fun hide() {
        if (Gdx.input.inputProcessor === input) Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        messageTime = (messageTime - delta).coerceAtLeast(0f)
        if (!isModal()) {
            // Advancing separately preserves the loop's long-frame guard at both speeds.
            loop.advance(delta.toDouble())
            if (doubleSpeed) loop.advance(delta.toDouble())
        }
        viewport.apply()
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val cursor = uiPoint(Gdx.input.x, Gdx.input.y)
        val onBoard = cursor != null && cursor.x in 0f..BOARD_WIDTH &&
            cursor.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT && !isModal()
        worldProjection.set(camera.combined).translate(0f, BOARD_Y, 0f)
        renderer.render(
            simulation, loop.interpolationAlpha, worldProjection, selectedTower(), buildType,
            if (onBoard) cursor!!.x.toDouble() else null,
            if (onBoard) (cursor!!.y - BOARD_Y).toDouble() else null,
        )
        drawInterface(cursor)
    }

    private fun clickBoard(x: Double, y: Double) {
        val existing = simulation.towers.firstOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) <= 24.0 * 24.0 }
        if (existing != null) {
            selectedTowerId = existing.id
            buildType = null
            return
        }
        val type = buildType
        if (type != null) {
            val result = simulation.buildTower(type, x, y)
            report(result)
            if (result.success) {
                selectedTowerId = result.towerId
                buildType = null
            }
        } else selectedTowerId = null
    }

    private fun choose(type: TowerType) {
        buildType = type
        selectedTowerId = null
        notify("${type.title}: click free ground to build. Right-click or Esc cancels.")
    }

    private fun selectedTower(): Tower? = simulation.towers.firstOrNull { it.id == selectedTowerId }

    private fun report(result: ActionResult) = notify(result.message, !result.success)

    private fun notify(text: String, error: Boolean = false) {
        message = text
        messageError = error
        messageTime = 5f
    }

    private fun restart() {
        simulation.restart()
        loop.reset()
        paused = false
        doubleSpeed = false
        restartConfirmation = false
        selectedTowerId = null
        buildType = null
        notify("New defence. Build your first towers, then press Space to start.")
    }

    private fun togglePause() {
        paused = !paused
        loop.reset()
    }

    override fun pause() {
        if (!isFinished()) paused = true
        loop.reset()
    }

    override fun resume() = loop.reset()

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    private fun uiPoint(screenX: Int, screenY: Int): Vector2? {
        val bottomY = Gdx.graphics.height - screenY
        if (screenX < viewport.screenX || screenX >= viewport.screenX + viewport.screenWidth ||
            bottomY < viewport.screenY || bottomY >= viewport.screenY + viewport.screenHeight) return null
        return viewport.unproject(pointer.set(screenX.toFloat(), screenY.toFloat()))
    }

    private fun isFinished() = simulation.state == GameState.WON || simulation.state == GameState.LOST
    private fun isModal() = paused || restartConfirmation || isFinished()

    private fun drawInterface(cursor: Vector2?) {
        buttons.clear()
        labels.clear()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        rect(0f, 640f, WIDTH, 80f, PANEL)
        rect(0f, 0f, WIDTH, 80f, PANEL)
        rect(900f, 80f, 300f, 560f, PANEL)
        rect(0f, 638f, WIDTH, 2f, BORDER)
        rect(900f, 80f, 2f, 560f, BORDER)
        text("BRE / TOWER DEFENCE", 24f, 692f, 1.45f, WHITE)
        text("HOLD THE LINE", 25f, 665f, 0.8f, MUTED)
        stat("GOLD", simulation.gold.toString(), 386f, GOLD)
        stat("BASE", "${simulation.lives} / ${GameSimulation.START_LIVES}", 523f, if (simulation.lives <= 5) RED else CYAN)
        stat("WAVE", "${simulation.wave} / ${simulation.totalWaves}", 678f, WHITE)
        button("speed", if (doubleSpeed) "2x [F]" else "1x [F]", 835f, 660f, 90f, 40f) { doubleSpeed = !doubleSpeed }
        button("pause", "Pause [P]", 938f, 660f, 112f, 40f) { togglePause() }
        button("restart", "Restart [R]", 1063f, 660f, 113f, 40f) { restartConfirmation = true }

        text("BUILD TOWERS", 922f, 614f, 1f, WHITE)
        text("Select a role. Place beside the route.", 922f, 593f, 0.8f, MUTED)
        towerCard(TowerType.RAPID, "1", 508f, "Fast, reliable single-target fire", CYAN)
        towerCard(TowerType.CANNON, "2", 425f, "Splash damage against groups", ORANGE)
        towerCard(TowerType.FROST, "3", 342f, "Slows enemies for other towers", ICE)

        val tower = selectedTower()
        when {
            buildType != null -> buildPanel(buildType!!)
            tower != null -> selectedPanel(tower)
            else -> {
                text("YOUR DEFENCE", 922f, 313f, 0.95f, WHITE)
                text("${simulation.towers.size} towers deployed", 922f, 285f, 0.95f, CYAN)
                text("Click a tower to inspect, upgrade\nor sell it. Combine tower roles\nat bends to cover more of the route.",
                    922f, 257f, 0.9f, MUTED, 254f)
            }
        }

        val preparing = simulation.state == GameState.PREPARING
        text(if (preparing) "NEXT / WAVE ${simulation.wave + 1}" else "WAVE ${simulation.wave} / INCOMING", 24f, 58f, 0.85f, CYAN)
        text(wavePreview(), 24f, 35f, 0.87f, WHITE, 500f)
        val nextLabel = if (preparing) "Start wave ${simulation.wave + 1} [Space]" else "${simulation.enemies.size} on route  /  ${simulation.remainingToSpawn} incoming"
        button("wave", nextLabel, 565f, 20f, 310f, 43f, preparing, CYAN) { report(simulation.startWave()) }
        text("1-3 Build   U Upgrade   Esc Cancel", 922f, 59f, 0.78f, MUTED)
        text("Space Wave/Pause   P Pause   F Speed", 922f, 36f, 0.75f, MUTED)

        // Feedback sits inside the board's unused upper margin, without hiding the route.
        val placementReason = if (buildType != null && cursor != null && !isModal() &&
            cursor.x in 0f..BOARD_WIDTH && cursor.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT) {
            simulation.placementError(cursor.x.toDouble(), (cursor.y - BOARD_Y).toDouble())
                ?: if (simulation.gold < buildType!!.cost) "Not enough gold for ${buildType!!.title}." else null
        } else null
        val hint = placementReason ?: if (messageTime > 0f || simulation.wave == 0) message else "Protect the exit. Select a tower to inspect its reach and upgrade options."
        rect(18f, 590f, 864f, 32f, BACKGROUND)
        rect(18f, 590f, 3f, 32f, if (placementReason != null || messageError && messageTime > 0f) RED else CYAN)
        text(hint, 31f, 611f, 0.9f, if (placementReason != null || messageError && messageTime > 0f) RED else WHITE, 840f)

        modalButtonStart = buttons.size
        shapes.end()
        drawText(0, modalButtonStart)
        if (isModal()) {
            // Modal shapes must cover both underlying artwork and its text.
            labels.clear()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawModal()
            shapes.end()
            drawText(modalButtonStart, buttons.size)
        }
    }

    private fun drawText(firstButton: Int, lastButton: Int) {
        batch.begin()
        for (label in labels) {
            font.data.setScale(label.scale)
            font.color = label.color
            if (label.width > 0f) font.draw(batch, label.text, label.x, label.y, label.width, Align.left, true)
            else font.draw(batch, label.text, label.x, label.y)
        }
        for (index in firstButton until lastButton) {
            val button = buttons[index]
            font.data.setScale(button.scale)
            font.color = if (button.enabled) WHITE else MUTED
            glyph.setText(font, button.label)
            font.draw(batch, button.label, button.bounds.x + (button.bounds.width - glyph.width) / 2f,
                button.bounds.y + (button.bounds.height + glyph.height) / 2f)
        }
        font.data.setScale(1f)
        batch.end()
    }

    private fun wavePreview(): String {
        if (simulation.wave == simulation.totalWaves && simulation.state != GameState.PREPARING) return "Final assault. Hold until the route is clear."
        return if (simulation.state == GameState.PREPARING) {
            "${simulation.nextWaveDescription}  /  Clear bonus +${simulation.waveReward + 5}g"
        } else simulation.waveDescription(simulation.wave)
    }

    private fun buildPanel(type: TowerType) {
        text("PLACING / ${type.title.uppercase()}", 922f, 313f, 0.95f, towerColor(type))
        text("Cost ${type.cost}  /  Range ${type.baseRange.roundToInt()}", 922f, 285f, 0.9f, WHITE)
        text("${type.baseDamage.roundToInt()} damage / ${formatRate(type.baseAttackIntervalTicks)} shots per sec", 922f, 262f, 0.86f, MUTED)
        text("Green = clear ground\nRed = cannot build here\nKeep the route clear.", 922f, 229f, 0.88f, MUTED, 250f)
        button("cancel", "Cancel placement [Esc]", 922f, 116f, 254f, 40f) { buildType = null }
    }

    private fun selectedPanel(tower: Tower) {
        text("${tower.type.title.uppercase()} / LEVEL ${tower.level}", 922f, 313f, 0.95f, towerColor(tower.type))
        text("Damage ${tower.damage.roundToInt()}   Range ${tower.range.roundToInt()}", 922f, 285f, 0.88f, WHITE)
        text("${formatRate(tower.attackIntervalTicks)} shots per sec", 922f, 262f, 0.86f, MUTED)
        val cost = tower.upgradeCost
        text(if (cost == null) "Maximum level reached" else "Upgrade: more damage and reach", 922f, 237f, 0.83f, MUTED)
        button("upgrade", if (cost == null) "Fully upgraded" else "Upgrade [U]  /  $cost gold", 922f, 179f, 254f, 42f,
            cost != null && simulation.gold >= cost, towerColor(tower.type)) { report(simulation.upgradeTower(tower.id)) }
        button("sell", "Sell  /  +${tower.sellValue} gold", 922f, 123f, 254f, 40f, accent = ORANGE) {
            report(simulation.sellTower(tower.id))
            selectedTowerId = null
        }
    }

    private fun drawModal() {
        // Opaque backing keeps the text readable on any battlefield state.
        rect(140f, 205f, 720f, 315f, Color.valueOf("080d17"))
        rect(140f, 516f, 720f, 4f, if (simulation.state == GameState.LOST) RED else CYAN)
        val title: String
        val subtitle: String
        when {
            restartConfirmation -> {
                title = "RESTART DEFENCE?"
                subtitle = "Your current defence will be cleared. Start a fresh mission?"
            }
            simulation.state == GameState.WON -> {
                title = "LINE SECURED"
                subtitle = "All ${simulation.totalWaves} waves defeated. Your base survived with ${simulation.lives} lives."
            }
            simulation.state == GameState.LOST -> {
                title = "BASE OVERRUN"
                subtitle = "You reached wave ${simulation.wave}. Rebuild, mix your towers and try again."
            }
            else -> {
                title = "DEFENCE PAUSED"
                subtitle = "Take your time. The battlefield is waiting for your command."
            }
        }
        text(title, 182f, 473f, 1.85f, WHITE)
        text(subtitle, 182f, 421f, 1f, MUTED, 636f)
        if (restartConfirmation) {
            button("confirm", "Restart [Enter / R]", 182f, 267f, 300f, 52f, accent = ORANGE) { restart() }
            button("keep", "Keep playing [Esc]", 502f, 267f, 300f, 52f) { restartConfirmation = false }
        } else if (isFinished()) {
            text("Strategy: cover bends, upgrade damage, and slow fast enemies.", 182f, 364f, 0.95f, CYAN, 636f)
            button("new", "New defence [Enter / R]", 182f, 267f, 620f, 52f, accent = CYAN) { restart() }
        } else {
            button("resume", "Resume [P / Space]", 182f, 267f, 300f, 52f, accent = CYAN) { togglePause() }
            button("restart-modal", "Restart mission", 502f, 267f, 300f, 52f) { restartConfirmation = true }
        }
    }

    private fun towerCard(type: TowerType, key: String, y: Float, description: String, accent: Color) {
        val chosen = buildType == type
        rect(922f, y, 254f, 73f, if (chosen) Color.valueOf("243247") else BACKGROUND)
        rect(922f, y, 3f, 73f, accent)
        text(description, 934f, y + 19f, 0.76f, MUTED)
        button("build-$key", "[$key] ${type.title}   ${type.cost}g", 931f, y + 29f, 236f, 37f,
            enabled = true, accent = if (chosen) accent else BORDER, scale = 0.95f) { choose(type) }
    }

    private fun stat(label: String, value: String, x: Float, color: Color) {
        text(label, x, 701f, 0.75f, MUTED)
        text(value, x, 679f, 1.35f, color)
    }

    private fun button(id: String, title: String, x: Float, y: Float, width: Float, height: Float,
        enabled: Boolean = true, accent: Color = BORDER, scale: Float = 0.9f, action: () -> Unit) {
        val bounds = Rectangle(x, y, width, height)
        val hover = uiPoint(Gdx.input.x, Gdx.input.y)?.let(bounds::contains) == true
        rect(x, y, width, height, if (enabled && hover) HOVER else BUTTON)
        rect(x, y, width, 2f, if (enabled) accent else BORDER)
        buttons += Button(id, title, bounds, enabled, scale, action)
    }

    private fun rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        shapes.color = color
        shapes.rect(x, y, width, height)
    }

    private fun text(value: String, x: Float, y: Float, scale: Float = 1f, color: Color = WHITE, width: Float = 0f) {
        labels += Label(value, x, y, scale, color, width)
    }

    private fun towerColor(type: TowerType) = when (type) {
        TowerType.RAPID -> CYAN
        TowerType.CANNON -> ORANGE
        TowerType.FROST -> ICE
    }

    private fun formatRate(ticks: Long): String = String.format(java.util.Locale.ROOT, "%.1f", 60.0 / ticks)

    override fun dispose() {
        hide()
        renderer.dispose()
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }

    private data class Label(val text: String, val x: Float, val y: Float, val scale: Float, val color: Color, val width: Float)
    private data class Button(val id: String, val label: String, val bounds: Rectangle, val enabled: Boolean,
        val scale: Float, val action: () -> Unit)

    companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 720f
        const val BOARD_WIDTH = 900f
        const val BOARD_HEIGHT = 560f
        const val BOARD_Y = 80f
        private val BACKGROUND = Color.valueOf("0b101c")
        private val PANEL = Color.valueOf("151e2e")
        private val BORDER = Color.valueOf("2e4058")
        private val BUTTON = Color.valueOf("1c2a3e")
        private val HOVER = Color.valueOf("2c435e")
        private val WHITE = Color.valueOf("e6edf7")
        private val MUTED = Color.valueOf("9aaec6")
        private val CYAN = Color.valueOf("59d8ea")
        private val GOLD = Color.valueOf("ffd278")
        private val RED = Color.valueOf("ff8791")
        private val ORANGE = Color.valueOf("ffba6b")
        private val ICE = Color.valueOf("a8bfff")
    }
}
