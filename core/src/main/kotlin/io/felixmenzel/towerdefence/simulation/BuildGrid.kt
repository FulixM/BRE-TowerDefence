package io.felixmenzel.towerdefence.simulation

/** The camera, build preview, and simulation all use the same board coordinates. */
internal object BuildGrid {
    const val SIZE = 44
    const val COLUMNS = 20
    const val ROWS = 12
    const val LEFT = 10
    const val BOTTOM = 16

    fun center(x: Double, y: Double): Waypoint? {
        if (!(x >= LEFT && x < LEFT + COLUMNS * SIZE && y >= BOTTOM && y < BOTTOM + ROWS * SIZE)) return null
        val column = ((x - LEFT) / SIZE).toInt()
        val row = ((y - BOTTOM) / SIZE).toInt()
        return Waypoint(LEFT + column * SIZE + SIZE / 2.0, BOTTOM + row * SIZE + SIZE / 2.0)
    }
}
