package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import io.felixmenzel.towerdefence.rendering.towerColor
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE
import kotlin.math.roundToLong

private val damagePanelBounds = Rectangle(610f, 390f, 278f, 244f)

internal fun insideDamagePanel(point: Vector2): Boolean = damagePanelBounds.contains(point)

internal fun HudCanvas.drawDamagePanel(simulation: GameSimulation, wholeRun: Boolean, selectScope: (Boolean) -> Unit) {
    with(damagePanelBounds) {
        rect(x, y, width, height, OVERLAY)
        rect(x, y + height - 2f, width, 2f, CYAN)
    }
    button("Wave", 624f, 596f, 114f, 26f, accent = if (wholeRun) BORDER else CYAN) { selectScope(false) }
    button("Mission", 746f, 596f, 128f, 26f, accent = if (wholeRun) CYAN else BORDER) { selectScope(true) }
    val scope = when {
        wholeRun -> "MISSION TOTAL"
        simulation.wave == 0 -> "NO WAVE YET"
        simulation.state == GameState.RUNNING -> "CURRENT WAVE ${simulation.wave}"
        else -> "WAVE ${simulation.wave}"
    }
    text(scope, 624f, 585f, 0.75f, MUTED)
    text("HP + SHIELD", 793f, 585f, 0.75f, MUTED)
    val damage = simulation.damageByTowerType(wholeRun).entries.sortedByDescending { it.value }
    if (damage.isEmpty()) text("No damage recorded.", 624f, 557f, 0.82f, WHITE)
    damage.forEachIndexed { index, (type, amount) ->
        val y = 557f - index * 17f
        text(type.title, 624f, y, 0.83f, towerColor(type))
        text(amount.roundToLong().toString(), 793f, y, 0.83f, WHITE)
    }
}
