package io.felixmenzel.towerdefence.simulation

import kotlin.math.sqrt

internal data class RouteGrade(val x: Float, val y: Float, val dx: Float, val dy: Float, val uphill: Boolean)

/** Find real height changes along a polyline, including bends and overlapping terraces. */
internal fun BattleTerrain.routeGrades(path: List<Waypoint>): List<RouteGrade> = buildList {
    if (terraces.isEmpty()) return@buildList
    var previousHeight: Int? = null
    for (index in 0 until path.lastIndex) {
        val start = path[index]
        val end = path[index + 1]
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = sqrt(dx * dx + dy * dy)
        if (length == 0.0) continue
        val ux = dx / length
        val uy = dy / length
        val cuts = mutableListOf(0.0, length)
        for (terrace in terraces) {
            val cx = terrace.x - start.x
            val cy = terrace.y - start.y
            val along = cx * ux + cy * uy
            val across = cx * uy - cy * ux
            for (radius in listOf(terrace.radius, terrace.summitRadius)) {
                val halfChordSquared = radius * radius - across * across
                if (halfChordSquared < 0.0) continue
                val halfChord = sqrt(halfChordSquared)
                for (cut in listOf(along - halfChord, along + halfChord)) {
                    if (cut > 0.0 && cut < length) cuts.add(cut)
                }
            }
        }
        cuts.sort()
        for (cutIndex in 0 until cuts.lastIndex) {
            val from = cuts[cutIndex]
            val to = cuts[cutIndex + 1]
            if (to - from < 1e-7) continue
            val midpoint = (from + to) / 2.0
            val height = heightAt(start.x + ux * midpoint, start.y + uy * midpoint)
            val previous = previousHeight
            if (previous != null && previous != height) {
                add(RouteGrade((start.x + ux * from).toFloat(), (start.y + uy * from).toFloat(),
                    ux.toFloat(), uy.toFloat(), uphill = height > previous))
            }
            previousHeight = height
        }
    }
}
