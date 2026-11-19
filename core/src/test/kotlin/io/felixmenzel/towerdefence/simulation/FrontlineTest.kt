package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrontlineTest {
    @Test
    fun `front switches twice and rear guards take the old route`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS, Difficulty.RELAXED, mode = MissionMode.FRONTLINE)
        assertEquals(8, game.totalWaves)
        for (wave in 1..6) {
            val switches = wave == 3 || wave == 6
            assertEquals(switches, game.hasRearGuard)
            assertEquals(wave in 3..5, game.useUpperRoute)
            assertFalse(game.switchRoute().success)
            assertTrue(game.startWave().success)
            if (switches) {
                game.update()
                val first = game.enemies.single()
                first.update(4.6)
                assertEquals(game.useUpperRoute, first.y > 300.0)
                while (game.remainingToSpawn > 0) game.update()
                val rear = game.enemies.last()
                rear.update(4.6)
                assertEquals(!game.useUpperRoute, rear.y > 300.0)
            }
            while (game.state == GameState.RUNNING) {
                game.enemies.forEach { it.takeDamage(10000.0) }
                game.update()
            }
            assertEquals(GameState.PREPARING, game.state)
        }
    }

    @Test
    fun `one move preserves tower identity and lets a delayed echo replay from its old square after loading`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS, mode = MissionMode.FRONTLINE)
        val tower = assertNotNull(game.buildTower(TowerType.RAPID, 275.0, 240.0).towerId)
        assertTrue(game.upgradeTower(tower).success)
        assertTrue(game.selectEchoTower(tower).success)
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) game.update()

        val before = assertNotNull(game.echo.firstRecordedDelay)
        val recordedShot = assertNotNull(game.echo.nextShot(preparing = true))
        assertFalse(game.moveTower(tower, 220.0, 450.0).success)
        assertTrue(game.moveTower(tower, 275.0, 365.0).success)
        assertEquals(tower, game.towers.single().id)
        assertEquals(2, game.towers.single().level)
        assertFalse(game.moveTower(tower, 275.0, 240.0).success)
        assertTrue(game.toggleEchoDelay().success)
        assertEquals(before + 3 * GameSimulation.UPS, game.echoPreviewTicks)
        var encoded = ""
        val saves = MissionSaves({ encoded }, { encoded = it })
        saves.save(assertNotNull(game.checkpoint()))
        val resumed = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals(game.damageByTowerType(true), resumed.damageByTowerType(true))
        assertEquals(game.damageByTowerType(false), resumed.damageByTowerType(false))
        assertFalse(resumed.canMoveTower)
        assertTrue(resumed.echoDelayed)
        val due = assertNotNull(resumed.echoPreviewTicks)
        assertTrue(resumed.startWave().success)
        assertTrue(resumed.damageByTowerType(false).isEmpty())
        repeat(due.toInt() - 1) { resumed.update() }
        assertFalse(resumed.shots.any { it.isEcho })
        resumed.update()
        val replayed = resumed.shots.single { it.isEcho }
        assertEquals(recordedShot.fromX, replayed.fromX)
        assertEquals(recordedShot.fromY, replayed.fromY)
        assertEquals(false, resumed.echoDelayed)
    }

    @Test
    fun `damage includes only depleted resources and attributes poison and replay to their type`() {
        val target = Enemy(1, listOf(Waypoint(12.0, 20.0), Waypoint(200.0, 20.0)), 0.0, 100.0, EnemyType.SHIELDED)
        val amounts = mutableMapOf<TowerType, Double>()
        target.onTowerDamage = { type, amount -> amounts[type] = (amounts[type] ?: 0.0) + amount }
        applyTowerHit(TowerType.SUNDER, target, 50.0)
        applyTowerHit(TowerType.VENOM, target, 6.0)
        target.update(1.0)
        val echo = WaveEcho()
        val tower = Tower(10.0, 20.0, 100.0, 1000.0, 1L, id = 1, type = TowerType.RAIL)
        echo.toggleTower(tower.id)
        echo.startWave(1)
        echo.record(tower, Shot(10.0, 20.0, 12.0, 20.0, TowerType.RAIL, 2, emptyList()))
        echo.startWave(3)
        echo.replay(4, listOf(target), mutableListOf())
        assertEquals(80.0, amounts[TowerType.SUNDER])
        assertEquals(12.0, amounts[TowerType.VENOM])
        assertEquals(53.0, amounts[TowerType.RAIL])
        assertEquals(0.0, target.health)
    }
}
