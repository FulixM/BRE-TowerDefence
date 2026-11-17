package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyTest {
    private fun enemy(path: List<Waypoint> = listOf(Waypoint(0.0, 0.0), Waypoint(100.0, 0.0))) =
        Enemy(1L, path, speed = 10.0, maxHealth = 100.0)

    @Test
    fun `movement follows vertical segments without drifting right`() {
        val enemy = enemy(listOf(Waypoint(5.0, 0.0), Waypoint(5.0, 100.0)))
        enemy.update(1.0)
        assertEquals(5.0, enemy.x, 1e-9)
        assertEquals(10.0, enemy.y, 1e-9)
    }

    @Test
    fun `movement budget is preserved across corners and repeated waypoints`() {
        val enemy = enemy(listOf(Waypoint(0.0, 0.0), Waypoint(0.0, 0.0), Waypoint(5.0, 0.0), Waypoint(5.0, 20.0)))
        enemy.update(1.0)
        assertEquals(5.0, enemy.x, 1e-9)
        assertEquals(5.0, enemy.y, 1e-9)
        assertEquals(10.0, enemy.distanceTravelled, 1e-9)
        assertFalse(enemy.hasReachedGoal)
    }

    @Test
    fun `movement stops exactly at the final waypoint`() {
        val enemy = enemy()
        enemy.update(100.0)
        assertEquals(100.0, enemy.x)
        assertEquals(0.0, enemy.y)
        assertTrue(enemy.hasReachedGoal)
        enemy.update(1.0)
        assertEquals(100.0, enemy.x)
    }

    @Test
    fun `lethal damage clamps health and dead enemies do not move`() {
        val enemy = enemy()
        enemy.takeDamage(120.0)
        enemy.update(1.0)
        assertEquals(0.0, enemy.health)
        assertFalse(enemy.isAlive)
        assertEquals(0.0, enemy.x)
    }

    @Test
    fun `interpolation uses previous and current positions`() {
        val enemy = enemy()
        enemy.update(1.0)
        assertEquals(0.0, enemy.interpolatedX(0.0))
        assertEquals(5.0, enemy.interpolatedX(0.5))
        assertEquals(10.0, enemy.interpolatedX(1.0))
    }

    @Test
    fun `slow expires within a movement step and does not multiply`() {
        val enemy = enemy()
        enemy.applySlow(0.5, 0.5)
        enemy.applySlow(0.5, 0.5)
        enemy.update(1.0)
        assertEquals(7.5, enemy.x, 1e-9)
        assertEquals(1.0, enemy.slowFactor)
        enemy.update(1.0)
        assertEquals(17.5, enemy.x, 1e-9)
    }

    @Test
    fun `path ownership prevents caller mutation from changing movement`() {
        val path = mutableListOf(Waypoint(0.0, 0.0), Waypoint(100.0, 0.0))
        val enemy = enemy(path)
        path.clear()
        enemy.update(1.0)
        assertEquals(10.0, enemy.x)
    }

    @Test
    fun `invalid input cannot poison enemy state`() {
        assertFailsWith<IllegalArgumentException> { enemy(emptyList()) }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertFailsWith<IllegalArgumentException> { Enemy(1L, listOf(Waypoint(0.0, 0.0)), value, 100.0) }
            assertFailsWith<IllegalArgumentException> { enemy().takeDamage(value) }
            assertFailsWith<IllegalArgumentException> { enemy().update(value) }
        }
        assertFailsWith<IllegalArgumentException> { Waypoint(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { enemy().applySlow(0.0, 1.0) }
    }
}
