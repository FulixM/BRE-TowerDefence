package io.felixmenzel.towerdefence.ui

import io.felixmenzel.towerdefence.rendering.EnemyDefenseColors
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
        val color = when {
            type.damageMultiplier < 1.0 -> EnemyDefenseColors.ARMOR
            type.shieldFraction > 0.0 -> EnemyDefenseColors.SHIELD
            type.regenerationFraction > 0.0 -> EnemyDefenseColors.REGENERATION
            else -> WHITE
        }
        text(type.title, 182f, y, 0.95f, color)
        text("Wave ${type.firstWave}+", 294f, y, 0.85f, CYAN)
        text(type.description, 394f, y, 0.83f, MUTED, 411f)
    }
    rect(182f, 244f, 636f, 1f, MUTED)
    rect(182f, 226f, 16f, 5f, EnemyDefenseColors.HEALTH)
    text("Health", 205f, 234f, 0.83f, WHITE)
    rect(306f, 226f, 16f, 3f, EnemyDefenseColors.SHIELD)
    text("Shield", 329f, 234f, 0.83f, WHITE)
    for (plate in 0..2) rect(432f + plate * 6f, 226f, 4f, 4f, EnemyDefenseColors.ARMOR)
    text("Armor (resistance)", 455f, 234f, 0.83f, WHITE)
    rect(653f, 227f, 11f, 2f, EnemyDefenseColors.REGENERATION)
    rect(657f, 223f, 3f, 10f, EnemyDefenseColors.REGENERATION)
    text("Regenerating", 674f, 234f, 0.83f, WHITE)
    button("Back [Esc / G]", 182f, 162f, 300f, 44f) { close() }
}
