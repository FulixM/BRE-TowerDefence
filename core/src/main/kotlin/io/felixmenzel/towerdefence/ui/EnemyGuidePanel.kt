package io.felixmenzel.towerdefence.ui

import io.felixmenzel.towerdefence.simulation.EnemyType
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

internal fun HudCanvas.drawEnemyGuide(close: () -> Unit) {
    rect(140f, 134f, 720f, 452f, OVERLAY)
    rect(140f, 582f, 720f, 4f, CYAN)
    text("ENEMY GUIDE", 182f, 550f, 1.65f, WHITE)
    for ((index, type) in EnemyType.entries.withIndex()) {
        val y = 493f - index * 46f
        text(type.title, 182f, y, 0.95f, WHITE)
        text("Wave ${type.firstWave}+", 294f, y, 0.85f, CYAN)
        text(type.description, 394f, y, 0.83f, MUTED, 411f)
    }
    button("Back [Esc / G]", 182f, 162f, 300f, 44f) { close() }
}
