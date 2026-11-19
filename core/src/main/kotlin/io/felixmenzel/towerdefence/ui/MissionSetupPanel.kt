package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.graphics.Color
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Difficulty
import io.felixmenzel.towerdefence.simulation.RailModule
import io.felixmenzel.towerdefence.simulation.RemixScore
import io.felixmenzel.towerdefence.simulation.ResearchLoadout
import io.felixmenzel.towerdefence.simulation.ResearchProgress
import io.felixmenzel.towerdefence.simulation.TremorModule
import io.felixmenzel.towerdefence.simulation.Waypoint
import io.felixmenzel.towerdefence.simulation.MissionMode
import io.felixmenzel.towerdefence.ui.DefenseScreen.Companion.BOARD_HEIGHT
import io.felixmenzel.towerdefence.ui.DefenseScreen.Companion.BOARD_WIDTH
import io.felixmenzel.towerdefence.ui.HudPalette.BACKGROUND
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.RED
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

internal fun HudCanvas.drawMissionSetup(
    pendingMap: BattleMap,
    pendingDifficulty: Difficulty,
    pendingSeed: Int?,
    mode: MissionMode,
    module: TremorModule,
    rank: Int,
    railModule: RailModule,
    relayHunterUnlocked: Boolean,
    research: ResearchProgress,
    best: RemixScore?,
    feedback: String,
    feedbackError: Boolean,
    selectMap: (BattleMap) -> Unit,
    selectDifficulty: (Difficulty) -> Unit,
    toggleRemix: () -> Unit,
    reroll: () -> Unit,
    toggleQuick: () -> Unit,
    cycleModule: () -> Unit,
    toggleRailModule: () -> Unit,
    openWorkshop: () -> Unit,
    copyCode: () -> Unit,
    importCode: () -> Unit,
    launch: () -> Unit,
    cancel: () -> Unit,
) {
    rect(140f, 46f, 720f, 549f, OVERLAY)
    rect(140f, 591f, 720f, 4f, CYAN)
    text("MISSION SETUP", 182f, 559f, 1.65f, WHITE)
    button("Workshop / ${research.available} RP", 610f, 530f, 192f, 37f, scale = 0.82f, accent = CYAN,
        action = openWorkshop)
    text("${mode.title} / ${mode.waveCount} waves", 182f, 524f, 0.9f, MUTED, 636f)
    val mapGap = 12f
    val mapWidth = (636f - mapGap * (BattleMap.entries.size - 1)) / BattleMap.entries.size
    for ((index, map) in BattleMap.entries.withIndex()) {
        val x = 182f + index * (mapWidth + mapGap)
        rect(x, 345f, mapWidth, 145f, BACKGROUND)
        button(map.title, x, 440f, mapWidth, 42f, enabled = mode == MissionMode.CAMPAIGN || map == BattleMap.CORRIDOR,
            accent = if (pendingMap == map) CYAN else BORDER) { selectMap(map) }
        // Draw the preview from the mission route.
        val routeColor = if (pendingMap == map) CYAN else BORDER
        fun previewRoute(path: List<Waypoint>, color: Color) {
            for ((from, to) in path.zipWithNext()) {
                line(x + 15f + from.x.toFloat() / BOARD_WIDTH * (mapWidth - 30f),
                    354f + from.y.toFloat() / BOARD_HEIGHT * 72f,
                    x + 15f + to.x.toFloat() / BOARD_WIDTH * (mapWidth - 30f),
                    354f + to.y.toFloat() / BOARD_HEIGHT * 72f, 3f, color)
            }
        }
        map.alternatePath?.let { previewRoute(it, MUTED) }
        previewRoute(map.path, routeColor)
    }
    text("DIFFICULTY", 182f, 320f, 0.95f, WHITE)
    for ((index, difficulty) in Difficulty.entries.withIndex()) {
        button(difficulty.title, 182f + index * 212f, 254f, 196f, 42f,
            accent = if (pendingDifficulty == difficulty) CYAN else BORDER) { selectDifficulty(difficulty) }
    }
    val gold = mode.startingGold(pendingDifficulty) + if (ResearchLoadout.appliesTo(mode, pendingSeed)) {
        research.loadout.startingGoldBonus
    } else 0
    text("$gold gold / ${pendingDifficulty.startingLives} base lives / Enemy health ${pendingDifficulty.healthMultiplier}x",
        182f, 249f, 0.78f, CYAN, 636f)
    val quick = mode == MissionMode.QUICK
    button(if (pendingSeed == null) "Mode / Classic" else "Mode / Remix", 182f, 210f, 178f, 24f,
        enabled = !quick, accent = if (pendingSeed == null) BORDER else CYAN, scale = 0.75f, action = toggleRemix)
    if (pendingSeed != null) {
        button("Reroll seed $pendingSeed", 370f, 210f, 182f, 24f, scale = 0.75f, action = reroll)
    } else if (quick) {
        text("Mixed squads on The Corridor.", 374f, 228f, 0.75f, MUTED)
    } else {
        text("Remix varies spawns.", 374f, 228f, 0.75f, MUTED)
    }
    button(if (quick) "Switch to Campaign" else "Quick mission / 6 waves", 563f, 210f, 239f, 24f,
        accent = CYAN, scale = 0.75f, action = toggleQuick)
    button("Launch mission [Enter]", 182f, 160f, 300f, 48f, accent = CYAN) { launch() }
    button("Cancel [Esc]", 502f, 160f, 300f, 48f) { cancel() }
    button("Paste Remix code", 182f, 128f, 142f, 24f, scale = 0.76f, action = importCode)
    button("Copy code", 334f, 128f, 103f, 24f, enabled = pendingSeed != null, scale = 0.76f, action = copyCode)
    val bestLabel = best?.let { "Best: ${it.cleared} waves / ${it.lives} lives / ${it.kills} kills" }
    if (quick || pendingSeed != null) text(bestLabel ?: "No local best yet", 453f, 146f, 0.78f, CYAN, 346f)
    if (feedback.isNotEmpty()) text(feedback, 453f, 126f, 0.74f, if (feedbackError) RED else WHITE, 346f)
    if (rank >= TremorModule.REQUIRED_RANK) {
        button("Tremor / ${module.title}", 182f, 92f, 180f, 25f, scale = 0.76f, action = cycleModule)
        text("${module.radius.toInt()} impact radius  /  ${module.slowPercent}% slow", 379f, 110f, 0.77f, MUTED)
    } else {
        text("Tremor modules unlock at field rank ${TremorModule.REQUIRED_RANK}.", 182f, 106f, 0.78f, MUTED)
    }

    if (relayHunterUnlocked) {
        button("Rail / ${railModule.title}", 182f, 54f, 180f, 25f, scale = 0.76f, action = toggleRailModule)
        val detail = if (railModule == RailModule.RELAY_HUNTER) "2x shield damage / +25% vs relays / +25% reload"
            else "Equip Relay Hunter to trade fire rate for shield damage."
        text(detail, 379f, 72f, 0.77f, MUTED)
    } else {
        text("Rail workshop: defeat the Marshal on The Corridor to unlock Relay Hunter.", 182f, 72f, 0.77f, MUTED, 636f)
    }
}
