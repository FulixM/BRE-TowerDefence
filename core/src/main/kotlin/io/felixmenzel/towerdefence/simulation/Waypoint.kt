package io.felixmenzel.towerdefence.simulation

data class Waypoint(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite()) { "Waypoint coordinates must be finite" }
    }
}
