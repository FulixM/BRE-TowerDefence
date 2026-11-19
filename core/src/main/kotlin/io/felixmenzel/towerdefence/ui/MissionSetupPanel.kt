package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.graphics.Color
import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Difficulty
import io.felixmenzel.towerdefence.simulation.RemixScore
import io.felixmenzel.towerdefence.simulation.TremorModule
import io.felixmenzel.towerdefence.simulation.Waypoint
import io.felixmenzel.towerdefence.simulation.GameSimulation.Companion.TOTAL_WAVES
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
    module: TremorModule,
    rank: Int,
    best: RemixScore?,
    feedback: String,
    feedbackError: Boolean,
    selectMap: (BattleMap) -> Unit,
    selectDifficulty: (Difficulty) -> Unit,
    toggleRemix: () -> Unit,
    reroll: () -> Unit,
    cycleModule: () -> Unit,
    copyCode: () -> Unit,
    importCode: () -> Unit,
    launch: () -> Unit,
    cancel: () -> Unit,
) {
    rect(140f, 82f, 720f, 513f, OVERLAY)
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
    button("Paste Remix code", 182f, 128f, 142f, 24f, scale = 0.76f, action = importCode)
    button("Copy code", 334f, 128f, 103f, 24f, enabled = pendingSeed != null, scale = 0.76f, action = copyCode)
    val bestLabel = best?.let { "Best: ${it.cleared} waves / ${it.lives} lives / ${it.kills} kills" }
    if (pendingSeed != null) text(bestLabel ?: "No local best for this code", 453f, 146f, 0.78f, CYAN, 346f)
    if (feedback.isNotEmpty()) text(feedback, 453f, 126f, 0.74f, if (feedbackError) RED else WHITE, 346f)
    if (rank >= TremorModule.REQUIRED_RANK) {
        button("Tremor / ${module.title}", 182f, 92f, 180f, 25f, scale = 0.76f, action = cycleModule)
        text("${module.radius.toInt()} impact radius  /  ${module.slowPercent}% slow", 379f, 110f, 0.77f, MUTED)
    } else {
        text("Tremor modules unlock at field rank ${TremorModule.REQUIRED_RANK}.", 182f, 106f, 0.78f, MUTED)
    }
}
