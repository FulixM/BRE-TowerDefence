package io.felixmenzel.towerdefence.ui

import io.felixmenzel.towerdefence.simulation.BattleMap
import io.felixmenzel.towerdefence.simulation.Difficulty
import io.felixmenzel.towerdefence.simulation.MissionMode
import io.felixmenzel.towerdefence.simulation.ResearchLoadout
import io.felixmenzel.towerdefence.simulation.ResearchProgress
import io.felixmenzel.towerdefence.simulation.ResearchTrack
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.GOLD
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.PANEL
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

internal fun HudCanvas.drawWorkshop(
    progress: ResearchProgress,
    message: String,
    buy: (ResearchTrack) -> Unit,
    reset: () -> Unit,
    back: () -> Unit,
) {
    rect(140f, 82f, 720f, 513f, OVERLAY)
    rect(140f, 591f, 720f, 4f, CYAN)
    text("WORKSHOP", 182f, 555f, 1.65f, WHITE)
    text("${progress.available} research available", 580f, 552f, 0.91f, GOLD)
    text("Upgrades apply when you start or retry a Classic campaign.", 182f, 511f, 0.9f, MUTED, 635f)

    text("RESEARCH UPGRADES", 182f, 476f, 0.84f, CYAN)
    for ((index, track) in ResearchTrack.entries.withIndex()) {
        val y = 394f - index * 67f
        val level = progress.loadout.level(track)
        val cost = progress.loadout.nextCost(track)
        rect(182f, y, 321f, 60f, PANEL)
        text("${track.title}  $level/${ResearchLoadout.MAX_LEVEL}", 193f, y + 48f, 0.79f, WHITE)
        val benefit = track.benefit(level)
        val preview = if (cost == null) benefit else "$benefit > ${track.benefit(level + 1)}"
        text(preview, 193f, y + 25f, 0.68f, MUTED, 219f)
        if (cost != null) {
            button("Buy $cost RP", 419f, y + 13f, 76f, 33f, progress.available >= cost, GOLD, 0.68f) { buy(track) }
        }
    }

    text("MAP MASTERY", 531f, 476f, 0.84f, CYAN)
    text("Earn 1 RP every 5 waves per map and difficulty. Higher difficulties also count for easier ones.",
        531f, 449f, 0.77f, MUTED, 273f)
    val tableColumns = listOf(654f, 704f, 756f)
    for ((index, difficulty) in Difficulty.entries.withIndex()) {
        text(difficulty.title.take(3), tableColumns[index], 372f, 0.73f, MUTED)
    }
    for ((index, map) in BattleMap.entries.withIndex()) {
        val y = 319f - index * 34f
        rect(531f, y, 271f, 30f, PANEL)
        text(map.title, 541f, y + 20f, 0.73f, WHITE)
        for ((column, difficulty) in Difficulty.entries.withIndex()) {
            val best = progress.best(map, difficulty)
            val color = if (best == MissionMode.CAMPAIGN.waveCount) GOLD else WHITE
            text(best.toString(), tableColumns[column] + 3f, y + 20f, 0.73f, color)
        }
    }
    text("Best waves cleared", 541f, 202f, 0.7f, MUTED)
    if (message.isNotEmpty()) text(message, 182f, 175f, 0.72f, CYAN)
    button("Back [Esc]", 182f, 110f, 180f, 38f, scale = 0.82f, action = back)
    button("Refund all upgrades", 616f, 110f, 186f, 38f, progress.available < progress.earned,
        GOLD, 0.77f, action = reset)
}
