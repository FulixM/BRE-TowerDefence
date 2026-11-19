package io.felixmenzel.towerdefence.simulation

/** The camera, build preview, and simulation all use the same board coordinates. */
internal object BuildGrid {
    const val SIZE = 44
    const val COLUMNS = 20
    const val ROWS = 12
    const val LEFT = 10
    const val BOTTOM = 16

    val centers = List(COLUMNS * ROWS) { index ->
        Waypoint(LEFT + (index % COLUMNS + 0.5) * SIZE, BOTTOM + (index / COLUMNS + 0.5) * SIZE)
    }

    fun center(x: Double, y: Double): Waypoint? {
        if (!(x >= LEFT && x < LEFT + COLUMNS * SIZE && y >= BOTTOM && y < BOTTOM + ROWS * SIZE)) return null
        val column = ((x - LEFT) / SIZE).toInt()
        val row = ((y - BOTTOM) / SIZE).toInt()
        return centers[row * COLUMNS + column]
    }
}
