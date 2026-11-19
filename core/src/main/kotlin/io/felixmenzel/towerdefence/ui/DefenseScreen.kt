package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import io.felixmenzel.towerdefence.rendering.ScreenFonts
import io.felixmenzel.towerdefence.rendering.WorldRenderer
import io.felixmenzel.towerdefence.simulation.ActionResult
import io.felixmenzel.towerdefence.simulation.FieldProfile
import io.felixmenzel.towerdefence.simulation.FixedStepLoop
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.RemixChallenge
import io.felixmenzel.towerdefence.simulation.RemixRecords
import io.felixmenzel.towerdefence.simulation.TremorModule
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import io.felixmenzel.towerdefence.simulation.Waves
import io.felixmenzel.towerdefence.ui.HudPalette.BACKGROUND
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.ECHO
import io.felixmenzel.towerdefence.ui.HudPalette.GOLD
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.ORANGE
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.PANEL
import io.felixmenzel.towerdefence.ui.HudPalette.RED
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE
import ktx.app.KtxScreen
import kotlin.random.Random

/** Owns presentation and input; all game rules remain in GameSimulation. */
class DefenseScreen(
    private val profile: FieldProfile = FieldProfile(),
    private val records: RemixRecords = RemixRecords(),
) : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(WIDTH, HEIGHT, camera)
    private val fonts = ScreenFonts()
    private val renderer = WorldRenderer(fonts)
    private val hud = HudCanvas(fonts)
    private val worldProjection = Matrix4()
    private val pointer = Vector2()
    private var simulation = GameSimulation(fieldRank = profile.rank)
    private var resultXp: Int? = null
    private var profileSaveFailed = false
    private var newRemixBest = false
    private val loop = FixedStepLoop { simulation.update() }
    private var selectedTowerId: Long? = null
    private var buildType: TowerType? = null
    private var paused = false
    private var doubleSpeed = false
    private var restartConfirmation = false
    private var enemyGuide = false
    private var missionSetup = false
    private var missionLaunchConfirmation = false
    private var pendingMap = simulation.battleMap
    private var pendingDifficulty = simulation.difficulty
    private var pendingSeed = simulation.seed
    private var pendingModule = simulation.tremorModule
    private var setupFeedback = ""
    private var setupError = false
    private var message = "Choose a tower, place it beside the route, then start wave 1."
    private var messageError = false
    private var messageTime = 0f
    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (button == Input.Buttons.RIGHT) {
                buildType = null
                selectedTowerId = null
                return true
            }
            if (button != Input.Buttons.LEFT) return false
            val point = uiPoint(screenX, screenY) ?: return false
            if (hud.click(point, overlayOnly = isModal())) return true
            if (isModal()) return true
            if (point.x in 0f..BOARD_WIDTH && point.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT) {
                clickBoard(point.x.toDouble(), (point.y - BOARD_Y).toDouble())
                return true
            }
            return false
        }

        override fun keyDown(keycode: Int): Boolean {
            if (enemyGuide) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.G) closeEnemyGuide()
                return true
            }
            if (missionSetup) {
                when (keycode) {
                    Input.Keys.ENTER -> if (missionLaunchConfirmation) launchMission() else requestLaunch()
                    Input.Keys.ESCAPE -> {
                        if (missionLaunchConfirmation) missionLaunchConfirmation = false
                        else closeMissionSetup()
                    }
                }
                return true
            }
            if (keycode == Input.Keys.M) {
                openMissionSetup()
                return true
            }
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
            val tower = towerShortcut(keycode)
            if (tower != null) {
                if (!paused) choose(tower)
                return true
            }
            when (keycode) {
                Input.Keys.G -> enemyGuide = true
                Input.Keys.E -> if (!paused) {
                    val tower = selectedTower()
                    if (tower == null) notify("Select a tower to record its shots", true)
                    else report(simulation.selectEchoTower(tower.id))
                }
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
                Input.Keys.U -> if (!paused) selectedTower()?.let { report(simulation.upgradeTower(it.id)) }
                Input.Keys.T -> if (!paused) selectedTower()?.let { report(simulation.cycleTargetPriority(it.id)) }
                Input.Keys.F -> if (!paused) doubleSpeed = !doubleSpeed
                Input.Keys.V -> if (!paused && simulation.inactivePath != null) report(simulation.switchRoute())
                else -> return false
            }
            return true
        }
    }

    private fun towerShortcut(keycode: Int): TowerType? {
        val index = when (keycode) {
            Input.Keys.NUM_0, Input.Keys.NUMPAD_0 -> 9
            in Input.Keys.NUM_1..Input.Keys.NUM_9 -> keycode - Input.Keys.NUM_1
            in Input.Keys.NUMPAD_1..Input.Keys.NUMPAD_9 -> keycode - Input.Keys.NUMPAD_1
            else -> return null
        }
        return TowerType.entries.getOrNull(index)
    }

    private fun closeEnemyGuide() {
        enemyGuide = false
        loop.reset()
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
        if (isFinished() && resultXp == null) {
            resultXp = try {
                profile.award(simulation.state, simulation.wave)
            } catch (failure: RuntimeException) {
                Gdx.app.error("Profile", "Unable to save mission XP", failure)
                profileSaveFailed = true
                0
            }
            try {
                newRemixBest = records.submit(simulation)
            } catch (failure: RuntimeException) {
                Gdx.app.error("Profile", "Unable to save the Remix best", failure)
            }
        }
        viewport.apply()
        fonts.updateDensity(viewport.screenWidth.toFloat() / WIDTH *
            Gdx.graphics.backBufferWidth.toFloat() / Gdx.graphics.width)
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val cursor = uiPoint(Gdx.input.x, Gdx.input.y)
        val onBoard = cursor != null && cursor.x in 0f..BOARD_WIDTH &&
            cursor.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT && !isModal()
        worldProjection.set(camera.combined).translate(0f, BOARD_Y, 0f)
        renderer.render(
            simulation, loop.interpolationAlpha, worldProjection, selectedTower(), buildType,
            if (onBoard) cursor.x.toDouble() else null,
            if (onBoard) (cursor.y - BOARD_Y).toDouble() else null,
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
        if (!simulation.isUnlocked(type)) {
            notify("${type.title} unlocks at field rank ${type.requiredRank}.", true)
            return
        }
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
        if (simulation.fieldRank != profile.rank) {
            simulation = GameSimulation(
                simulation.battleMap, simulation.difficulty, simulation.seed, profile.rank, simulation.tremorModule,
            )
        } else simulation.restart()
        resultXp = null
        profileSaveFailed = false
        newRemixBest = false
        loop.reset()
        paused = false
        doubleSpeed = false
        restartConfirmation = false
        missionSetup = false
        missionLaunchConfirmation = false
        selectedTowerId = null
        buildType = null
        notify("New defence. Build your first towers, then press Space to start.")
    }

    private fun openMissionSetup() {
        pendingMap = simulation.battleMap
        pendingDifficulty = simulation.difficulty
        pendingSeed = simulation.seed
        pendingModule = simulation.tremorModule
        setupFeedback = ""
        setupError = false
        missionSetup = true
        missionLaunchConfirmation = false
        restartConfirmation = false
        loop.reset()
    }

    private fun closeMissionSetup() {
        missionSetup = false
        missionLaunchConfirmation = false
        loop.reset()
    }

    private fun requestLaunch() {
        if (!isFinished() && (simulation.wave > 0 || simulation.towers.isNotEmpty())) {
            missionLaunchConfirmation = true
        } else launchMission()
    }

    private fun newSeed(): Int {
        val next = Random.nextInt(100000, 1000000)
        return if (next == pendingSeed) 100000 + (next - 99999) % 900000 else next
    }

    private fun pendingChallenge() = pendingSeed?.let { RemixChallenge(pendingMap, pendingDifficulty, it) }

    private fun copyChallenge() {
        val challenge = pendingChallenge() ?: return
        Gdx.app.clipboard.contents = challenge.code
        setupFeedback = "Copied ${challenge.code}"
        setupError = false
    }

    private fun importChallenge() {
        val challenge = Gdx.app.clipboard.contents?.let(RemixChallenge::parse)
        if (challenge == null) {
            setupFeedback = "Clipboard does not contain a valid Remix code."
            setupError = true
            return
        }
        pendingMap = challenge.map
        pendingDifficulty = challenge.difficulty
        pendingSeed = challenge.seed
        setupFeedback = "Loaded ${challenge.code}"
        setupError = false
    }

    private fun cycleModule() {
        val modules = TremorModule.entries
        pendingModule = modules[(pendingModule.ordinal + 1) % modules.size]
    }

    private fun launchMission() {
        simulation = GameSimulation(pendingMap, pendingDifficulty, pendingSeed, profile.rank, pendingModule)
        restart()
        notify("${pendingMap.title} / ${pendingDifficulty.title}. Build towers, then start wave 1.")
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
    private fun isModal() = paused || restartConfirmation || missionSetup || enemyGuide || isFinished()

    private fun drawInterface(cursor: Vector2?) = with(hud) {
        beginFrame(camera.combined, cursor)
        rect(0f, 640f, WIDTH, 80f, PANEL)
        rect(0f, 0f, WIDTH, 80f, PANEL)
        rect(900f, 80f, 300f, 560f, PANEL)
        rect(0f, 638f, WIDTH, 2f, BORDER)
        rect(900f, 80f, 2f, 560f, BORDER)
        text("BRE / TOWER DEFENCE", 24f, 703f, 1.2f, WHITE)
        text("${simulation.battleMap.title.uppercase()} / ${simulation.difficulty.title.uppercase()}", 25f, 667f, 0.75f, MUTED)
        button("Mission [M]", 250f, 650f, 117f, 30f, scale = 0.8f) { openMissionSetup() }
        stat("GOLD", simulation.gold.toString(), 386f, GOLD)
        stat("BASE", "${simulation.lives} / ${simulation.difficulty.startingLives}", 523f, if (simulation.lives <= 5) RED else CYAN)
        stat("WAVE", "${simulation.wave} / ${simulation.totalWaves}", 678f, WHITE)
        button(if (doubleSpeed) "2x [F]" else "1x [F]", 835f, 660f, 90f, 40f) { doubleSpeed = !doubleSpeed }
        button("Pause [P]", 938f, 660f, 112f, 40f) { togglePause() }
        button("Restart [R]", 1063f, 660f, 113f, 40f) { restartConfirmation = true }

        drawTowerPanel(
            simulation, selectedTower(), buildType,
            choose = ::choose,
            openEnemyGuide = { enemyGuide = true },
            cancelPlacement = { buildType = null },
            report = ::report,
            sell = { tower ->
                report(simulation.sellTower(tower.id))
                selectedTowerId = null
            },
        )

        val preparing = simulation.state == GameState.PREPARING
        val briefedWave = if (preparing) simulation.wave + 1 else simulation.wave
        val waveTitle = Waves.name(briefedWave)?.let { " / ${it.uppercase()}" }.orEmpty()
        val waveHeading = if (preparing) "NEXT / WAVE $briefedWave" else "WAVE $briefedWave"
        text(waveHeading + waveTitle, 24f, 58f, 0.85f, CYAN)
        val echo = simulation.echo
        val echoStatus = when {
            preparing && echo.recordedCount > 0 -> "Echo ready / ${echo.recordedCount} shots"
            preparing && echo.armedTowerId != null -> "Echo recorder armed"
            echo.remainingCount > 0 -> "Echo / ${echo.remainingCount} shots left"
            echo.recordingTowerId != null -> "Recording / ${echo.recordedCount} shots"
            else -> null
        }
        echoStatus?.let { text(it, 335f, 58f, 0.82f, ECHO) }
        text(wavePreview(), 24f, 35f, 0.87f, WHITE, 500f)
        val nextLabel = if (preparing) "Start wave ${simulation.wave + 1} [Space]" else "${simulation.enemies.size} on route  /  ${simulation.remainingToSpawn} incoming"
        val hasSwitch = simulation.inactivePath != null
        val startWidth = if (hasSwitch) 188f else 310f
        button(nextLabel, 565f, 20f, startWidth, 43f, preparing, CYAN, scale = if (hasSwitch) 0.77f else 0.90f) {
            report(simulation.startWave())
        }
        if (hasSwitch) {
            val lock = if (simulation.useUpperRoute) "Upper" else "Lower"
            button("$lock lock [V]", 761f, 20f, 114f, 43f, preparing, CYAN, scale = 0.78f) {
                report(simulation.switchRoute())
            }
        }
        text("1-9 / 0 Build   U Upgrade   T Target", 922f, 59f, 0.78f, MUTED)
        text("Space Wave/Pause   P Pause   F Speed", 922f, 36f, 0.75f, MUTED)
        val seedLabel = simulation.seed?.let { "  /  Seed $it" }.orEmpty()
        text("Rank ${profile.rank}  /  ${profile.rankXp} XP$seedLabel", 922f, 15f, 0.72f, CYAN)

        // Feedback sits inside the board's unused upper margin, without hiding the route.
        val placementReason = if (buildType != null && cursor != null && !isModal() &&
            cursor.x in 0f..BOARD_WIDTH && cursor.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT) {
            simulation.placementError(cursor.x.toDouble(), (cursor.y - BOARD_Y).toDouble())
                ?: if (simulation.gold < buildType!!.cost) "Not enough gold for ${buildType!!.title}." else null
        } else null
        val hint = placementReason ?: if (messageTime > 0f || simulation.wave == 0) message else "Open the enemy guide [G] to check enemy abilities and counters."
        rect(18f, 590f, 864f, 32f, BACKGROUND)
        rect(18f, 590f, 3f, 32f, if (placementReason != null || messageError && messageTime > 0f) RED else CYAN)
        text(hint, 31f, 611f, 0.9f, if (placementReason != null || messageError && messageTime > 0f) RED else WHITE, 840f)

        endHud()
        if (isModal()) {
            beginOverlay()
            drawModal()
            endOverlay()
        }
    }

    private fun wavePreview(): String {
        return if (simulation.state == GameState.PREPARING) {
            "${simulation.nextWaveDescription}  /  Clear bonus +${simulation.waveReward + 5}g"
        } else simulation.waveDescription(simulation.wave)
    }

    private fun HudCanvas.drawModal() {
        if (enemyGuide) {
            drawEnemyGuide(::closeEnemyGuide)
            return
        }
        if (missionSetup && !missionLaunchConfirmation) {
            drawMissionSetup(
                pendingMap, pendingDifficulty, pendingSeed,
                pendingModule, profile.rank, pendingChallenge()?.let(records::best), setupFeedback, setupError,
                selectMap = { pendingMap = it },
                selectDifficulty = { pendingDifficulty = it },
                toggleRemix = { pendingSeed = if (pendingSeed == null) newSeed() else null },
                reroll = { pendingSeed = newSeed() },
                cycleModule = ::cycleModule,
                copyCode = ::copyChallenge,
                importCode = ::importChallenge,
                launch = ::requestLaunch,
                cancel = ::closeMissionSetup,
            )
            return
        }
        // Opaque backing keeps the text readable on any battlefield state.
        rect(140f, 205f, 720f, 315f, OVERLAY)
        rect(140f, 516f, 720f, 4f, if (simulation.state == GameState.LOST) RED else CYAN)
        val title: String
        val subtitle: String
        when {
            missionLaunchConfirmation -> {
                title = "REPLACE CURRENT DEFENCE?"
                subtitle = "Your current towers and progress will be cleared. Launch ${pendingMap.title} / ${pendingDifficulty.title}?"
            }
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
        if (missionLaunchConfirmation) {
            button("Launch new mission [Enter]", 182f, 267f, 300f, 52f, accent = ORANGE) { launchMission() }
            button("Back to setup [Esc]", 502f, 267f, 300f, 52f) { missionLaunchConfirmation = false }
        } else if (restartConfirmation) {
            button("Restart [Enter / R]", 182f, 267f, 300f, 52f, accent = ORANGE) { restart() }
            button("Keep playing [Esc]", 502f, 267f, 300f, 52f) { restartConfirmation = false }
        } else if (isFinished()) {
            text("${simulation.battleMap.title} / ${simulation.difficulty.title}   |   ${simulation.kills} defeated / ${simulation.leakedEnemies} escaped", 182f, 364f, 0.95f, CYAN, 636f)
            val xpLabel = if (profileSaveFailed) "Could not save XP" else "+${resultXp ?: 0} XP  /  Field rank ${profile.rank}"
            val unlocked = TowerType.entries.filter { it.requiredRank in (simulation.fieldRank + 1)..profile.rank }
            val unlocks = unlocked.map { it.title }.toMutableList()
            if (simulation.fieldRank < TremorModule.REQUIRED_RANK && profile.rank >= TremorModule.REQUIRED_RANK) {
                unlocks.add("Tremor modules")
            }
            val unlockLabel = unlocks.joinToString()
            text(if (unlockLabel.isEmpty()) xpLabel else "$xpLabel  /  Unlocked $unlockLabel",
                182f, 336f, 0.92f, GOLD, 636f)
            if (newRemixBest) text("New local Remix best", 182f, 252f, 0.87f, CYAN)
            button("Retry mission [Enter / R]", 182f, 267f, 300f, 52f, accent = CYAN) { restart() }
            button("Choose mission [M]", 502f, 267f, 300f, 52f) { openMissionSetup() }
        } else {
            button("Choose mission [M]", 182f, 337f, 620f, 40f) { openMissionSetup() }
            button("Resume [P / Space]", 182f, 267f, 300f, 52f, accent = CYAN) { togglePause() }
            button("Restart mission", 502f, 267f, 300f, 52f) { restartConfirmation = true }
        }
    }

    private fun HudCanvas.stat(label: String, value: String, x: Float, color: Color) {
        text(label, x, 701f, 0.75f, MUTED)
        text(value, x, 679f, 1.35f, color)
    }

    override fun dispose() {
        hide()
        renderer.dispose()
        hud.dispose()
        fonts.dispose()
    }

    companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 720f
        const val BOARD_WIDTH = 900f
        const val BOARD_HEIGHT = 560f
        const val BOARD_Y = 80f
    }
}
