package io.felixmenzel.towerdefence.ui

import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Difficulty
import io.felixmenzel.towerdefence.simulation.GameSimulation.Companion.TOTAL_WAVES
import io.felixmenzel.towerdefence.ui.DefenseScreen.Companion.BOARD_HEIGHT
import io.felixmenzel.towerdefence.ui.DefenseScreen.Companion.BOARD_WIDTH
import io.felixmenzel.towerdefence.ui.HudPalette.BACKGROUND
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

internal fun HudCanvas.drawMissionSetup(
    pendingMap: BattleMap,
    pendingDifficulty: Difficulty,
    pendingSeed: Int?,
    selectMap: (BattleMap) -> Unit,
    selectDifficulty: (Difficulty) -> Unit,
    toggleRemix: () -> Unit,
    reroll: () -> Unit,
    launch: () -> Unit,
    cancel: () -> Unit,
) {
    rect(140f, 125f, 720f, 470f, OVERLAY)
    rect(140f, 591f, 720f, 4f, CYAN)
    text("MISSION SETUP", 182f, 559f, 1.65f, WHITE)
    text("Choose a route and challenge. $TOTAL_WAVES waves stand between you and victory.", 182f, 524f, 0.9f, MUTED, 636f)
    val mapGap = 12f
    val mapWidth = (636f - mapGap * (BattleMap.entries.size - 1)) / BattleMap.entries.size
    for ((index, map) in BattleMap.entries.withIndex()) {
        val x = 182f + index * (mapWidth + mapGap)
        rect(x, 345f, mapWidth, 145f, BACKGROUND)
        button(map.title, x, 440f, mapWidth, 42f,
            accent = if (pendingMap == map) CYAN else BORDER) { selectMap(map) }
        // Draw the preview from the mission route.
        val routeColor = if (pendingMap == map) CYAN else BORDER
        for ((from, to) in map.path.zipWithNext()) {
            line(x + 15f + from.x.toFloat() / BOARD_WIDTH * (mapWidth - 30f),
                354f + from.y.toFloat() / BOARD_HEIGHT * 72f,
                x + 15f + to.x.toFloat() / BOARD_WIDTH * (mapWidth - 30f),
                354f + to.y.toFloat() / BOARD_HEIGHT * 72f, 3f, routeColor)
        }
    }
    text("DIFFICULTY", 182f, 320f, 0.95f, WHITE)
    for ((index, difficulty) in Difficulty.entries.withIndex()) {
        button(difficulty.title, 182f + index * 212f, 254f, 196f, 42f,
            accent = if (pendingDifficulty == difficulty) CYAN else BORDER) { selectDifficulty(difficulty) }
    }
    text("${pendingDifficulty.startingGold} gold / ${pendingDifficulty.startingLives} base lives / Enemy health ${pendingDifficulty.healthMultiplier}x",
        182f, 249f, 0.78f, CYAN, 636f)
    button(if (pendingSeed == null) "Mode / Classic" else "Mode / Remix", 182f, 210f, 178f, 24f,
        accent = if (pendingSeed == null) BORDER else CYAN, scale = 0.75f, action = toggleRemix)
    if (pendingSeed != null) {
        button("Reroll seed $pendingSeed", 370f, 210f, 182f, 24f, scale = 0.75f, action = reroll)
        text("Retry keeps the seed.", 563f, 228f, 0.75f, MUTED)
    } else text("Remix varies enemy order and spawn timing.", 374f, 228f, 0.75f, MUTED)
    button("Launch mission [Enter]", 182f, 160f, 300f, 48f, accent = CYAN) { launch() }
    button("Cancel [Esc]", 502f, 160f, 300f, 48f) { cancel() }
}
