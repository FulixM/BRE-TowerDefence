package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSimulationTest {
    @Test
    fun `preparation never spawns enemies until player starts a wave`() {
        val game = GameSimulation()
        repeat(600) { game.update() }
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(0, game.wave)
        assertTrue(game.enemies.isEmpty())
        assertEquals(GameSimulation.START_GOLD, game.gold)
        assertTrue(game.startWave().success)
        assertFalse(game.startWave().success)
        assertEquals(1, game.wave)
        game.update()
        assertEquals(1, game.enemies.size)
    }

    @Test
    fun `illegal construction never spends gold or creates towers`() {
        val game = GameSimulation()
        val invalid = listOf(
            Double.NaN to 80.0, Double.POSITIVE_INFINITY to 80.0,
            0.0 to 80.0, 901.0 to 80.0, 80.0 to 0.0, 80.0 to 561.0,
            100.0 to 160.0, 300.0 to 250.0, 450.0 to 350.0,
            600.0 to 220.0, 800.0 to 100.0,
        )
        for ((x, y) in invalid) {
            assertNotNull(game.placementError(x, y), "position=$x,$y")
            assertFalse(game.buildTower(TowerType.RAPID, x, y).success)
        }
        assertEquals(GameSimulation.START_GOLD, game.gold)
        assertTrue(game.towers.isEmpty())
    }

    @Test
    fun `construction respects occupancy and budget without partial mutations`() {
        val game = GameSimulation()
        assertNull(game.placementError(150.0, 240.0))
        assertTrue(game.buildTower(TowerType.CANNON, 150.0, 240.0).success)
        val remaining = game.gold
        assertFalse(game.buildTower(TowerType.RAPID, 160.0, 240.0).success)
        assertEquals(remaining, game.gold)
        assertTrue(game.buildTower(TowerType.RAPID, 450.0, 250.0).success)
        val afterSecondBuild = game.gold
        assertFalse(game.buildTower(TowerType.CANNON, 750.0, 250.0).success)
        assertEquals(afterSecondBuild, game.gold)
        assertEquals(2, game.towers.size)
        assertEquals(2, game.towers.map { it.id }.distinct().size)
    }

    @Test
    fun `upgrade and sale charge and refund exactly once`() {
        val game = GameSimulation()
        val id = assertNotNull(game.buildTower(TowerType.RAPID, 150.0, 240.0).towerId)
        val tower = game.towers.single()
        val upgradeCost = assertNotNull(tower.upgradeCost)
        val beforeUpgrade = game.gold
        assertTrue(game.upgradeTower(id).success)
        assertEquals(beforeUpgrade - upgradeCost, game.gold)
        assertEquals(2, tower.level)
        val beforeSale = game.gold
        val refund = tower.sellValue
        assertTrue(game.sellTower(id).success)
        assertEquals(beforeSale + refund, game.gold)
        assertFalse(game.sellTower(id).success)
        assertFalse(game.upgradeTower(id).success)
        assertEquals(beforeSale + refund, game.gold)
        assertTrue(game.towers.isEmpty())
    }

    @Test
    fun `unaffordable upgrade keeps level and gold unchanged`() {
        val game = GameSimulation()
        val id = assertNotNull(game.buildTower(TowerType.CANNON, 150.0, 240.0).towerId)
        assertTrue(game.buildTower(TowerType.RAPID, 450.0, 250.0).success)
        val before = game.gold
        assertFalse(game.upgradeTower(id).success)
        assertEquals(1, game.towers.first().level)
        assertEquals(before, game.gold)
    }

    @Test
    fun `kills pay bounty once and wave completion pays one reward`() {
        val game = GameSimulation()
        assertTrue(game.startWave().success)
        val startingGold = game.gold
        game.update()
        val first = game.enemies.single()
        val bounty = first.bounty
        first.takeDamage(first.health)
        game.update()
        assertEquals(startingGold + bounty, game.gold)
        assertTrue(game.enemies.isEmpty())
        repeat(20) { game.update() }
        assertEquals(startingGold + bounty, game.gold)

        val additionalBounty = clearCurrentWave(game)
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(startingGold + bounty + additionalBounty + game.waveReward, game.gold)
        val completedGold = game.gold
        repeat(100) { game.update() }
        assertEquals(completedGold, game.gold)
    }

    @Test
    fun `escaped enemies cost one life each and never pay kill bounty`() {
        val game = GameSimulation()
        assertTrue(game.startWave().success)
        repeat(6000) { if (game.state == GameState.RUNNING) game.update() }
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(GameSimulation.START_LIVES - GameSimulation.enemyCount(1), game.lives)
        assertEquals(GameSimulation.START_GOLD + game.waveReward, game.gold)
        assertTrue(game.enemies.isEmpty())
        val lives = game.lives
        repeat(100) { game.update() }
        assertEquals(lives, game.lives)
    }

    @Test
    fun `clearing final wave wins and terminal commands cannot change economy`() {
        val game = GameSimulation()
        repeat(game.totalWaves) { expectedWave ->
            assertTrue(game.startWave().success)
            assertEquals(expectedWave + 1, game.wave)
            clearCurrentWave(game)
        }
        assertEquals(GameState.WON, game.state)
        val gold = game.gold
        assertFalse(game.startWave().success)
        assertFalse(game.buildTower(TowerType.RAPID, 150.0, 240.0).success)
        assertFalse(game.upgradeTower(1L).success)
        assertFalse(game.sellTower(1L).success)
        repeat(100) { game.update() }
        assertEquals(gold, game.gold)
        assertEquals(GameState.WON, game.state)
        assertEquals(10, game.wave)
    }

    @Test
    fun `restart removes all progress and allows a fresh mission`() {
        val game = GameSimulation()
        game.buildTower(TowerType.RAPID, 150.0, 240.0)
        game.startWave()
        repeat(240) { game.update() }
        game.restart()
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(GameSimulation.START_GOLD, game.gold)
        assertEquals(GameSimulation.START_LIVES, game.lives)
        assertEquals(0L, game.tick)
        assertEquals(0, game.wave)
        assertEquals(0, game.remainingToSpawn)
        assertTrue(game.enemies.isEmpty())
        assertTrue(game.towers.isEmpty())
        assertTrue(game.shots.isEmpty())
        assertEquals(1L, game.buildTower(TowerType.RAPID, 150.0, 240.0).towerId)
        assertTrue(game.startWave().success)
        game.update()
        assertEquals(1L, game.enemies.single().id)
    }

    @Test
    fun `identical command sequences produce identical mission states`() {
        fun run(): List<Any> {
            val game = GameSimulation()
            game.buildTower(TowerType.RAPID, 220.0, 240.0)
            game.buildTower(TowerType.CANNON, 450.0, 250.0)
            game.startWave()
            repeat(1200) { game.update() }
            return listOf(game.tick, game.wave, game.gold, game.lives, game.state,
                game.enemies.map { listOf(it.id, it.x, it.y, it.health) },
                game.towers.map { listOf(it.id, it.level) }, game.shots.toList())
        }
        assertEquals(run(), run())
    }

    @Test
    fun `zero lives ends the mission and stops damage spawning and rewards`() {
        val game = GameSimulation()
        repeat(30000) {
            if (game.state == GameState.PREPARING) game.startWave()
            if (game.state != GameState.LOST) game.update()
        }
        assertEquals(GameState.LOST, game.state)
        assertEquals(0, game.lives)
        assertEquals(0, game.remainingToSpawn)
        assertFalse(game.startWave().success)
        assertFalse(game.buildTower(TowerType.RAPID, 150.0, 240.0).success)
        val gold = game.gold
        val enemyIds = game.enemies.map { it.id }
        repeat(1000) { game.update() }
        assertEquals(gold, game.gold)
        assertEquals(0, game.lives)
        assertEquals(enemyIds, game.enemies.map { it.id })
        game.restart()
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(GameSimulation.START_LIVES, game.lives)
    }

    @Test
    fun `combat histories and enemy cleanup remain bounded during play`() {
        val game = GameSimulation()
        game.buildTower(TowerType.RAPID, 220.0, 240.0)
        game.buildTower(TowerType.CANNON, 450.0, 250.0)
        game.startWave()
        var sawShot = false
        repeat(6000) {
            game.update()
            sawShot = sawShot || game.shots.isNotEmpty()
            assertTrue(game.shots.all { game.tick - it.tick <= GameSimulation.SHOT_LIFETIME_TICKS })
            assertTrue(game.enemies.all { it.isAlive && !it.hasReachedGoal })
        }
        assertTrue(sawShot)
        assertTrue(game.shots.isEmpty())
    }

    /** Synthetic kills isolate state transitions and accounting from balance policy. */
    private fun clearCurrentWave(game: GameSimulation): Int {
        var bounty = 0
        repeat(10000) {
            if (game.state != GameState.RUNNING) return bounty
            for (enemy in game.enemies) {
                if (enemy.isAlive) {
                    bounty += enemy.bounty
                    enemy.takeDamage(enemy.health)
                }
            }
            game.update()
        }
        error("Wave ${game.wave} did not finish within the simulation budget")
    }
}
