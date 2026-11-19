package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverdriveTest {
    @Test
    fun `boost halves the pending reload and the intervals of boosted shots`() {
        val tower = Tower(10.0, 10.0, 120.0, 10.0, 20L)
        val enemy = Enemy(1L, listOf(Waypoint(12.0, 10.0), Waypoint(80.0, 10.0)), 0.0, 100.0)
        val enemies = listOf(enemy)

        assertNotNull(tower.update(10L, enemies))
        tower.beginOverdrive(14L)
        assertNull(tower.update(21L, enemies, overdrive = true))
        assertNotNull(tower.update(22L, enemies, overdrive = true))
        assertNull(tower.update(31L, enemies, overdrive = true))
        assertNotNull(tower.update(32L, enemies))
        assertNull(tower.update(42L, enemies))
        assertNotNull(tower.update(52L, enemies))
        assertEquals(60.0, enemy.health)
    }

    @Test
    fun `a classic wave grants one timed charge and the next wave recharges it`() {
        val game = GameSimulation()
        val first = assertNotNull(game.buildTower(TowerType.RAPID, 140.0, 208.0).towerId)
        val second = assertNotNull(game.buildTower(TowerType.RAPID, 450.0, 250.0).towerId)
        assertFalse(game.activateOverdrive(first).success)
        assertTrue(game.startWave().success)
        assertFalse(game.activateOverdrive(99L).success)
        assertTrue(game.activateOverdrive(first).success)
        assertEquals(first, game.overdriveTowerId)
        assertFalse(game.activateOverdrive(second).success)

        val duration = GameSimulation.UPS * GameSimulation.OVERDRIVE_SECONDS
        assertEquals(duration.toLong(), game.overdriveTicksLeft)
        repeat(duration - 1) { game.update() }
        assertEquals(first, game.overdriveTowerId)
        game.update()
        assertEquals(0L, game.overdriveTicksLeft)
        assertNull(game.overdriveTowerId)
        assertFalse(game.canActivateOverdrive)
        while (game.state == GameState.RUNNING) game.update()
        assertTrue(game.startWave().success)
        assertTrue(game.activateOverdrive(second).success)
        assertTrue(game.sellTower(second).success)
        assertNull(game.overdriveTowerId)
        assertFalse(game.activateOverdrive(first).success)
    }

    @Test
    fun `remix seeds and Quick preserve their existing rules`() {
        for (game in listOf(GameSimulation(seed = 583471), GameSimulation(mode = MissionMode.QUICK))) {
            val tower = assertNotNull(game.buildTower(TowerType.RAPID, 140.0, 208.0).towerId)
            assertTrue(game.startWave().success)
            assertFalse(game.canActivateOverdrive)
            assertFalse(game.activateOverdrive(tower).success)
        }
    }
}
