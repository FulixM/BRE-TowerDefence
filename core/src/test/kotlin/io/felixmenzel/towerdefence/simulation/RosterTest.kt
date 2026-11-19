package io.felixmenzel.towerdefence.simulation

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RosterTest {
    private fun enemy(id: Long, x: Double = 15.0, type: EnemyType = EnemyType.NORMAL) =
        Enemy(id, listOf(Waypoint(x, 0.0), Waypoint(1000.0, 0.0)), 0.0, 100.0, type)

    private fun tower(type: TowerType, damage: Double = 20.0) =
        Tower(0.0, 0.0, 80.0, damage, 10, type = type)

    @Test
    fun `rail bypasses armor and shields still absorb incoming damage`() {
        val plated = enemy(1, type = EnemyType.PLATED)
        tower(TowerType.RAPID).update(1, listOf(plated))
        assertEquals(88.0, plated.health)
        tower(TowerType.RAIL).update(1, listOf(plated))
        assertEquals(68.0, plated.health)

        val shielded = enemy(2, type = EnemyType.SHIELDED)
        tower(TowerType.RAIL, 50.0).update(1, listOf(shielded))
        assertEquals(0.0, shielded.shield)
        assertEquals(95.0, shielded.health)
    }

    @Test
    fun `sunder triples shield damage without amplifying damage to health or bypassing armor`() {
        val vanguard = enemy(1, type = EnemyType.VANGUARD)
        val unshielded = enemy(2)
        val sunder = tower(TowerType.SUNDER)

        assertNotNull(sunder.update(1, listOf(vanguard)))
        assertEquals(0.0, vanguard.shield)
        assertEquals(95.0, vanguard.health)
        assertNotNull(sunder.update(11, listOf(vanguard)))
        assertEquals(80.0, vanguard.health)
        assertNotNull(sunder.update(21, listOf(unshielded)))
        assertEquals(80.0, unshielded.health)
    }

    @Test
    fun `sunder echo applies half base damage with the same shield bonus`() {
        val sunder = Tower(0.0, 0.0, 80.0, 20.0, 10, id = 42, type = TowerType.SUNDER)
        val echo = WaveEcho()
        echo.toggleTower(sunder.id)
        echo.startWave(0)
        echo.record(sunder, assertNotNull(sunder.update(1, listOf(enemy(1)))))
        echo.finishWave()
        val vanguard = enemy(2, type = EnemyType.VANGUARD)
        val shots = mutableListOf<Shot>()
        echo.startWave(100)
        echo.replay(101, listOf(vanguard), shots)

        assertEquals(listOf(vanguard.id), shots.single().hitEnemyIds)
        assertTrue(shots.single().isEcho)
        assertEquals(7.5, vanguard.shield)
        assertEquals(100.0, vanguard.health)
    }

    @Test
    fun `arc chains beyond initial range with falloff and no repeat hits`() {
        val enemies = listOf(enemy(1, 65.0), enemy(2, 115.0), enemy(3, 170.0), enemy(4, 220.0))
        val shot = assertNotNull(tower(TowerType.ARC).update(1, enemies))
        assertEquals(listOf(1L, 2L, 3L), shot.hitEnemyIds)
        assertEquals(listOf(Waypoint(115.0, 0.0), Waypoint(170.0, 0.0)), shot.arcJumps)
        assertEquals(listOf(80.0, 87.0, 92.0, 100.0), enemies.map { it.health })
    }

    @Test
    fun `inferno hits all enemies in tower range rather than around the selected target`() {
        val enemies = listOf(enemy(1, 72.0), enemy(2, -70.0), enemy(3, 83.0))
        val shot = assertNotNull(tower(TowerType.INFERNO).update(1, enemies))
        assertEquals(listOf(1L, 2L), shot.hitEnemyIds)
        assertEquals(listOf(80.0, 80.0, 100.0), enemies.map { it.health })
    }

    @Test
    fun `only the strongest poison ticks and damage stops after expiry`() {
        val target = enemy(1)
        tower(TowerType.VENOM, 10.0).update(1, listOf(target))
        tower(TowerType.VENOM, 5.0).update(1, listOf(target))
        target.update(4.0)
        assertEquals(55.0, target.health)
        assertFalse(target.isPoisoned)
        target.update(2.0)
        assertEquals(55.0, target.health)
    }

    @Test
    fun `mark amplifies later hits and regeneration starts after a quiet period`() {
        val target = enemy(1, type = EnemyType.REGENERATING)
        tower(TowerType.BEACON, 4.0).update(1, listOf(target))
        tower(TowerType.RAPID).update(1, listOf(target))
        assertEquals(71.0, target.health)
        target.update(1.0)
        assertEquals(71.0, target.health)
        target.update(1.5)
        assertEquals(79.0, target.health)
        assertEquals(0.0, target.markedSeconds)
        target.update(4.0)
        assertEquals(100.0, target.health)
    }

    @Test
    fun `all enemies appear during the mission and introductions match the guide`() {
        val waves = (1..GameSimulation.TOTAL_WAVES).associateWith { wave ->
            (0 until GameSimulation.enemyCount(wave)).map { GameSimulation.enemyType(wave, it) }
        }
        for (type in EnemyType.entries) {
            assertEquals(type.firstWave, waves.entries.first { type in it.value }.key)
        }
        assertTrue(waves.getValue(10).containsAll(EnemyType.entries.filter { it.firstWave <= 10 }))
    }

    @Test
    fun `late assaults use distinct formations and increase in health without the opening curve`() {
        val total = GameSimulation.TOTAL_WAVES
        assertEquals(20, total)
        val assaults = (11..total).map { wave ->
            (0 until GameSimulation.enemyCount(wave)).map { GameSimulation.enemyType(wave, it) }
        }
        assertEquals(listOf(30, 30, 32, 32, 36, 36, 36, 40, 40, 42), assaults.map { it.size })
        assertEquals(listOf(EnemyType.SHIELDED, EnemyType.ARMORED, EnemyType.REGENERATING, EnemyType.SHIELDED), assaults.take(4).map { it.first() })
        assertEquals(assaults.size, assaults.toSet().size)
        assertTrue(assaults.last().containsAll(EnemyType.entries))
        assertEquals(27L, GameSimulation.spawnIntervalTicks(10))
        assertEquals(23L, GameSimulation.spawnIntervalTicks(total))
        assertEquals(6.625, Waves.healthMultiplier(10), 1e-9)
        assertEquals(12.625, Waves.healthMultiplier(total), 1e-9)
    }

    @Test
    fun `a poison kill is counted and paid once before the enemy can leak`() {
        val game = GameSimulation()
        assertTrue(game.startWave().success)
        game.update()
        val enemy = game.enemies.single()
        val pathLength = game.path.zipWithNext().sumOf { (start, end) -> hypot(end.x - start.x, end.y - start.y) }
        enemy.update((pathLength - enemy.distanceTravelled - 0.25) / enemy.speed)
        assertFalse(enemy.hasReachedGoal)
        val startingGold = game.gold
        enemy.applyPoison(10000.0, 3.0)
        repeat(2) { game.update() }
        assertEquals(1, game.kills)
        assertEquals(startingGold + enemy.bounty, game.gold)
        assertEquals(0, game.leakedEnemies)
    }
}
