package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionMasteryTest {
    @Test
    fun `bastion takes full damage only in both marked windows`() {
        val boss = Enemy(1, BattleMap.CORRIDOR.path, 1.0, 500.0, EnemyType.ARMORED, isBoss = true)
        for ((distance, exposed) in listOf(0.0 to false, 260.0 to true, 500.0 to false, 700.0 to true, 910.0 to false)) {
            boss.update(distance - boss.distanceTravelled)
            assertEquals(exposed, boss.bossExposed)
            val before = boss.health
            boss.takeDamage(40.0, piercesArmor = true)
            assertEquals(if (exposed) 40.0 else 14.0, before - boss.health, 1e-8)
        }
    }

    @Test
    fun `bastion appears once in the corridor finale and a breach loses the mission`() {
        val game = GameSimulation(seed = 583471)
        repeat(game.totalWaves - 1) { clear(game) }
        assertTrue(game.showBossWindows)
        assertTrue("Bastion" in game.nextWaveDescription)
        assertTrue(game.startWave().success)
        repeat(2000) {
            game.enemies.filterNot { it.isBoss }.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
        assertEquals(1, game.enemies.count { it.isBoss })
        assertNotNull(game.activeBoss)
        while (game.state == GameState.RUNNING) game.update()
        assertEquals(GameState.LOST, game.state)
        assertTrue(game.bossBreached)
        assertTrue(game.lives > 0)
        assertFalse(game.waveReports.last().cleared)
    }

    @Test
    fun `quick mission restores its mode and produces a separate local best after six mixed waves`() {
        var stored = ""
        val saves = MissionSaves({ stored }, { stored = it })
        var game = GameSimulation(mode = MissionMode.QUICK)
        assertEquals(330, game.gold)
        assertEquals(6, game.totalWaves)
        assertTrue("Runner" in game.nextWaveDescription)
        clear(game)
        val cannonId = assertNotNull(game.buildTower(TowerType.CANNON, 150.0, 230.0).towerId)
        repeat(2) { assertTrue(game.upgradeTower(cannonId).success) }
        assertTrue(game.specializeCannon(cannonId, CannonSpecialization.RAPID).success)
        saves.save(assertNotNull(game.checkpoint()))
        game = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals(MissionMode.QUICK, game.mode)
        assertEquals(1, game.wave)
        assertEquals(CannonSpecialization.RAPID, game.towers.single().cannonSpecialization)
        repeat(5) { clear(game) }
        assertEquals(GameState.WON, game.state)
        assertEquals(6, game.waveReports.count { it.cleared })
        assertFalse(game.eliteOffered)
        val results = mutableMapOf<String, String>()
        val records = RemixRecords({ results[it].orEmpty() }, { key, value -> results[key] = value })
        assertTrue(records.submit(game))
        assertEquals(6, records.quickBest(Difficulty.NORMAL)?.cleared)
        assertNull(records.quickBest(Difficulty.VETERAN))
        assertNull(records.best(RemixChallenge(BattleMap.CORRIDOR, Difficulty.NORMAL, 583471)))
    }

    @Test
    fun `specializing cannon is permanent and echo keeps the recorded wide splash`() {
        fun cannon() = Tower(100.0, 100.0, 150.0, 34.0, 66, id = 1, type = TowerType.CANNON)
        val wide = cannon()
        assertFalse(wide.specializeCannon(CannonSpecialization.WIDE))
        repeat(2) { wide.upgrade() }
        val baselineInterval = wide.attackIntervalTicks
        assertTrue(wide.specializeCannon(CannonSpecialization.WIDE))
        assertFalse(wide.specializeCannon(CannonSpecialization.RAPID))
        assertTrue(wide.attackIntervalTicks > baselineInterval)
        val restored = cannon().also { it.restore(wide.checkpoint()) }
        assertEquals(wide.attackIntervalTicks, restored.attackIntervalTicks)
        assertEquals(CannonSpecialization.WIDE, restored.cannonSpecialization)

        val rapid = cannon().also { tower -> repeat(2) { tower.upgrade() } }
        assertTrue(rapid.specializeCannon(CannonSpecialization.RAPID))
        assertTrue(rapid.attackIntervalTicks < baselineInterval)
        fun enemy(id: Long, x: Double) = Enemy(id, listOf(Waypoint(x, 100.0), Waypoint(900.0, 100.0)), 0.0, 100.0)
        val shot = assertNotNull(wide.update(1, listOf(enemy(1, 220.0))))
        assertEquals(76.0, shot.areaRadius)
        assertEquals(44.0, assertNotNull(rapid.update(1, listOf(enemy(1, 220.0)))).areaRadius)
        val recorder = WaveEcho()
        recorder.toggleTower(wide.id)
        recorder.startWave(0)
        recorder.record(wide, shot)
        recorder.finishWave()
        recorder.startWave(100)
        val replay = mutableListOf<Shot>()
        assertEquals(1, recorder.replay(101, listOf(enemy(2, 295.0), enemy(3, 297.0)), replay))
        assertEquals(listOf(2L), replay.single().hitEnemyIds)
    }

    @Test
    fun `version one checkpoint restores an unspecialized campaign cannon`() {
        // Version 1 payload with one level-1 Cannon and no pending echoes.
        val encoded = "AAAAAQAIQ09SUklET1IABk5PUk1BTAAAAAABAAhTVEFOREFSRAAAAAAAAAAAAAAAAAAAAABkAAAAFAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAgAAAAABAAAAAAAAAAEABkNBTk5PTkBiwAAAAAAAQGzAAAAAAAAAAAABAAVGSVJTVAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJ81qVo="
        val checkpoint = assertNotNull(MissionSaves({ encoded }).load())
        val game = assertNotNull(GameSimulation.restore(checkpoint))
        assertEquals(MissionMode.CAMPAIGN, game.mode)
        assertEquals(100, game.gold)
        assertEquals(CannonSpecialization.STANDARD, game.towers.single().cannonSpecialization)
    }

    private fun clear(game: GameSimulation) {
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) {
            game.enemies.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
    }
}
