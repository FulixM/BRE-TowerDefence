package io.felixmenzel.towerdefence.ui

import io.felixmenzel.towerdefence.rendering.towerColor
import io.felixmenzel.towerdefence.simulation.ActionResult
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.GameState
import io.felixmenzel.towerdefence.simulation.Tower
import io.felixmenzel.towerdefence.simulation.TowerType
import io.felixmenzel.towerdefence.simulation.TremorModule
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.ECHO
import io.felixmenzel.towerdefence.ui.HudPalette.GOLD
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.ORANGE
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE
import kotlin.math.roundToInt

internal fun HudCanvas.drawTowerPanel(
    simulation: GameSimulation,
    tower: Tower?,
    buildType: TowerType?,
    choose: (TowerType) -> Unit,
    openEnemyGuide: () -> Unit,
    cancelPlacement: () -> Unit,
    report: (ActionResult) -> Unit,
    sell: (Tower) -> Unit,
) {
    text("BUILD TOWERS", 922f, 614f, 1f, WHITE)
    for ((index, type) in TowerType.entries.withIndex()) {
        val x = 922f + (index % 2) * 130f
        val y = 552f - (index / 2) * 43f
        val key = (index + 1) % 10
        towerCard(type, key, x, y, simulation.gold, simulation.isUnlocked(type), buildType == type, choose)
    }
    button("Enemies [G]", 922f, 330f, 124f, 32f, scale = 0.8f) { openEnemyGuide() }
    val armed = tower != null && simulation.echo.armedTowerId == tower.id
    button(if (armed) "Armed [E]" else "Record [E]", 1052f, 330f, 124f, 32f,
        enabled = tower != null && simulation.state == GameState.PREPARING && simulation.wave < simulation.totalWaves - 1,
        accent = ECHO, scale = 0.8f, selected = armed) {
        tower?.let { report(simulation.selectEchoTower(it.id)) }
    }

    when {
        buildType != null -> buildPanel(buildType, simulation.tremorModule, cancelPlacement)
        tower != null -> selectedPanel(tower, simulation, report, sell)
        else -> {
            text("YOUR DEFENCE", 922f, 313f, 0.95f, WHITE)
            text("${simulation.towers.size} towers deployed", 922f, 285f, 0.95f, CYAN)
            text("Click a tower to inspect, upgrade or sell it. Build near bends to cover more of the route.",
                922f, 257f, 0.9f, MUTED, 254f)
        }
    }
}

private fun HudCanvas.towerCard(
    type: TowerType, key: Int, x: Float, y: Float, gold: Int, unlocked: Boolean,
    selected: Boolean, choose: (TowerType) -> Unit,
) {
    val accent = towerColor(type)
    button("[$key] ${type.title}", x, y, 124f, 38f, enabled = unlocked, accent = accent, scale = 0.84f,
        selected = selected, labelOffsetY = 7f) { choose(type) }
    val info = if (unlocked) "${type.cost}g" else "Rank ${type.requiredRank}"
    text(info, x + 12f, y + 13f, 0.70f, if (unlocked && gold >= type.cost) GOLD else MUTED)
}

private fun HudCanvas.buildPanel(type: TowerType, module: TremorModule, cancelPlacement: () -> Unit) {
    text("PLACING / ${type.title.uppercase()}", 922f, 313f, 0.95f, towerColor(type))
    text("Cost ${type.cost}  /  Range ${type.baseRange.roundToInt()}", 922f, 285f, 0.9f, WHITE)
    text("${type.baseDamage.roundToInt()} damage / ${formatRate(type.baseAttackIntervalTicks)} shots per sec", 922f, 262f, 0.86f, MUTED)
    val description = if (type == TowerType.TREMOR) {
        "${module.title} / ${module.radius.toInt()} radius / ${module.slowPercent}% slow"
    } else type.description
    text(description, 922f, 230f, 0.88f, WHITE, 250f)
    text("Click clear ground to build.", 922f, 175f, 0.80f, MUTED, 250f)
    button("Cancel placement [Esc]", 922f, 116f, 254f, 40f) { cancelPlacement() }
}

private fun HudCanvas.selectedPanel(
    tower: Tower, simulation: GameSimulation, report: (ActionResult) -> Unit, sell: (Tower) -> Unit,
) {
    val module = if (tower.type == TowerType.TREMOR) " / ${tower.tremorModule.title.uppercase()}" else ""
    text("${tower.type.title.uppercase()}$module / LEVEL ${tower.level}", 922f, 313f, 0.95f, towerColor(tower.type))
    val upgrade = tower.nextUpgrade
    val cost = upgrade?.cost
    text("Damage ${formatDamage(tower.damage)}" + (upgrade?.let { " -> ${formatDamage(it.damage)}" } ?: " / MAX"),
        922f, 285f, 0.88f, WHITE)
    text("Range ${tower.range.roundToInt()}" + (upgrade?.let { " -> ${it.range.roundToInt()}" } ?: ""),
        922f, 263f, 0.86f, MUTED)
    text("Rate ${formatRate(tower.attackIntervalTicks)}" + (upgrade?.let { " -> ${formatRate(it.attackIntervalTicks)}" } ?: "") + " shots/s",
        922f, 241f, 0.83f, MUTED)
    button(if (cost == null) "Fully upgraded" else "Upgrade [U]  /  $cost gold", 922f, 179f, 254f, 42f,
        cost != null && simulation.gold >= cost, towerColor(tower.type)) { report(simulation.upgradeTower(tower.id)) }
    button("Sell  /  +${tower.sellValue} gold", 922f, 123f, 254f, 40f, accent = ORANGE) {
        sell(tower)
    }
    button("Target: ${tower.targetPriority.title} [T]", 922f, 87f, 254f, 28f, scale = 0.82f) {
        report(simulation.cycleTargetPriority(tower.id))
    }
}

private fun formatDamage(damage: Double): String =
    String.format(java.util.Locale.ROOT, "%.2f", damage).trimEnd('0').trimEnd('.')

private fun formatRate(ticks: Long): String = String.format(java.util.Locale.ROOT, "%.1f", 60.0 / ticks)
