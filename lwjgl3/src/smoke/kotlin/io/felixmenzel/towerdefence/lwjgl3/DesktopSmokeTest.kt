package io.felixmenzel.towerdefence.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import io.felixmenzel.towerdefence.rendering.ScreenFonts
import io.felixmenzel.towerdefence.rendering.WorldRenderer
import io.felixmenzel.towerdefence.Main
import io.felixmenzel.towerdefence.simulation.Enemy
import io.felixmenzel.towerdefence.simulation.EnemyType
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.Waypoint
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Difficulty
import io.felixmenzel.towerdefence.simulation.TargetPriority
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
            setHdpiMode(com.badlogic.gdx.graphics.glutils.HdpiMode.Logical)
            setSamples(4)
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
        private var missionSetupStage = 0
        private var missionSetupFrames = 0
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
            simulation = privateField(screen, "simulation")
            if (missionSetupStage < 12) {
                checkMissionPicker()
                return
            }
            frame++
            check(frame <= 400) { "Smoke test exceeded frame budget" }
            when (frame) {
                1 -> {
                    val samples = BufferUtils.newIntBuffer(1)
                    Gdx.gl.glGetIntegerv(GL20.GL_SAMPLES, samples)
                    if (samples.get(0) >= 4) {
                        println("Geometry antialiasing active: ${samples.get(0)} samples")
                    } else {
                        val renderer = privateField<Any>(screen, "renderer")
                        val target = privateField<FrameBuffer?>(renderer, "fallbackBuffer")
                        val nativeWidth = viewport.screenWidth.toFloat() / DefenseScreen.WIDTH *
                            Gdx.graphics.backBufferWidth / Gdx.graphics.width * DefenseScreen.BOARD_WIDTH
                        check(target != null && target.width >= nativeWidth * 1.5f) {
                            "Geometry lacks both multisampling and supersampling"
                        }
                        println("Geometry antialiasing active: supersampled framebuffer ${target.width}x${target.height}")
                    }
                    check(simulation.gold == GameSimulation.START_GOLD)
                    check(simulation.state == GameState.PREPARING)
                    key(Input.Keys.NUM_8)
                    check(privateField<TowerType>(screen, "buildType") == TowerType.BEACON)
                    click(150f, 160f + DefenseScreen.BOARD_Y) // Invalid: on the route.
                    check(simulation.towers.isEmpty()) { "Invalid route placement created a tower" }
                    check(simulation.gold == GameSimulation.START_GOLD) { "Invalid placement spent gold" }
                    key(Input.Keys.NUM_1)
                }
                2 -> {
                    click(220f, 240f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.size == 1) { "Valid mouse placement did not build" }
                    check(simulation.gold == GameSimulation.START_GOLD - TowerType.RAPID.cost)
                    val tower = simulation.towers.single()
                    check(tower.targetPriority == TargetPriority.FIRST)
                    key(Input.Keys.T)
                    check(tower.targetPriority == TargetPriority.STRONGEST) { "Target shortcut did not change selected tower" }
                    key(Input.Keys.T)
                    check(tower.targetPriority == TargetPriority.NEAREST)
                    key(Input.Keys.T)
                    check(tower.targetPriority == TargetPriority.FIRST)
                }
                3 -> {
                    val tower = simulation.towers.single()
                    val cost = checkNotNull(tower.upgradeCost)
                    val before = simulation.gold
                    val preview = checkNotNull(tower.nextUpgrade)
                    key(Input.Keys.U)
                    check(tower.range == preview.range && tower.damage == preview.damage &&
                        tower.attackIntervalTicks == preview.attackIntervalTicks) { "Upgrade differs from preview" }
                    check(tower.level == 2) { "Upgrade shortcut did not target selected tower" }
                    check(simulation.gold == before - cost)
                    expectedRefund = tower.sellValue
                    click(1110f, 345f)
                    val echo = privateField<Any>(simulation, "echo")
                    check(privateField<Long?>(echo, "armedTowerId") == tower.id)
                    key(Input.Keys.E)
                    check(privateField<Long?>(echo, "armedTowerId") == null)
                    key(Input.Keys.E)
                    check(privateField<Long?>(echo, "armedTowerId") == tower.id)
                }
                4 -> {
                    val before = simulation.gold
                    click(1040f, 142f) // Visible Sell button in the selected-tower inspector.
                    check(simulation.towers.isEmpty()) { "Sell button did not remove tower" }
                    check(simulation.gold == before + expectedRefund)
                    check(privateField<Long?>(privateField<Any>(simulation, "echo"), "armedTowerId") == null)
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
                    key(Input.Keys.E)
                    key(Input.Keys.SPACE)
                    val echo = privateField<Any>(simulation, "echo")
                    check(privateField<Long?>(echo, "recordingTowerId") == simulation.towers.last().id)
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
                55 -> {
                    key(Input.Keys.G)
                    check(privateField<Boolean>(screen, "enemyGuide"))
                    frozenTick = simulation.tick
                }
                60 -> {
                    check(simulation.tick == frozenTick) { "Enemy guide did not pause the wave" }
                    writeScreenshot("-enemy-guide")
                    key(Input.Keys.ESCAPE)
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
                    click(1110f, 520f) // Rail is the fourth catalogue entry.
                    click(450f, 250f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.map { it.type } == listOf(TowerType.RAPID, TowerType.RAIL))
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
                    checkEnemyDefenses()
                    screenshotWritten = true
                    println("Desktop smoke passed: mission selection, difficulty, target priorities, placement, upgrade, sale, waves, pause, restart, resize, letterboxing, and enemy defenses; screenshots saved.")
                    Gdx.app.exit()
                }
            }
        }

        /** Exercise mission selection and abandonment confirmation before the original regression path. */
        private fun checkMissionPicker() {
            check(++missionSetupFrames <= 40) { "Mission picker smoke exceeded frame budget" }
            when (missionSetupStage++) {
                0 -> {
                    click(308f, 665f) // Mission button on the permanent HUD.
                    frozenTick = simulation.tick
                }
                1 -> {
                    check(simulation.tick == frozenTick) { "Mission setup advanced simulation" }
                    click(500f, 461f) // Switchback.
                    click(704f, 275f) // Veteran.
                }
                2 -> {
                    writeScreenshot("-mission-options")
                    click(332f, 184f) // Launch mission.
                    simulation = privateField(screen, "simulation")
                    check(simulation.battleMap == BattleMap.SWITCHBACK)
                    check(simulation.difficulty == Difficulty.VETERAN)
                    check(simulation.gold == Difficulty.VETERAN.startingGold)
                    check(simulation.lives == Difficulty.VETERAN.startingLives)
                    key(Input.Keys.SPACE)
                }
                3 -> {
                    check(simulation.state == GameState.RUNNING && simulation.wave == 1)
                    if (simulation.enemies.isEmpty()) {
                        missionSetupStage = 3 // A real frame can be shorter than one fixed simulation step.
                        return
                    }
                    val enemy = simulation.enemies.single()
                    check(enemy.maxHealth == enemy.type.baseHealth * Difficulty.VETERAN.healthMultiplier)
                    writeScreenshot("-switchback")
                    key(Input.Keys.M)
                    frozenTick = simulation.tick
                }
                4 -> {
                    check(simulation.tick == frozenTick)
                    click(280f, 461f) // Original Corridor.
                    click(492f, 275f) // Normal.
                }
                5 -> {
                    click(332f, 184f)
                    // A running defence must not be discarded before explicit confirmation.
                    check(simulation.battleMap == BattleMap.SWITCHBACK)
                    check(simulation.wave == 1)
                }
                6 -> {
                    check(simulation.tick == frozenTick)
                    click(332f, 293f) // Confirm replacing the running mission.
                    simulation = privateField(screen, "simulation")
                    check(simulation.battleMap == BattleMap.CORRIDOR)
                    check(simulation.difficulty == Difficulty.NORMAL)
                    checkFreshMission()
                }
                7 -> key(Input.Keys.M)
                8 -> {
                    click(718f, 461f) // Crosswind Pass.
                    click(332f, 184f)
                    simulation = privateField(screen, "simulation")
                    check(simulation.battleMap == BattleMap.CROSSWIND)
                    checkFreshMission()
                }
                9 -> {
                    key(Input.Keys.NUM_9)
                    check(privateField<TowerType>(screen, "buildType") == TowerType.SUNDER)
                    key(Input.Keys.ESCAPE)
                    click(985f, 399f) // Sunder catalogue card.
                    click(194f, 205f + DefenseScreen.BOARD_Y)
                    check(simulation.towers.single().type == TowerType.SUNDER)
                    check(simulation.gold == GameSimulation.START_GOLD - TowerType.SUNDER.cost)
                }
                10 -> {
                    writeScreenshot("-crosswind")
                    click(1040f, 142f) // Sell before returning to Corridor.
                    key(Input.Keys.M)
                }
                11 -> {
                    click(280f, 461f)
                    click(332f, 184f)
                    simulation = privateField(screen, "simulation")
                    check(simulation.battleMap == BattleMap.CORRIDOR)
                    checkFreshMission()
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

        private fun checkEnemyDefenses() {
            val fixture = GameSimulation()
            val enemies = privateField<MutableList<Enemy>>(fixture, "mutableEnemies")
            fun addEnemy(x: Double, type: EnemyType): Enemy {
                val route = listOf(Waypoint(x, 280.0), Waypoint(x + 100.0, 280.0))
                return Enemy(enemies.size + 1L, route, 0.0, 100.0, type).also(enemies::add)
            }

            addEnemy(160.0, EnemyType.SHIELDED).takeDamage(15.0)
            addEnemy(280.0, EnemyType.SHIELDED).takeDamage(55.0)
            addEnemy(400.0, EnemyType.PLATED).takeDamage(50.0)
            addEnemy(520.0, EnemyType.REGENERATING).apply {
                takeDamage(50.0)
                update(2.0)
            }
            addEnemy(640.0, EnemyType.NORMAL).takeDamage(80.0)
            addEnemy(760.0, EnemyType.VANGUARD)

            val renderer = privateField<WorldRenderer>(screen, "renderer")
            val projection = Matrix4(viewport.camera.combined).translate(0f, DefenseScreen.BOARD_Y, 0f)
            renderer.render(fixture, 1.0, projection)
            val pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            try {
                fun expectColor(label: String, x: Float, y: Float, rgb: Int) {
                    val point = viewport.project(Vector2(x, y + DefenseScreen.BOARD_Y))
                    val px = (point.x * pixels.width / Gdx.graphics.width).toInt()
                    val py = (point.y * pixels.height / Gdx.graphics.height).toInt()
                    val actual = pixels.getPixel(px, py) ushr 8
                    for (shift in listOf(16, 8, 0)) {
                        val channel = (actual shr shift) and 255
                        val expected = (rgb shr shift) and 255
                        check(abs(channel - expected) <= 12) {
                            "$label expected RGB ${rgb.toString(16)}, got ${actual.toString(16)}"
                        }
                    }
                }

                expectColor("full health after shield absorbs a shot", 150f, 301f, 0x7ce7a2)
                expectColor("remaining shield", 150f, 307f, 0x56dffa)
                expectColor("spent part of shield", 170f, 307f, 0x080d17)
                expectColor("broken shield track", 270f, 307f, 0x080d17)
                expectColor("armor plate after taking damage", 400f, 307f, 0xffcb68)
                expectColor("gap between armor plates", 403f, 307f, 0x080d17)
                expectColor("active regeneration", 542f, 301f, 0xd5a1ff)
                expectColor("critical health", 628f, 301f, 0xff7d83)
                expectColor("Vanguard shield", 750f, 307f, 0x56dffa)
                expectColor("Vanguard armor above its shield", 760f, 314f, 0xffcb68)
            } finally {
                pixels.dispose()
            }
            writeScreenshot("-enemy-defenses")
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
