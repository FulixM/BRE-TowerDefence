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
import io.felixmenzel.towerdefence.audio.CombatAudio
import io.felixmenzel.towerdefence.rendering.ScreenFonts
import io.felixmenzel.towerdefence.rendering.WorldRenderer
import io.felixmenzel.towerdefence.simulation.ActionResult
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.FieldProfile
import io.felixmenzel.towerdefence.simulation.FixedStepLoop
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.MissionSaves
import io.felixmenzel.towerdefence.simulation.RailModule
import io.felixmenzel.towerdefence.simulation.RemixChallenge
import io.felixmenzel.towerdefence.simulation.RemixRecords
import io.felixmenzel.towerdefence.simulation.ResearchLoadout
import io.felixmenzel.towerdefence.simulation.ResearchProgress
import io.felixmenzel.towerdefence.simulation.ResearchTrack
import io.felixmenzel.towerdefence.simulation.TremorModule
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import io.felixmenzel.towerdefence.simulation.MissionMode
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
    private val saves: MissionSaves = MissionSaves(),
    private val persistResearch: (ResearchProgress) -> Unit = {},
    private val persistResult: ((Int, Pair<String, String>?, Boolean, ResearchProgress) -> Unit)? = null,
    sfxEnabled: Boolean = true,
    private val persistSfx: (Boolean) -> Unit = {},
) : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(WIDTH, HEIGHT, camera)
    private val fonts = ScreenFonts()
    private val renderer = WorldRenderer(fonts)
    private val hud = HudCanvas(fonts)
    private val worldProjection = Matrix4()
    private val pointer = Vector2()
    private var simulation = GameSimulation(fieldRank = profile.rank, research = profile.research.loadout)
    private var startupMission = readSavedMission()
    private var hasCheckpoint = startupMission != null
    private var checkpointDirty = false
    private var checkpointError = false
    private var resultXp: Int? = null
    private var profileSaveFailed = false
    private var newRemixBest = false
    private var newRelayHunter = false
    private var gainedResearch = 0
    private val combatAudio = CombatAudio(sfxEnabled)
    private val onEnemyEvent = combatAudio::enemy
    private val loop = FixedStepLoop {
        simulation.update(onEnemyEvent)
        val shots = simulation.shots
        for (index in shots.lastIndex downTo 0) {
            val shot = shots[index]
            if (shot.tick != simulation.tick) break
            combatAudio.shot(shot)
        }
    }
    private var selectedTowerId: Long? = null
    private var buildType: TowerType? = null
    private var paused = false
    private var doubleSpeed = false
    private var restartConfirmation = false
    private var enemyGuide = false
    private var reportOpen = false
    private var missionSetup = false
    private var workshopOpen = false
    private var workshopMessage = ""
    private var missionLaunchConfirmation = false
    private var pendingMap = simulation.battleMap
    private var pendingDifficulty = simulation.difficulty
    private var pendingSeed = simulation.seed
    private var pendingElevatedTerrain = simulation.elevatedTerrain
    private var pendingGridPlacement = simulation.gridPlacement
    private var pendingMode = simulation.mode
    private var pendingModule = simulation.tremorModule
    private var pendingRailModule = simulation.railModule
    private var setupFeedback = ""
    private var setupError = false
    private var message = "Choose a tower, place it beside the route, then start wave 1."
    private var messageError = false
    private var messageTime = 0f
    private val shiftKeys = mutableSetOf<Int>()
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

        override fun keyUp(keycode: Int): Boolean = shiftKeys.remove(keycode)

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT) {
                shiftKeys.add(keycode)
                return true
            }
            if (keycode == Input.Keys.F8) {
                toggleSfx()
                return true
            }
            if (startupMission != null) {
                if (keycode == Input.Keys.ENTER) continueMission()
                return true
            }
            if (reportOpen) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.L) closeWaveReport()
                return true
            }
            if (enemyGuide) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.G) closeEnemyGuide()
                return true
            }
            if (workshopOpen) {
                if (keycode == Input.Keys.ESCAPE) workshopOpen = false
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
            if (keycode == Input.Keys.L) {
                openWaveReport()
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
                    else if (simulation.state == GameState.PREPARING) startWave()
                    else togglePause()
                }
                Input.Keys.Z -> if (!paused) undoLastBuild()
                Input.Keys.X -> if (!paused && simulation.overdriveEnabled && simulation.state == GameState.RUNNING) activateOverdrive()
                Input.Keys.U -> if (!paused) selectedTower()?.let { report(simulation.upgradeTower(it.id)) }
                Input.Keys.T -> if (!paused) selectedTower()?.let { report(simulation.cycleTargetPriority(it.id)) }
                Input.Keys.F -> if (!paused) doubleSpeed = !doubleSpeed
                Input.Keys.V -> if (!paused && simulation.inactivePath != null) report(simulation.switchRoute())
                Input.Keys.B -> if (!paused && simulation.canOfferElite) report(simulation.toggleElite())
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

    private fun openWaveReport() {
        reportOpen = true
        loop.reset()
    }

    private fun closeWaveReport() {
        reportOpen = false
        loop.reset()
    }

    private fun readSavedMission(): GameSimulation? = try {
        saves.load()?.let { GameSimulation.restore(it) }
    } catch (failure: RuntimeException) {
        Gdx.app.error("Mission", "Unable to load the saved mission", failure)
        null
    }

    private fun continueMission() {
        simulation = startupMission ?: return
        startupMission = null
        checkpointError = false
        loop.reset()
        notify("Defence restored before wave ${simulation.wave + 1}.")
    }

    private fun discardSave() {
        if (startupMission == null) return
        if (clearCheckpoint()) startupMission = null
    }

    private fun saveCheckpoint(force: Boolean = false) {
        if ((!force && !checkpointDirty) || simulation.state != GameState.PREPARING) return
        checkpointDirty = false
        val checkpoint = simulation.checkpoint()
        if (checkpoint == null) {
            clearCheckpoint()
            return
        }
        try {
            saves.save(checkpoint)
            hasCheckpoint = true
            checkpointError = false
        } catch (failure: RuntimeException) {
            Gdx.app.error("Mission", "Unable to save the defence", failure)
            checkpointError = true
            notify("Could not save the defence.", true)
        }
    }

    private fun clearCheckpoint(): Boolean {
        if (!hasCheckpoint) {
            checkpointError = false
            return true
        }
        return try {
            saves.clear()
            hasCheckpoint = false
            checkpointError = false
            true
        } catch (failure: RuntimeException) {
            Gdx.app.error("Mission", "Unable to clear the saved defence", failure)
            checkpointError = true
            notify("Could not clear the saved defence.", true)
            false
        }
    }

    private fun startWave() {
        saveCheckpoint(force = true)
        val result = simulation.startWave()
        if (!checkpointError || !result.success) report(result)
    }

    override fun show() {
        Gdx.input.inputProcessor = input
        Gdx.graphics.setTitle("BRE Tower Defence")
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    override fun hide() {
        combatAudio.stop()
        shiftKeys.clear()
        saveCheckpoint()
        if (Gdx.input.inputProcessor === input) Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        messageTime = (messageTime - delta).coerceAtLeast(0f)
        combatAudio.beginFrame(delta)
        val previousState = simulation.state
        if (!isModal()) {
            // Advancing separately preserves the loop's long-frame guard at both speeds.
            loop.advance(delta.toDouble())
            if (doubleSpeed) loop.advance(delta.toDouble())
        }
        if (previousState == GameState.RUNNING && simulation.state == GameState.PREPARING) {
            checkpointDirty = true
        }
        saveCheckpoint()
        if (isFinished() && resultXp == null) claimMissionResult()
        viewport.apply()
        fonts.updateDensity(viewport.screenWidth.toFloat() / WIDTH *
            Gdx.graphics.backBufferWidth.toFloat() / Gdx.graphics.width)
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val cursor = uiPoint(Gdx.input.x, Gdx.input.y)
        val onBoard = cursor != null && cursor.x in 0f..BOARD_WIDTH &&
            cursor.y in BOARD_Y..BOARD_Y + BOARD_HEIGHT && !isModal()
        val tower = selectedTower()
        val previewUpgrade = buildType == null && tower?.upgradeCost != null && !isModal() && isUpgradeHovered(cursor)
        worldProjection.set(camera.combined).translate(0f, BOARD_Y, 0f)
        renderer.render(
            simulation, loop.interpolationAlpha, worldProjection, tower, buildType,
            if (onBoard) cursor.x.toDouble() else null,
            if (onBoard) (cursor.y - BOARD_Y).toDouble() else null,
            previewUpgrade,
        )
        drawInterface(cursor, previewUpgrade)
    }

    private fun claimMissionResult() {
        try {
            val persist = persistResult
            val relayHunter = profile.relayHunterAfterMission(simulation)
            val research = profile.research.afterMission(simulation)
            if (persist == null) {
                check(clearCheckpoint())
                resultXp = profile.award(simulation.state, simulation.wave)
                newRemixBest = records.submit(simulation)
            } else {
                val total = profile.totalAfterMission(simulation.state, simulation.wave)
                val best = records.improvedRecord(simulation)
                persist(total, best, relayHunter, research)
                hasCheckpoint = false
                resultXp = profile.applyTotal(total)
                newRemixBest = best != null
            }
            newRelayHunter = profile.applyRelayHunterUnlock(relayHunter)
            gainedResearch = profile.applyResearch(research)
        } catch (failure: RuntimeException) {
            Gdx.app.error("Profile", "Unable to save the mission result", failure)
            profileSaveFailed = true
            resultXp = 0
        }
    }

    private fun clickBoard(x: Double, y: Double) {
        val existing = simulation.towerAt(x, y)
        if (existing != null && (buildType == null || !simulation.gridPlacement)) {
            selectedTowerId = existing.id
            buildType = null
            return
        }
        val type = buildType
        if (type != null) {
            val result = simulation.buildTower(type, x, y)
            report(result)
            if (result.success && !(simulation.gridPlacement && shiftKeys.isNotEmpty() && simulation.gold >= type.cost)) {
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

    private fun activateOverdrive() {
        val tower = selectedTower()
        if (tower == null) notify("Select a tower for Overdrive", true)
        else report(simulation.activateOverdrive(tower.id))
    }

    private fun undoLastBuild() {
        val result = simulation.undoLastBuild()
        if (result.success && selectedTowerId == result.towerId) selectedTowerId = null
        report(result)
    }

    private fun selectedTower(): Tower? = simulation.towers.firstOrNull { it.id == selectedTowerId }

    private fun report(result: ActionResult) {
        if (result.success && simulation.state == GameState.PREPARING) checkpointDirty = true
        notify(result.message, !result.success)
    }

    private fun notify(text: String, error: Boolean = false) {
        message = text
        messageError = error
        messageTime = 5f
    }

    private fun restart() {
        if (!clearCheckpoint()) return
        val research = if (ResearchLoadout.appliesTo(simulation.mode, simulation.seed)) profile.research.loadout
            else ResearchLoadout.EMPTY
        val elevated = simulation.elevatedTerrain || simulation.mode == MissionMode.CAMPAIGN && simulation.seed == null
        if (simulation.fieldRank != profile.rank || simulation.research != research || simulation.elevatedTerrain != elevated) {
            simulation = GameSimulation(
                simulation.battleMap, simulation.difficulty, simulation.seed, profile.rank, simulation.tremorModule,
                simulation.mode, simulation.railModule, research,
                elevated, simulation.gridPlacement,
            )
        } else simulation.restart()
        resetMissionInterface()
        notify("New defence. Build your first towers, then press Space to start.")
    }

    private fun resetMissionInterface() {
        checkpointDirty = false
        checkpointError = false
        resultXp = null
        profileSaveFailed = false
        newRemixBest = false
        newRelayHunter = false
        gainedResearch = 0
        workshopOpen = false
        loop.reset()
        paused = false
        doubleSpeed = false
        restartConfirmation = false
        reportOpen = false
        missionSetup = false
        missionLaunchConfirmation = false
        selectedTowerId = null
        buildType = null
    }

    private fun openMissionSetup() {
        pendingMap = simulation.battleMap
        pendingDifficulty = simulation.difficulty
        pendingSeed = simulation.seed
        pendingElevatedTerrain = simulation.elevatedTerrain
        pendingGridPlacement = simulation.gridPlacement
        pendingMode = simulation.mode
        pendingModule = simulation.tremorModule
        pendingRailModule = if (profile.relayHunterUnlocked) simulation.railModule else RailModule.STANDARD
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

    private fun pendingChallenge() = pendingSeed?.let { RemixChallenge(pendingMap, pendingDifficulty, it, pendingElevatedTerrain, pendingGridPlacement) }

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
        pendingMode = MissionMode.CAMPAIGN
        pendingMap = challenge.map
        pendingDifficulty = challenge.difficulty
        pendingSeed = challenge.seed
        pendingElevatedTerrain = challenge.elevatedTerrain
        pendingGridPlacement = challenge.gridPlacement
        setupFeedback = "Loaded ${challenge.code}"
        setupError = false
    }

    private fun cycleModule() {
        val modules = TremorModule.entries
        pendingModule = modules[(pendingModule.ordinal + 1) % modules.size]
    }

    private fun toggleQuickMission() {
        pendingMode = if (pendingMode == MissionMode.QUICK) MissionMode.CAMPAIGN else MissionMode.QUICK
        if (pendingMode == MissionMode.QUICK) {
            pendingMap = BattleMap.CORRIDOR
            pendingSeed = null
        }
    }

    private fun updateResearch(updated: ResearchProgress, message: String) {
        workshopMessage = try {
            persistResearch(updated)
            profile.applyResearch(updated)
            message
        } catch (failure: RuntimeException) {
            Gdx.app.error("Profile", "Unable to save workshop research", failure)
            "Could not save this change."
        }
    }

    private fun buyResearch(track: ResearchTrack) {
        val updated = profile.research.buy(track) ?: return
        updateResearch(updated, "${track.title} upgraded for future Classic missions.")
    }

    private fun launchMission() {
        if (!clearCheckpoint()) return
        val railModule = if (profile.relayHunterUnlocked) pendingRailModule else RailModule.STANDARD
        simulation = GameSimulation(pendingMap, pendingDifficulty, pendingSeed, profile.rank, pendingModule,
            pendingMode, railModule, profile.research.loadout,
            elevatedTerrain = pendingMode == MissionMode.CAMPAIGN && (pendingSeed == null || pendingElevatedTerrain),
            gridPlacement = pendingSeed == null || pendingGridPlacement)
        resetMissionInterface()
        notify("${pendingMap.title} / ${pendingDifficulty.title}. Build towers, then start wave 1.")
    }

    private fun toggleSfx() {
        combatAudio.enabled = !combatAudio.enabled
        try {
            persistSfx(combatAudio.enabled)
        } catch (failure: RuntimeException) {
            Gdx.app.error("CombatAudio", "Could not save sound preference", failure)
        }
    }

    private fun togglePause() {
        paused = !paused
        if (paused) combatAudio.stop()
        loop.reset()
    }

    override fun pause() {
        combatAudio.stop()
        shiftKeys.clear()
        saveCheckpoint()
        if (!isFinished() && startupMission == null) paused = true
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
    private fun isModal() = startupMission != null || paused || restartConfirmation || missionSetup ||
        enemyGuide || reportOpen || isFinished()

    private fun drawInterface(cursor: Vector2?, previewUpgrade: Boolean) = with(hud) {
        beginFrame(camera.combined, cursor)
        rect(0f, 640f, WIDTH, 80f, PANEL)
        rect(0f, 0f, WIDTH, 80f, PANEL)
        rect(900f, 80f, 300f, 560f, PANEL)
        rect(0f, 638f, WIDTH, 2f, BORDER)
        rect(900f, 80f, 2f, 560f, BORDER)
        text("BRE / TOWER DEFENCE", 24f, 703f, 1.2f, WHITE)
        val missionTitle = if (simulation.mode == MissionMode.QUICK) "QUICK / CORRIDOR" else simulation.battleMap.title.uppercase()
        text("$missionTitle / ${simulation.difficulty.title.uppercase()}", 25f, 667f, 0.75f, MUTED)
        button("Mission [M]", 250f, 650f, 117f, 30f, scale = 0.8f) { openMissionSetup() }
        stat("GOLD", simulation.gold.toString(), 386f, GOLD)
        stat("BASE", "${simulation.lives} / ${simulation.difficulty.startingLives}", 523f, if (simulation.lives <= 5) RED else CYAN)
        stat("WAVE", "${simulation.wave} / ${simulation.totalWaves}", 678f, WHITE)
        button(if (combatAudio.enabled) "SFX on" else "SFX off", 758f, 660f, 66f, 40f,
            scale = 0.70f, action = ::toggleSfx)
        button(if (doubleSpeed) "2x [F]" else "1x [F]", 835f, 660f, 90f, 40f) { doubleSpeed = !doubleSpeed }
        button("Pause [P]", 938f, 660f, 112f, 40f) { togglePause() }
        button("Restart [R]", 1063f, 660f, 113f, 40f) { restartConfirmation = true }

        drawTowerPanel(
            simulation, selectedTower(), buildType, previewUpgrade,
            choose = ::choose,
            openEnemyGuide = { enemyGuide = true },
            cancelPlacement = { buildType = null },
            undoBuild = ::undoLastBuild,
            report = ::report,
            sell = { tower ->
                report(simulation.sellTower(tower.id))
                selectedTowerId = null
            },
        )

        val preparing = simulation.state == GameState.PREPARING
        val briefedWave = if (preparing) simulation.wave + 1 else simulation.wave
        val waveTitle = simulation.waveName(briefedWave)?.let { " / ${it.uppercase()}" }.orEmpty()
        val waveHeading = if (preparing) "NEXT / WAVE $briefedWave" else "WAVE $briefedWave"
        val eliteWave = !preparing && simulation.eliteActive
        val eliteLabel = if (eliteWave) " / ELITE" else ""
        text(waveHeading + waveTitle + eliteLabel, 24f, 58f, 0.85f, if (eliteWave) ORANGE else CYAN)
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
        val showOverdrive = simulation.overdriveEnabled && simulation.state == GameState.RUNNING
        val startWidth = when {
            showOverdrive -> 150f
            hasSwitch -> 188f
            else -> 310f
        }
        button(nextLabel, 565f, 20f, startWidth, 43f, preparing, CYAN, scale = if (hasSwitch || showOverdrive) 0.77f else 0.90f) {
            startWave()
        }
        if (showOverdrive) {
            val seconds = (simulation.overdriveTicksLeft + GameSimulation.UPS - 1) / GameSimulation.UPS
            val label = when {
                simulation.canActivateOverdrive && selectedTower() == null -> "Select tower [X]"
                simulation.canActivateOverdrive -> "Overdrive [X]"
                seconds > 0 -> "Overdrive / ${seconds}s"
                else -> "Overdrive spent"
            }
            button(label, 725f, 20f, 150f, 43f, simulation.canActivateOverdrive && selectedTower() != null,
                accent = GOLD, scale = 0.79f, action = ::activateOverdrive)
        } else if (hasSwitch) {
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
        val activeBoss = simulation.activeBoss
        val hint = placementReason ?: when {
            messageTime > 0f -> message
            simulation.canOfferElite -> {
                val healthBonus = ((GameSimulation.ELITE_HEALTH_MULTIPLIER - 1.0) * 100).toInt()
                "Optional: ${GameSimulation.ELITE_COUNT} enemies gain $healthBonus% health. Clear for +${GameSimulation.ELITE_REWARD}g."
            }
            activeBoss?.isMarshal == true -> {
                val relayCount = activeBoss.supportingRelays.count()
                when {
                    relayCount == 0 -> "Marshal exposed: no active relays remain."
                    activeBoss.bossExposed -> "Marshal exposed in the damage window. $relayCount active relays."
                    else -> "Marshal protected by $relayCount active relays. Target them or use the marked lanes."
                }
            }
            activeBoss?.bossExposed == true -> "Bastion exposed: damage is no longer reduced."
            activeBoss != null -> "Bastion protected: deal full damage in the marked lanes."
            preparing && simulation.showBossWindows -> {
                if (simulation.waveName(briefedWave) == "Marshal") {
                    "The Marshal reaches the core unless stopped. Break both relays or use the marked damage windows."
                } else "Bastion must fall before the core. Build near the two marked damage windows."
            }
            simulation.hasCommanders(briefedWave) -> "Green links mark protected escorts. Take out their commander to remove the protection."
            simulation.wave == 0 -> message
            else -> "Open the enemy guide [G] to check enemy abilities and counters."
        }
        val hintError = placementReason != null || messageError && messageTime > 0f
        rect(18f, 590f, 864f, 32f, BACKGROUND)
        rect(18f, 590f, 3f, 32f, if (hintError) RED else CYAN)
        text(hint, 31f, 611f, 0.9f, if (hintError) RED else WHITE, if (simulation.canOfferElite) 712f else 840f)
        if (simulation.canOfferElite) {
            val status = if (simulation.eliteOffered) "On" else "Off"
            button("Elite: $status [B]", 758f, 592f, 115f, 28f, accent = ORANGE,
                selected = simulation.eliteOffered, scale = 0.78f) { report(simulation.toggleElite()) }
        }

        endHud()
        if (isModal()) {
            beginOverlay()
            drawModal()
            endOverlay()
        }
    }

    private fun wavePreview(): String {
        return if (simulation.state == GameState.PREPARING) {
            "${simulation.nextWaveDescription}  /  Clear bonus +${simulation.nextWaveReward}g"
        } else simulation.waveDescription(simulation.wave)
    }

    private fun HudCanvas.drawModal() {
        if (workshopOpen) {
            drawWorkshop(profile.research, workshopMessage,
                buy = ::buyResearch,
                reset = { updateResearch(profile.research.resetUpgrades(), "Research points refunded.") },
                back = { workshopOpen = false },
            )
            return
        }
        startupMission?.let {
            drawSavedMission(it)
            return
        }
        if (reportOpen) {
            drawWaveReport(simulation.waveReports, simulation.totalWaves, ::closeWaveReport)
            return
        }
        if (enemyGuide) {
            drawEnemyGuide(::closeEnemyGuide)
            return
        }
        if (missionSetup && !missionLaunchConfirmation) {
            drawMissionSetup(
                pendingMap, pendingDifficulty, pendingSeed, pendingMode,
                pendingModule, profile.rank, pendingRailModule, profile.relayHunterUnlocked, profile.research,
                if (pendingMode == MissionMode.QUICK) records.quickBest(pendingDifficulty) else pendingChallenge()?.let(records::best),
                setupFeedback, setupError,
                selectMap = { if (pendingMode.supports(it, pendingSeed)) pendingMap = it },
                selectDifficulty = { pendingDifficulty = it },
                toggleRemix = {
                    if (pendingMode == MissionMode.CAMPAIGN) {
                        pendingSeed = if (pendingSeed == null) newSeed() else null
                        pendingElevatedTerrain = true
                        pendingGridPlacement = true
                    }
                },
                reroll = {
                    if (pendingMode == MissionMode.CAMPAIGN) {
                        pendingSeed = newSeed()
                        pendingElevatedTerrain = true
                        pendingGridPlacement = true
                    }
                },
                toggleQuick = ::toggleQuickMission,
                cycleModule = ::cycleModule,
                toggleRailModule = {
                    if (profile.relayHunterUnlocked) pendingRailModule = when (pendingRailModule) {
                        RailModule.STANDARD -> RailModule.RELAY_HUNTER
                        RailModule.RELAY_HUNTER -> RailModule.STANDARD
                    }
                },
                openWorkshop = {
                    workshopMessage = ""
                    workshopOpen = true
                },
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
                subtitle = if (simulation.bossBreached) "${simulation.waveName(simulation.wave)} reached the core. Build near the marked damage windows."
                    else "You reached wave ${simulation.wave}. Rebuild, mix your towers and try again."
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
            if (newRelayHunter) unlocks.add("Rail: Relay Hunter")
            val unlockLabel = unlocks.joinToString()
            text(if (unlockLabel.isEmpty()) xpLabel else "$xpLabel  /  Unlocked $unlockLabel",
                182f, 336f, 0.92f, GOLD, 636f)
            if (gainedResearch > 0) text("+$gainedResearch research / open Workshop in Mission Setup", 182f, 252f, 0.78f, CYAN)
            if (newRemixBest) {
                val mode = if (simulation.mode == MissionMode.QUICK) "Quick mission" else "Remix"
                text("New local best / $mode", 182f, 252f, 0.87f, CYAN)
            }
            button("Retry mission [Enter / R]", 182f, 267f, 300f, 52f, accent = CYAN) { restart() }
            button("Choose mission [M]", 502f, 267f, 300f, 52f) { openMissionSetup() }
            waveReportButton()
        } else {
            button("Choose mission [M]", 182f, 337f, 620f, 40f) { openMissionSetup() }
            button("Resume [P / Space]", 182f, 267f, 300f, 52f, accent = CYAN) { togglePause() }
            button("Restart mission", 502f, 267f, 300f, 52f) { restartConfirmation = true }
            waveReportButton()
        }
    }

    private fun HudCanvas.waveReportButton() {
        button("Wave report [L]", 502f, 220f, 300f, 33f, accent = CYAN, scale = 0.8f) { openWaveReport() }
    }

    private fun HudCanvas.drawSavedMission(saved: GameSimulation) {
        rect(140f, 205f, 720f, 315f, OVERLAY)
        rect(140f, 516f, 720f, 4f, CYAN)
        text("CONTINUE DEFENCE?", 182f, 473f, 1.85f, WHITE)
        val mode = if (saved.mode == MissionMode.QUICK) " / Quick"
        else saved.challenge?.let { " / Remix ${it.seed}" }.orEmpty()
        text("${saved.battleMap.title} / ${saved.difficulty.title}$mode", 182f, 421f, 1f, MUTED, 636f)
        text("Before wave ${saved.wave + 1} / ${saved.lives} lives / ${saved.gold}g", 182f, 384f, 0.95f, CYAN)
        button("Continue [Enter]", 182f, 267f, 300f, 52f, accent = CYAN) { continueMission() }
        button("Discard save", 502f, 267f, 300f, 52f, accent = ORANGE) { discardSave() }
        if (checkpointError) text("Could not clear the saved defence.", 182f, 240f, 0.87f, RED)
    }

    private fun HudCanvas.stat(label: String, value: String, x: Float, color: Color) {
        text(label, x, 701f, 0.75f, MUTED)
        text(value, x, 679f, 1.35f, color)
    }

    override fun dispose() {
        hide()
        combatAudio.dispose()
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
