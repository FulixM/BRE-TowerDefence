package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RailWorkshopTest {
    @Test
    fun `only a marshal campaign victory unlocks the module and the reward is permanent`() {
        val profile = FieldProfile()
        val quick = GameSimulation(mode = MissionMode.QUICK)
        val corridor = GameSimulation()
        val switchback = GameSimulation(BattleMap.SWITCHBACK)
        assertFalse(profile.relayHunterAfterMission(corridor))
        win(quick)
        assertFalse(profile.relayHunterAfterMission(quick))
        win(switchback)
        assertFalse(profile.relayHunterAfterMission(switchback))
        win(corridor)
        assertTrue(profile.applyRelayHunterUnlock(profile.relayHunterAfterMission(corridor)))
        assertFalse(profile.applyRelayHunterUnlock(profile.relayHunterAfterMission(corridor)))
        val reloaded = FieldProfile(relayHunterUnlocked = profile.relayHunterUnlocked)
        corridor.restart()
        assertTrue(reloaded.relayHunterAfterMission(corridor))
    }

    @Test
    fun `relay hunter boosts shield absorption and relays but does not improve unshielded damage`() {
        val route = listOf(Waypoint(60.0, 0.0), Waypoint(1000.0, 0.0))
        val regular = Enemy(1, route, 0.0, 100.0, EnemyType.SHIELDED)
        val relay = Enemy(2, route, 0.0, 100.0, EnemyType.SHIELDED, isMarshalRelay = true)
        val plated = Enemy(3, route, 0.0, 100.0, EnemyType.PLATED)
        val tower = Tower(15.0, 0.0, 150.0, 30.0, 115, type = TowerType.RAIL, railModule = RailModule.RELAY_HUNTER)
        assertEquals(143L, tower.attackIntervalTicks)
        assertNotNull(tower.update(1, listOf(regular)))
        assertEquals(0.0, regular.shield)
        assertEquals(92.5, regular.health)
        assertNotNull(tower.update(144, listOf(relay)))
        assertEquals(0.0, relay.shield)
        assertEquals(85.0, relay.health)
        assertNotNull(tower.update(287, listOf(plated)))
        assertEquals(70.0, plated.health)
    }

    @Test
    fun `loadout and recorded shots keep the module through a checkpoint and its echo`() {
        val original = GameSimulation(railModule = RailModule.RELAY_HUNTER)
        val id = assertNotNull(original.buildTower(TowerType.RAIL, 150.0, 230.0).towerId)
        assertTrue(original.selectEchoTower(id).success)
        clearNextWave(original)

        var encoded = ""
        val saves = MissionSaves({ encoded }, { encoded = it })
        saves.save(assertNotNull(original.checkpoint()))
        val snapshot = assertNotNull(saves.load())
        val restored = assertNotNull(GameSimulation.restore(snapshot))
        assertEquals(RailModule.RELAY_HUNTER, restored.railModule)
        assertEquals(143L, restored.towers.single().attackIntervalTicks)
        val recorded = snapshot.echo.impacts.first()
        assertEquals(RailModule.RELAY_HUNTER, recorded.shot.railModule)
        val impact = Waypoint(recorded.shot.toX, recorded.shot.toY)
        val relay = Enemy(1, listOf(impact, Waypoint(impact.x + 10, impact.y)), 0.0, 100.0,
            EnemyType.SHIELDED, isMarshalRelay = true)
        restored.echo.startWave(500)
        val shots = mutableListOf<Shot>()
        assertEquals(1, restored.echo.replay(500 + recorded.delay, listOf(relay), shots))
        assertEquals(0.0, relay.shield)
        assertEquals(65.0, relay.health)
        assertTrue(shots.single().isEcho)
    }

    @Test
    fun `version two rail checkpoint loads without a module`() {
        // Version 2 snapshot with a standard Rail bought before wave 1.
        val payload = "AAAAAgAIQ09SUklET1IABk5PUk1BTAAAAAABAAhTVEFOREFSRAAAAAAAAAAAAAAAAAAAAABQAAAAFAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAgAAAAABAAAAAAAAAAEABFJBSUxAYsAAAAAAAEBswAAAAAAAAAAAAQAFRklSU1QAAAAAAAAAAAAIU1RBTkRBUkQAAAAAAAAAAAAAAAAAAAAAAAhDQU1QQUlHThZoYPE="
        val snapshot = assertNotNull(MissionSaves({ payload }).load())
        val game = assertNotNull(GameSimulation.restore(snapshot))
        assertEquals(RailModule.STANDARD, game.railModule)
        assertEquals(115L, game.towers.single().attackIntervalTicks)
    }

    private fun win(game: GameSimulation) {
        repeat(game.totalWaves) { clearNextWave(game) }
        assertEquals(GameState.WON, game.state)
    }

    private fun clearNextWave(game: GameSimulation) {
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) {
            game.enemies.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
    }
}
