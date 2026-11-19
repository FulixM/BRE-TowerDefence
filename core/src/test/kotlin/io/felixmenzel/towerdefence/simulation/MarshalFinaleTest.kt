package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarshalFinaleTest {
    @Test
    fun `both route windows bypass protection but one surviving relay can still restore it`() {
        val route = BattleMap.CORRIDOR.path
        val relays = List(2) { Enemy(it + 1L, route, 0.0, 100.0, isMarshalRelay = true) }
        val marshal = Enemy(3L, route, 1.0, 1000.0, EnemyType.ARMORED, isBoss = true, marshalRelays = relays)

        fun expectDamageAt(distance: Double, exposed: Boolean) {
            marshal.update(distance - marshal.distanceTravelled)
            assertEquals(exposed, marshal.bossExposed)
            val before = marshal.health
            marshal.takeDamage(100.0, piercesArmor = true)
            assertEquals(if (exposed) 100.0 else 28.0, before - marshal.health, 1e-9)
        }

        expectDamageAt(0.0, false)
        expectDamageAt(340.0, true)
        relays.first().takeDamage(100.0)
        expectDamageAt(500.0, false)
        expectDamageAt(800.0, true)
        expectDamageAt(900.0, false)
        relays.last().takeDamage(100.0)
        expectDamageAt(910.0, true)
        expectDamageAt(1100.0, true)
    }

    @Test
    fun `seeded finale keeps its planned roster and restores the formation after saving wave nineteen`() {
        val seed = 583471
        val base = Waves.plan(Marshal.WAVE, seed)
        val formation = base.withMarshal()
        assertEquals(base.enemies.groupingBy { it }.eachCount(), formation.enemies.groupingBy { it }.eachCount())
        assertEquals(base.intervals, formation.intervals)
        assertFalse(GameSimulation(BattleMap.SWITCHBACK, seed = seed).waveDescription(Marshal.WAVE).contains("Marshal"))

        val original = GameSimulation(seed = seed)
        prepareFinalWave(original)
        val resumed = assertNotNull(GameSimulation.restore(assertNotNull(original.checkpoint())))
        assertEquals(Marshal.WAVE, resumed.bossWindowWave)
        assertTrue("Marshal + 2 Relays" in resumed.nextWaveDescription)
        assertTrue(resumed.startWave().success)
        while (resumed.activeBoss == null) resumed.update()
        val marshal = assertNotNull(resumed.activeBoss)
        assertTrue(marshal.isMarshal)
        assertFalse(marshal.bossExposed)
        assertEquals(2, marshal.supportingRelays.count())

        clearLiveWave(resumed)
        assertEquals(GameState.WON, resumed.state)
        assertEquals(Marshal.WAVE, resumed.wave)
        assertNull(resumed.bossWindowWave)
    }

    @Test
    fun `marshal reaching the core loses the mission even when lives remain`() {
        val game = GameSimulation()
        prepareFinalWave(game)
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) {
            game.enemies.filterNot { it.isMarshal }.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
        assertEquals(GameState.LOST, game.state)
        assertTrue(game.bossBreached)
        assertTrue(game.lives > 0)
        assertFalse(game.waveReports.last().cleared)
    }

    private fun prepareFinalWave(game: GameSimulation) {
        repeat(Marshal.WAVE - 1) {
            assertTrue(game.startWave().success)
            clearLiveWave(game)
        }
    }

    private fun clearLiveWave(game: GameSimulation) {
        while (game.state == GameState.RUNNING) {
            game.enemies.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
    }
}
