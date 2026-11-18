package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TowerTest {
    private fun enemy(id: Long, x: Double, y: Double = 0.0) =
        Enemy(id, listOf(Waypoint(x, y), Waypoint(1000.0, y)), 10.0, 100.0)

    @Test
    fun `fire prioritizes furthest progress with stable id tie break`() {
        val tower = Tower(0.0, 0.0, 200.0, 10.0, 30L)
        val behind = enemy(1L, 0.0)
        val ahead = enemy(3L, 0.0).apply { update(2.0) }
        val tied = enemy(2L, 0.0).apply { update(2.0) }
        tower.update(1L, listOf(behind, ahead, tied))
        assertEquals(100.0, behind.health)
        assertEquals(100.0, ahead.health)
        assertEquals(90.0, tied.health)
    }

    @Test
    fun `cooldown enforces exact attack interval`() {
        val tower = Tower(0.0, 0.0, 100.0, 10.0, 30L)
        val enemy = enemy(1L, 20.0)
        assertNotNull(tower.update(1L, listOf(enemy)))
        assertNull(tower.update(30L, listOf(enemy)))
        assertNotNull(tower.update(31L, listOf(enemy)))
        assertEquals(80.0, enemy.health)
    }

    @Test
    fun `no target does not consume cooldown and invalid targets are ignored`() {
        val tower = Tower(0.0, 0.0, 100.0, 10.0, 30L)
        val dead = enemy(1L, 10.0).apply { takeDamage(100.0) }
        val escaped = Enemy(2L, listOf(Waypoint(0.0, 0.0)), 10.0, 100.0)
        val far = enemy(3L, 101.0)
        assertNull(tower.update(1L, listOf(dead, escaped, far)))
        val atBoundary = enemy(4L, 100.0)
        assertNotNull(tower.update(2L, listOf(atBoundary)))
        assertEquals(90.0, atBoundary.health)
        assertEquals(100.0, escaped.health)
        assertEquals(100.0, far.health)
    }

    @Test
    fun `cannon damages nearby group without damaging remote enemies`() {
        val tower = Tower(0.0, 0.0, 100.0, 20.0, 30L, type = TowerType.CANNON)
        val target = enemy(1L, 10.0)
        val nearby = enemy(2L, 40.0)
        val remote = enemy(3L, 90.0)
        val shot = tower.update(1L, listOf(target, nearby, remote))
        assertNotNull(shot)
        assertEquals(TowerType.CANNON, shot.type)
        assertEquals(listOf(target.id, nearby.id), shot.hitEnemyIds)
        assertEquals(80.0, target.health)
        assertEquals(80.0, nearby.health)
        assertEquals(100.0, remote.health)
    }

    @Test
    fun `frost reduces movement without slowing unrelated enemies`() {
        val tower = Tower(0.0, 0.0, 100.0, 5.0, 30L, type = TowerType.FROST)
        val target = enemy(1L, 10.0)
        val untouched = enemy(2L, 20.0)
        val shot = assertNotNull(tower.update(1L, listOf(target, untouched)))
        assertEquals(listOf(target.id), shot.hitEnemyIds)
        assertTrue(target.slowFactor < 1.0)
        assertEquals(1.0, untouched.slowFactor)
        assertEquals(95.0, target.health)
    }

    @Test
    fun `upgrades improve stats and resale tracks investment`() {
        val tower = Tower(0.0, 0.0, 100.0, 10.0, 30L)
        val range = tower.range
        val damage = tower.damage
        val interval = tower.attackIntervalTicks
        val resale = tower.sellValue
        tower.upgrade()
        assertEquals(2, tower.level)
        assertTrue(tower.range > range)
        assertTrue(tower.damage > damage)
        assertTrue(tower.attackIntervalTicks < interval)
        assertTrue(tower.sellValue > resale)
        tower.upgrade()
        assertEquals(3, tower.level)
        assertNull(tower.upgradeCost)
    }
}
