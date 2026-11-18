package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MissionOptionsTest {
    @Test
    fun `default mission preserves original route budget and enemy health`() {
        val default = GameSimulation()
        val explicit = GameSimulation(BattleMap.CORRIDOR, Difficulty.NORMAL)
        assertEquals(BattleMap.CORRIDOR, default.battleMap)
        assertEquals(Difficulty.NORMAL, default.difficulty)
        assertEquals(GameSimulation.START_GOLD, default.gold)
        assertEquals(GameSimulation.START_LIVES, default.lives)
        assertEquals(explicit.path, default.path)
        default.startWave()
        explicit.startWave()
        repeat(120) { default.update(); explicit.update() }
        assertEquals(explicit.enemies.map { listOf(it.x, it.y, it.health) },
            default.enemies.map { listOf(it.x, it.y, it.health) })
        assertEquals(explicit.gold, default.gold)
    }

    @Test
    fun `selected route controls construction and every path segment stays protected`() {
        val corridor = GameSimulation(BattleMap.CORRIDOR)
        val switchback = GameSimulation(BattleMap.SWITCHBACK)
        assertNotNull(corridor.placementError(100.0, 160.0))
        assertNull(switchback.placementError(100.0, 160.0))
        assertNull(corridor.placementError(100.0, 410.0))
        assertNotNull(switchback.placementError(100.0, 410.0))
        for (map in BattleMap.entries) {
            val game = GameSimulation(map)
            for ((start, end) in game.path.zipWithNext()) {
                val x = (start.x + end.x) / 2.0
                val y = (start.y + end.y) / 2.0
                assertFalse(game.buildTower(TowerType.RAPID, x, y).success, "${map.name} segment $start->$end")
            }
            assertEquals(game.difficulty.startingGold, game.gold)
            assertTrue(game.towers.isEmpty())
        }
    }

    @Test
    fun `difficulty changes only stated starting resources and enemy health`() {
        val firstEnemies = Difficulty.entries.associateWith { difficulty ->
            val game = GameSimulation(BattleMap.SWITCHBACK, difficulty)
            assertEquals(difficulty.startingGold, game.gold)
            assertEquals(difficulty.startingLives, game.lives)
            game.startWave()
            game.update()
            game.enemies.single()
        }
        val normal = firstEnemies.getValue(Difficulty.NORMAL)
        for ((difficulty, enemy) in firstEnemies) {
            assertEquals(normal.maxHealth * difficulty.healthMultiplier, enemy.maxHealth, 1e-9)
            assertEquals(normal.speed, enemy.speed)
            assertEquals(normal.bounty, enemy.bounty)
            assertEquals(normal.type, enemy.type)
        }
    }

    @Test
    fun `restart retains mission selection while clearing all progress and counters`() {
        val game = GameSimulation(BattleMap.SWITCHBACK, Difficulty.VETERAN)
        game.buildTower(TowerType.RAPID, 100.0, 160.0)
        game.startWave()
        game.update()
        game.enemies.single().takeDamage(10000.0)
        game.update()
        assertEquals(1, game.kills)
        game.restart()
        assertEquals(BattleMap.SWITCHBACK, game.battleMap)
        assertEquals(Difficulty.VETERAN, game.difficulty)
        assertEquals(BattleMap.SWITCHBACK.path, game.path)
        assertEquals(Difficulty.VETERAN.startingGold, game.gold)
        assertEquals(Difficulty.VETERAN.startingLives, game.lives)
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(0, game.wave)
        assertEquals(0L, game.tick)
        assertEquals(0, game.kills)
        assertEquals(0, game.leakedEnemies)
        assertTrue(game.towers.isEmpty() && game.enemies.isEmpty() && game.shots.isEmpty())
        game.startWave()
        game.update()
        assertEquals(1L, game.enemies.single().id)
        assertTrue(game.enemies.single().y > 400.0)
    }

    @Test
    fun `kill and leak counters book each resolved enemy exactly once`() {
        val game = GameSimulation()
        game.startWave()
        game.update()
        game.enemies.single().takeDamage(10000.0)
        game.update()
        assertEquals(1, game.kills)
        repeat(6000) { game.update() }
        assertEquals(GameState.PREPARING, game.state)
        assertEquals(1, game.kills)
        assertEquals(GameSimulation.enemyCount(1) - 1, game.leakedEnemies)
        assertEquals(GameSimulation.START_LIVES - game.leakedEnemies, game.lives)
        repeat(100) { game.update() }
        assertEquals(1, game.kills)
        assertEquals(GameSimulation.enemyCount(1) - 1, game.leakedEnemies)
    }

    @Test
    fun `first strongest and nearest select distinct intended targets`() {
        val tower = Tower(0.0, 0.0, 200.0, 10.0, 30L)
        val nearest = enemy(1L, 10.0, 0.0, 100.0)
        val first = enemy(2L, 0.0, 40.0, 100.0)
        val strongest = enemy(3L, 60.0, 0.0, 200.0)
        val enemies = listOf(strongest, nearest, first)
        assertSame(first, tower.selectTarget(enemies))
        tower.cycleTargetPriority()
        assertEquals(TargetPriority.STRONGEST, tower.targetPriority)
        assertSame(strongest, tower.selectTarget(enemies))
        tower.cycleTargetPriority()
        assertEquals(TargetPriority.NEAREST, tower.targetPriority)
        assertSame(nearest, tower.selectTarget(enemies))
        tower.cycleTargetPriority()
        assertEquals(TargetPriority.FIRST, tower.targetPriority)
    }

    @Test
    fun `strongest uses current health and all priorities use stable id tie breaks`() {
        val tower = Tower(0.0, 0.0, 200.0, 10.0, 30L)
        val wounded = enemy(1L, 20.0, 0.0, 1000.0).apply { takeDamage(950.0) }
        val healthy = enemy(2L, 20.0, 0.0, 100.0)
        tower.cycleTargetPriority()
        assertSame(healthy, tower.selectTarget(listOf(wounded, healthy)))
        val lowId = enemy(3L, 20.0, 0.0, 100.0)
        val highId = enemy(4L, 20.0, 0.0, 100.0)
        for (priority in TargetPriority.entries) {
            while (tower.targetPriority != priority) tower.cycleTargetPriority()
            assertSame(lowId, tower.selectTarget(listOf(highId, lowId)))
        }
    }

    @Test
    fun `every priority rejects dead escaped and out of range targets`() {
        val tower = Tower(0.0, 0.0, 100.0, 10.0, 30L)
        val dead = enemy(1L, 10.0, 0.0, 100.0).apply { takeDamage(100.0) }
        val escaped = Enemy(2L, listOf(Waypoint(10.0, 0.0)), 10.0, 1000.0)
        val far = enemy(3L, 101.0, 0.0, 2000.0)
        repeat(TargetPriority.entries.size) {
            assertNull(tower.selectTarget(listOf(dead, escaped, far)))
            tower.cycleTargetPriority()
        }
    }

    @Test
    fun `target changes preserve cooldown and never cost gold`() {
        val tower = Tower(0.0, 0.0, 100.0, 10.0, 30L)
        val target = enemy(1L, 10.0, 0.0, 100.0)
        assertNotNull(tower.update(1L, listOf(target)))
        tower.cycleTargetPriority()
        assertNull(tower.update(2L, listOf(target)))
        assertNotNull(tower.update(31L, listOf(target)))
        val game = GameSimulation()
        val id = assertNotNull(game.buildTower(TowerType.RAPID, 150.0, 240.0).towerId)
        val gold = game.gold
        assertTrue(game.cycleTargetPriority(id).success)
        assertEquals(TargetPriority.STRONGEST, game.towers.single().targetPriority)
        assertEquals(gold, game.gold)
        assertFalse(game.cycleTargetPriority(Long.MAX_VALUE).success)
        assertEquals(gold, game.gold)
    }

    @Test
    fun `upgrade preview exactly matches applied values and vanishes at max level`() {
        for (type in TowerType.entries) {
            val tower = Tower(0.0, 0.0, type.baseRange, type.baseDamage, type.baseAttackIntervalTicks, type = type)
            repeat(Tower.MAX_LEVEL - 1) {
                val preview = assertNotNull(tower.nextUpgrade)
                assertEquals(tower.upgradeCost, preview.cost)
                tower.upgrade()
                assertEquals(preview.range, tower.range)
                assertEquals(preview.damage, tower.damage)
                assertEquals(preview.attackIntervalTicks, tower.attackIntervalTicks)
            }
            assertNull(tower.nextUpgrade)
            assertNull(tower.upgradeCost)
        }
    }

    private fun enemy(id: Long, startX: Double, progress: Double, health: Double) =
        Enemy(id, listOf(Waypoint(startX, 0.0), Waypoint(1000.0, 0.0)), 10.0, health)
            .apply { update(progress / 10.0) }
}
