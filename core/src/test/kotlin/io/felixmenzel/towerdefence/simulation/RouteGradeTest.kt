package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteGradeTest {
    @Test
    fun `marks both tiers in the order enemies cross them`() {
        val terrain = BattleTerrain.forMap(BattleMap.CORRIDOR, seed = null)
        val path = listOf(Waypoint(20.0, 166.0), Waypoint(270.0, 166.0))

        val forward = terrain.routeGrades(path)
        assertEquals(listOf(44f, 87f, 193f, 236f), forward.map { it.x })
        assertEquals(listOf(true, true, false, false), forward.map { it.uphill })
        assertEquals(listOf(1f, 1f, 1f, 1f), forward.map { it.dx })

        val reverse = terrain.routeGrades(path.reversed())
        assertEquals(forward.map { it.x }.reversed(), reverse.map { it.x })
        assertEquals(forward.map { it.uphill }, reverse.map { it.uphill })
        assertEquals(listOf(-1f, -1f, -1f, -1f), reverse.map { it.dx })
    }

    @Test
    fun `touches do not rise and crossings at bends appear once`() {
        val terrain = BattleTerrain.forMap(BattleMap.CORRIDOR, seed = null)
        val tangent = listOf(Waypoint(80.0, 262.0), Waypoint(200.0, 262.0))
        assertTrue(terrain.routeGrades(tangent).isEmpty())
        assertTrue(terrain.routeGrades(listOf(tangent[0], Waypoint(140.0, 262.0), tangent[0])).isEmpty())

        val bend = listOf(Waypoint(44.0, 250.0), Waypoint(44.0, 166.0), Waypoint(65.0, 166.0))
        assertEquals(listOf(RouteGrade(44f, 166f, 1f, 0f, uphill = true)), terrain.routeGrades(bend))
        assertTrue(BattleTerrain.FLAT.routeGrades(bend).isEmpty())
    }
}
