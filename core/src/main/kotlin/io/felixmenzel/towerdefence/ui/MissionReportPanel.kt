package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.graphics.Color
import io.felixmenzel.towerdefence.simulation.WaveReport
import io.felixmenzel.towerdefence.ui.HudPalette.BACKGROUND
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.CYAN
import io.felixmenzel.towerdefence.ui.HudPalette.ECHO
import io.felixmenzel.towerdefence.ui.HudPalette.GOLD
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.OVERLAY
import io.felixmenzel.towerdefence.ui.HudPalette.PANEL
import io.felixmenzel.towerdefence.ui.HudPalette.RED
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

internal fun HudCanvas.drawWaveReport(reports: List<WaveReport>, totalWaves: Int, close: () -> Unit) {
    val cleared = reports.count { it.cleared }
    rect(140f, 110f, 720f, 478f, OVERLAY)
    rect(140f, 584f, 720f, 4f, CYAN)
    text("MISSION REPORT", 182f, 558f, 1.65f, WHITE)

    reportTotal(182f, "WAVES CLEARED", "$cleared / $totalWaves", CYAN)
    reportTotal(398f, "TOTAL LEAKS", reports.sumOf { it.leaked }.toString(), RED)
    reportTotal(614f, "TOTAL ECHO HITS", reports.sumOf { it.echoHits }.toString(), ECHO)

    val waveReports = reports.associateBy { it.wave }
    val firstColumnSize = (totalWaves + 1) / 2
    drawReportColumn(182f, 1..firstColumnSize, waveReports)
    drawReportColumn(508f, (firstColumnSize + 1)..totalWaves, waveReports)

    button("Back", 371f, 131f, 258f, 40f, accent = CYAN, action = close)
}

private fun HudCanvas.reportTotal(x: Float, title: String, value: String, accent: Color) {
    rect(x, 468f, 204f, 57f, PANEL)
    rect(x, 468f, 3f, 57f, accent)
    text(title, x + 14f, 514f, 0.72f, MUTED)
    text(value, x + 14f, 493f, 1.1f, accent)
}

private fun HudCanvas.drawReportColumn(x: Float, waves: IntRange, reports: Map<Int, WaveReport>) {
    text("WAVE", x + 11f, 448f, 0.75f, MUTED)
    text("LEAKS", x + 148f, 448f, 0.75f, MUTED)
    text("ECHO HITS", x + 212f, 448f, 0.75f, MUTED)
    for ((index, wave) in waves.withIndex()) {
        val report = reports[wave]
        val y = 410f - index * 28f
        val accent = when {
            report == null -> BORDER
            !report.cleared -> RED
            report.elite -> GOLD
            else -> CYAN
        }
        val status = when {
            report == null -> ""
            report.elite && !report.cleared -> "  Elite / Lost"
            !report.cleared -> "  Lost"
            report.elite -> "  Elite"
            else -> ""
        }
        rect(x, y, 310f, 24f, if (report == null) BACKGROUND else PANEL)
        rect(x, y, 2f, 24f, accent)
        text(wave.toString().padStart(2, '0') + status, x + 11f, y + 18f, 0.77f,
            if (status.isNotEmpty()) accent else if (report == null) MUTED else WHITE)
        text(report?.leaked?.toString() ?: "-", x + 148f, y + 18f, 0.77f,
            if (report != null && report.leaked > 0) RED else MUTED)
        text(report?.echoHits?.toString() ?: "-", x + 212f, y + 18f, 0.77f,
            if (report != null && report.echoHits > 0) ECHO else MUTED)
    }
}
