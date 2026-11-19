package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommanderAssaultTest {
    @Test
    fun `escort protection halves even armor piercing damage and ends when its commander falls`() {
        val path = listOf(Waypoint(0.0, 0.0), Waypoint(500.0, 0.0))
        val leader = Enemy(1, path, 36.0, 100.0, isCommander = true)
        val escort = Enemy(2, path, 50.0, 100.0, escortCommander = leader)
        val stranger = Enemy(3, path, 50.0, 100.0)

        escort.takeDamage(30.0, piercesArmor = true)
        stranger.takeDamage(30.0, piercesArmor = true)
        assertEquals(85.0, escort.health)
        assertEquals(70.0, stranger.health)

        leader.takeDamage(100.0)
        assertNull(escort.protectingCommander)
        escort.takeDamage(30.0, piercesArmor = true)
        assertEquals(55.0, escort.health)
    }

    @Test
    fun `escort protection follows route distance and a commander cannot protect from the core`() {
        val path = listOf(Waypoint(0.0, 0.0), Waypoint(500.0, 0.0))
        val leader = Enemy(1, path, 10.0, 100.0, isCommander = true)
        val escort = Enemy(2, path, 10.0, 100.0, escortCommander = leader)
        escort.update(11.0)
        assertNull(escort.protectingCommander)
        leader.update(1.0)
        assertEquals(leader, escort.protectingCommander)

        escort.update(36.0)
        leader.update(49.0)
        assertTrue(leader.hasReachedGoal)
        assertNull(escort.protectingCommander)
    }

    @Test
    fun `seeded double patrol restores between waves and never links escorts to the wrong leader`() {
        val original = GameSimulation(BattleMap.SWITCHBACK, seed = 583471)
        repeat(18) {
            assertTrue(original.startWave().success)
            while (original.state == GameState.RUNNING) {
                original.enemies.forEach { it.takeDamage(10000.0, piercesArmor = true) }
                original.update()
            }
        }
        val checkpoint = assertNotNull(original.checkpoint())
        var payload = ""
        val saves = MissionSaves({ payload }, { payload = it })
        saves.save(checkpoint)
        val game = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertTrue(game.hasCommanders(19))
        assertTrue("2 Commander" in game.nextWaveDescription)
        assertTrue(game.startWave().success)
        while (game.enemies.size < 3) game.update()
        val firstLeader = game.enemies.first()
        assertTrue(firstLeader.isCommander)
        assertTrue(game.enemies.drop(1).all { it.protectingCommander === firstLeader })
        while (game.enemies.size < 23) game.update()

        val leaders = game.enemies.filter { it.isCommander }
        assertEquals(2, leaders.size)
        for (leader in leaders) {
            val escorts = game.enemies.filter { it.id in leader.id + 1..leader.id + 2 }
            assertEquals(2, escorts.size)
            // Old escorts may have outrun their protection, but can never attach to the second leader.
            assertTrue(escorts.all { it.protectingCommander == null || it.protectingCommander === leader })
        }
        assertEquals(leaders.last(), game.enemies.last().protectingCommander)
    }
}
