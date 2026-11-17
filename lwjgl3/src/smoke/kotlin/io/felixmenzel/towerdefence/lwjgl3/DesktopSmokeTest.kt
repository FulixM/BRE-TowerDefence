package io.felixmenzel.towerdefence.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import io.felixmenzel.towerdefence.rendering.ScreenFonts
import io.felixmenzel.towerdefence.Main
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.TowerType
import io.felixmenzel.towerdefence.ui.DefenseScreen
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Real LWJGL/OpenGL smoke test. Run through `lwjgl3:smokeTest`, under xvfb on CI.
 * Input is sent through the installed production InputProcessor. Test-only reflection reads
 * simulation/viewport state, avoiding debug accessors in the game's public API.
 */
object DesktopSmokeTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration().apply {
            setTitle("BRE Tower Defence / Desktop smoke")
            setWindowedMode(1200, 720)
            useVsync(false)
            setForegroundFPS(60)
            disableAudio(true)
        }
        Lwjgl3Application(SmokeApplication(), config)
    }

    private class SmokeApplication : ApplicationAdapter() {
        private val game = Main()
        private lateinit var screen: DefenseScreen
        private lateinit var simulation: GameSimulation
        private lateinit var viewport: FitViewport
        private var ready = false
        private var frame = 0
        private var frozenTick = 0L
        private var expectedRefund = 0
        private var screenshotWritten = false
        private var normalCapHeight = 0f
        private var normalNativeCapHeight = 0f

        override fun create() {
            game.create()
            screen = game.getScreen<DefenseScreen>()
            simulation = privateField(screen, "simulation")
            viewport = privateField(screen, "viewport")
            ready = true
        }

        override fun resize(width: Int, height: Int) {
            if (ready) game.resize(width, height)
        }

        override fun render() {
            game.render()
            frame++
            check(frame <= 400) { "Smoke test exceeded frame budget" }
            when (frame) {
                1 -> {
                    check(simulation.gold == GameSimulation.START_GOLD)
                    check(simulation.state == GameState.PREPARING)
                    key(Input.Keys.NUM_1)
                    click(150f, 160f + DefenseScreen.BOARD_Y) // Invalid: on the route.
                    check(simulation.towers.isEmpty()) { "Invalid route placement created a tower" }
                    check(simulation.gold == GameSimulation.START_GOLD) { "Invalid placement spent gold" }
                }
                2 -> {
                    click(220f, 240f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.size == 1) { "Valid mouse placement did not build" }
                    check(simulation.gold == GameSimulation.START_GOLD - TowerType.RAPID.cost)
                }
                3 -> {
                    val tower = simulation.towers.single()
                    val cost = checkNotNull(tower.upgradeCost)
                    val before = simulation.gold
                    key(Input.Keys.U)
                    check(tower.level == 2) { "Upgrade shortcut did not target selected tower" }
                    check(simulation.gold == before - cost)
                    expectedRefund = tower.sellValue
                }
                4 -> {
                    val before = simulation.gold
                    click(1040f, 142f) // Visible Sell button in the selected-tower inspector.
                    check(simulation.towers.isEmpty()) { "Sell button did not remove tower" }
                    check(simulation.gold == before + expectedRefund)
                }
                5 -> {
                    frozenTick = simulation.tick
                    key(Input.Keys.R)
                }
                8 -> {
                    check(simulation.tick == frozenTick) { "Restart confirmation did not freeze simulation" }
                    key(Input.Keys.ESCAPE)
                    check(simulation.towers.isEmpty())
                }
                9 -> {
                    key(Input.Keys.R)
                    key(Input.Keys.ENTER)
                    checkFreshMission()
                    key(Input.Keys.NUM_1)
                    click(220f, 240f + DefenseScreen.BOARD_Y)
                    key(Input.Keys.NUM_2)
                    click(450f, 250f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.size == 2)
                    key(Input.Keys.SPACE)
                    check(simulation.state == GameState.RUNNING && simulation.wave == 1)
                }
                30 -> {
                    check(simulation.tick > 0L && simulation.enemies.isNotEmpty())
                    key(Input.Keys.P)
                    frozenTick = simulation.tick
                }
                50 -> {
                    check(simulation.tick == frozenTick) { "Pause advanced simulation ticks" }
                    val before = simulation.gold
                    click(1040f, 142f) // Underlying inspector cannot receive modal clicks.
                    check(simulation.gold == before) { "Paused HUD accepted a game command" }
                    key(Input.Keys.P)
                }
                70 -> {
                    check(simulation.tick > frozenTick) { "Resume did not restart simulation" }
                    key(Input.Keys.R)
                    frozenTick = simulation.tick
                }
                75 -> {
                    check(simulation.tick == frozenTick)
                    key(Input.Keys.ENTER)
                    checkFreshMission()
                    check(Gdx.graphics.setWindowedMode(1200, 900)) { "Desktop resize failed" }
                }
                85 -> {
                    check(Gdx.graphics.width == 1200 && Gdx.graphics.height == 900)
                    check(viewport.screenY > 0) { "Tall window should have letterbox bars" }
                    key(Input.Keys.NUM_1)
                    val before = simulation.gold
                    val consumed = Gdx.input.inputProcessor.touchDown(600, 1, 0, Input.Buttons.LEFT)
                    check(!consumed) { "Letterbox click was consumed as a game action" }
                    check(simulation.gold == before && simulation.towers.isEmpty())
                    click(100f, 80f + DefenseScreen.BOARD_Y)
                    val tower = simulation.towers.single()
                    check(abs(tower.x - 100.0) <= 2.0 && abs(tower.y - 80.0) <= 2.0) {
                        "Resized click mapped to incorrect world position: ${tower.x}, ${tower.y}"
                    }
                    key(Input.Keys.NUM_2)
                    click(450f, 250f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.size == 2)
                    key(Input.Keys.SPACE)
                }
                95 -> {
                    val fonts = privateField<ScreenFonts>(screen, "fonts")
                    normalCapHeight = fonts.get().capHeight
                    normalNativeCapHeight = normalCapHeight / fonts.get().data.scaleY
                    check(Gdx.graphics.setWindowedMode(1800, 1080))
                }
                110 -> {
                    val fonts = privateField<ScreenFonts>(screen, "fonts")
                    val font = fonts.get()
                    val density = viewport.screenWidth.toFloat() / DefenseScreen.WIDTH *
                        Gdx.graphics.backBufferWidth / Gdx.graphics.width
                    check(density >= 1.4f) { "Large-window text was not rendered at higher density" }
                    check(abs(font.data.scaleY - 1f / density) < 0.001f)
                    check(font.capHeight / font.data.scaleY > normalNativeCapHeight * 1.3f) {
                        "Text atlas was enlarged instead of regenerated at native resolution"
                    }
                    check(abs(font.capHeight - normalCapHeight) < 1.5f) { "Logical text size changed on resize" }
                    writeScreenshot("-scaled")
                }
                120 -> check(Gdx.graphics.setWindowedMode(1200, 900))
                300 -> {
                    check(simulation.state == GameState.RUNNING)
                    check(simulation.enemies.isNotEmpty())
                    writeScreenshot()
                    screenshotWritten = true
                    println("Desktop smoke passed: placement, upgrade, sale, waves, pause, restart, resize and letterboxing; screenshot saved.")
                    Gdx.app.exit()
                }
            }
        }

        private fun key(code: Int) {
            check(Gdx.input.inputProcessor.keyDown(code)) { "Shortcut $code was not handled" }
            Gdx.input.inputProcessor.keyUp(code)
        }

        /** Project logical HUD coordinates through the production viewport into native input space. */
        private fun click(x: Float, y: Float) {
            val point = viewport.project(Vector2(x, y))
            val screenX = point.x.roundToInt()
            val screenY = (Gdx.graphics.height - point.y).roundToInt()
            check(Gdx.input.inputProcessor.touchDown(screenX, screenY, 0, Input.Buttons.LEFT)) {
                "Mouse input at $x,$y was not handled"
            }
            Gdx.input.inputProcessor.touchUp(screenX, screenY, 0, Input.Buttons.LEFT)
        }

        private fun checkFreshMission() {
            check(simulation.state == GameState.PREPARING && simulation.wave == 0)
            check(simulation.gold == GameSimulation.START_GOLD && simulation.lives == GameSimulation.START_LIVES)
            check(simulation.towers.isEmpty() && simulation.enemies.isEmpty() && simulation.shots.isEmpty())
        }

        private fun writeScreenshot(suffix: String = "") {
            val path = System.getProperty("bre.smoke.screenshot", "build/reports/desktop-smoke.png")
            val target = if (suffix.isEmpty()) path else path.removeSuffix(".png") + suffix + ".png"
            val file = Gdx.files.absolute(java.io.File(target).absolutePath)
            file.parent().mkdirs()
            val pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            try {
                PixmapIO.writePNG(file, pixels, -1, true)
                check(file.length() > 1000) { "Screenshot was empty" }
            } finally {
                pixels.dispose()
            }
        }

        override fun dispose() {
            game.dispose()
            check(screenshotWritten) { "Smoke application closed before completing all checks" }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> privateField(instance: Any, name: String): T =
            instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as T
    }
}
