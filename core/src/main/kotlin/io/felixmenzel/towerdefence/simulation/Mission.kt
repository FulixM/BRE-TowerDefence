package io.felixmenzel.towerdefence.simulation

import java.util.Collections

/** Authored routes in the existing 900 x 560 world; coordinates denote enemy centres. */
enum class BattleMap(val title: String, path: List<Waypoint>) {
    // Preserve the first mission's route exactly, so its established balance remains reproducible.
    CORRIDOR(
        "The Corridor",
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
        "Switchback",
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
    );

    /** Neither callers nor a mutable constructor argument can alter a mission during play. */
    val path: List<Waypoint> = Collections.unmodifiableList(path.toList())
}

/** Only these three explicit modifiers differ; rewards and wave composition remain unchanged. */
enum class Difficulty(
    val title: String,
    val startingGold: Int,
    val startingLives: Int,
    val healthMultiplier: Double,
) {
    RELAXED("Relaxed", 260, 30, 0.8),
    NORMAL("Normal", 220, 20, 1.0),
    VETERAN("Veteran", 180, 12, 1.25),
}
