package io.felixmenzel.towerdefence.simulation

import java.util.Collections

/** Authored routes in the existing 900 x 560 world; coordinates denote enemy centres. */
enum class BattleMap(val title: String, val code: String, path: List<Waypoint>, alternate: List<Waypoint>? = null) {
    // Preserve the first mission's route exactly, so its established balance remains reproducible.
    CORRIDOR(
        "The Corridor", "C",
        listOf(
            Waypoint(50.0, 160.0),
            Waypoint(300.0, 160.0),
            Waypoint(300.0, 350.0),
            Waypoint(600.0, 350.0),
            Waypoint(600.0, 100.0),
            Waypoint(850.0, 100.0),
        ),
    ),
    // The central return rewards overlapping coverage; the final bend leaves room for a fallback.
    SWITCHBACK(
        "Switchback", "S",
        listOf(
            Waypoint(50.0, 410.0),
            Waypoint(240.0, 410.0),
            Waypoint(240.0, 160.0),
            Waypoint(500.0, 160.0),
            Waypoint(500.0, 390.0),
            Waypoint(730.0, 390.0),
            Waypoint(730.0, 210.0),
            Waypoint(850.0, 210.0),
        ),
    ),
    CROSSWIND(
        "Crosswind Pass", "X",
        listOf(
            Waypoint(50.0, 120.0),
            Waypoint(210.0, 120.0),
            Waypoint(350.0, 360.0),
            Waypoint(510.0, 360.0),
            Waypoint(650.0, 145.0),
            Waypoint(850.0, 145.0),
        ),
    ),
    TWIN_LOCKS(
        "Twin Locks", "T",
        listOf(
            Waypoint(50.0, 300.0),
            Waypoint(220.0, 300.0),
            Waypoint(220.0, 145.0),
            Waypoint(425.0, 145.0),
            Waypoint(425.0, 295.0),
            Waypoint(650.0, 295.0),
            Waypoint(650.0, 440.0),
            Waypoint(850.0, 440.0),
        ),
        listOf(
            Waypoint(50.0, 300.0),
            Waypoint(220.0, 300.0),
            Waypoint(220.0, 450.0),
            Waypoint(425.0, 450.0),
            Waypoint(425.0, 295.0),
            Waypoint(650.0, 295.0),
            Waypoint(650.0, 440.0),
            Waypoint(850.0, 440.0),
        ),
    );

    /** Neither callers nor a mutable constructor argument can alter a mission during play. */
    val path: List<Waypoint> = Collections.unmodifiableList(path.toList())
    val alternatePath: List<Waypoint>? = alternate?.let { Collections.unmodifiableList(it.toList()) }
    internal val buildSegments = (path.zipWithNext() + alternatePath.orEmpty().zipWithNext()).distinct()
}

/** Only these three explicit modifiers differ; rewards and wave composition remain unchanged. */
enum class Difficulty(
    val title: String,
    val code: String,
    val startingGold: Int,
    val startingLives: Int,
    val healthMultiplier: Double,
) {
    RELAXED("Relaxed", "R", 260, 30, 0.8),
    NORMAL("Normal", "N", 220, 20, 1.0),
    VETERAN("Veteran", "V", 180, 12, 1.25),
}
