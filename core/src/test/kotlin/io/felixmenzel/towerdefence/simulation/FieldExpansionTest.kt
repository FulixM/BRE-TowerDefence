package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FieldExpansionTest {
    @Test
    fun `finished missions save XP and unlock tremor in the next mission`() {
        var savedXp = 80
        val profile = FieldProfile(savedXp) { savedXp = it }
        assertEquals(1, profile.rank)
        val locked = GameSimulation(fieldRank = profile.rank)
        assertFalse(locked.buildTower(TowerType.TREMOR, 125.0, 230.0).success)
        assertEquals(Difficulty.NORMAL.startingGold, locked.gold)
        assertEquals(0, profile.award(GameState.RUNNING, 7))
        assertEquals(0, profile.award(GameState.LOST, 1))
        assertEquals(20, profile.award(GameState.LOST, 2))
        val loaded = FieldProfile(savedXp)
        assertEquals(2, loaded.rank)
        assertEquals(0, loaded.rankXp)
        val unlocked = GameSimulation(fieldRank = loaded.rank)
        assertTrue(unlocked.buildTower(TowerType.TREMOR, 125.0, 230.0).success)
        assertEquals(400, profile.award(GameState.WON, 15))
        assertEquals(500, savedXp)
    }

    @Test
    fun `remix changes order and timing while preserving composition and introductions`() {
        val original = Waves.plan(14, null)
        val first = Waves.plan(14, 583471)
        assertEquals(first, Waves.plan(14, 583471))
        assertNotEquals(first, Waves.plan(14, 723400))
        assertNotEquals(original.enemies, first.enemies)
        assertNotEquals(original.intervals, first.intervals)
        assertEquals(original.description, first.description)
        assertEquals(original.enemies.groupingBy { it }.eachCount(), first.enemies.groupingBy { it }.eachCount())
        assertTrue(first.intervals.all { it in 21L..29L })
        for (wave in 1..Waves.total) {
            assertTrue(Waves.plan(wave, 583471).enemies.all { it.firstWave <= wave })
        }
    }

    @Test
    fun `retry reproduces the actual arrival ticks for the same seed`() {
        val game = GameSimulation(seed = 583471)
        fun arrivals(): List<Long> {
            val ticks = mutableListOf<Long>()
            assertTrue(game.startWave().success)
            while (game.remainingToSpawn > 0) {
                val before = game.remainingToSpawn
                game.update()
                if (game.remainingToSpawn < before) ticks += game.tick
            }
            return ticks
        }

        val first = arrivals()
        val expectedIntervals = Waves.plan(1, game.seed).intervals
        assertEquals(expectedIntervals.dropLast(1), first.zipWithNext { from, to -> to - from })
        game.restart()
        assertEquals(first, arrivals())
    }

    @Test
    fun `tremor and its echo slow every enemy within the recorded impact area`() {
        fun enemy(id: Long, x: Double) = Enemy(id, listOf(Waypoint(x, 0.0), Waypoint(500.0, 0.0)), 0.0, 100.0)
        val tower = Tower(0.0, 0.0, 128.0, 10.0, 45L, id = 7, type = TowerType.TREMOR)
        val center = enemy(1, 100.0)
        val edge = enemy(2, 144.0)
        val outside = enemy(3, 145.0)
        val shot = assertNotNull(tower.update(1L, listOf(center, edge, outside)))
        assertEquals(listOf(1L, 2L), shot.hitEnemyIds)
        assertEquals(0.70, edge.slowFactor)
        assertEquals(1.0, outside.slowFactor)

        val echo = WaveEcho()
        echo.toggleTower(tower.id)
        echo.startWave(0L)
        echo.record(tower, shot)
        echo.finishWave()
        echo.startWave(100L)
        val replayTarget = enemy(4, 144.0)
        val shots = mutableListOf<Shot>()
        echo.replay(101L, listOf(replayTarget, outside), shots)
        assertEquals(listOf(4L), shots.single().hitEnemyIds)
        assertEquals(95.0, replayTarget.health)
        assertEquals(0.70, replayTarget.slowFactor)
    }
}
