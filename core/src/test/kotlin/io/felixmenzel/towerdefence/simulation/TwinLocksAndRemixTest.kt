package io.felixmenzel.towerdefence.simulation

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwinLocksAndRemixTest {
    @Test
    fun `both locks stay clear for building and the next enemies use the route chosen between waves`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS)
        val lower = game.path
        val upper = assertNotNull(game.inactivePath)
        fun length(path: List<Waypoint>) = path.zipWithNext().sumOf { (from, to) ->
            hypot(to.x - from.x, to.y - from.y)
        }
        assertEquals(lower.first(), upper.first())
        assertEquals(lower.last(), upper.last())
        assertEquals(length(lower), length(upper))
        assertFalse(game.buildTower(TowerType.RAPID, 310.0, 450.0).success)
        assertTrue(game.switchRoute().success)
        assertFalse(game.buildTower(TowerType.RAPID, 310.0, 145.0).success)
        assertEquals(upper, game.path)
        game.startWave()
        assertFalse(game.switchRoute().success)
        repeat(300) { game.update() }
        assertTrue(game.enemies.first().y > 300.0)
        game.restart()
        assertEquals(lower, game.path)
        assertFalse(GameSimulation().switchRoute().success)
    }

    @Test
    fun `echo impact stays on its recorded branch and can miss the opposite lock`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS)
        fun progressed(id: Long) = Enemy(id, game.path, 50.0, 100.0).apply { update(5.0) }
        val lower = progressed(1)
        val tower = Tower(160.0, 220.0, 135.0, 12.0, 18, id = 10)
        val echo = WaveEcho()
        echo.toggleTower(tower.id)
        echo.startWave(0L)
        val recorded = assertNotNull(tower.update(5L, listOf(lower)))
        echo.record(tower, recorded)
        echo.finishWave()
        assertTrue(game.switchRoute().success)
        val upper = progressed(2)
        assertTrue(upper.y > 300.0 && lower.y < 300.0)
        val preview = echo.recordedImpacts().single()
        assertEquals(recorded.toY, preview.toY)

        echo.startWave(100L)
        val replay = mutableListOf<Shot>()
        echo.replay(105L, listOf(upper), replay)
        assertEquals(recorded.toY, replay.single().toY)
        assertTrue(replay.single().hitEnemyIds.isEmpty())
        assertEquals(100.0, upper.health)
    }

    @Test
    fun `rank gates modules and echoes keep the recorded radius and strength`() {
        val locked = GameSimulation(fieldRank = 2, tremorModule = TremorModule.FOCUSED)
        assertEquals(TremorModule.STANDARD, locked.tremorModule)
        val game = GameSimulation(fieldRank = 3, tremorModule = TremorModule.WIDE)
        assertTrue(game.buildTower(TowerType.TREMOR, 125.0, 230.0).success)
        val tower = game.towers.single()
        assertEquals(TremorModule.WIDE, tower.tremorModule)
        fun enemy(id: Long, x: Double) = Enemy(id, listOf(Waypoint(x, 230.0), Waypoint(600.0, 230.0)), 0.0, 100.0)

        val target = enemy(1, 207.0)
        val far = enemy(2, 267.0)
        val shot = assertNotNull(tower.update(1L, listOf(target, far)))
        assertEquals(60.0, shot.areaRadius)
        assertEquals(0.8, far.slowFactor)
        val echo = WaveEcho()
        echo.toggleTower(tower.id)
        echo.startWave(0L)
        echo.record(tower, shot)
        echo.finishWave()
        echo.startWave(100L)
        val replayTarget = enemy(3, 267.0)
        val shots = mutableListOf<Shot>()
        echo.replay(101L, listOf(replayTarget), shots)
        assertEquals(TremorModule.WIDE, shots.single().tremorModule)
        assertEquals(0.8, replayTarget.slowFactor)

        val focused = Tower(125.0, 230.0, 128.0, 10.0, 45L, type = TowerType.TREMOR, tremorModule = TremorModule.FOCUSED)
        val inside = enemy(4, 239.0)
        val outside = enemy(5, 240.0)
        focused.update(1L, listOf(enemy(1, 207.0), inside, outside))
        assertEquals(0.55, inside.slowFactor)
        assertEquals(1.0, outside.slowFactor)
    }

    @Test
    fun `remix codes preserve settings and reject unknown versions or out of range seeds`() {
        for (map in BattleMap.entries) for (difficulty in Difficulty.entries) {
            val challenge = RemixChallenge(map, difficulty, 583471)
            assertEquals(challenge, RemixChallenge.parse(" ${challenge.code.lowercase()} "))
        }
        for (invalid in listOf("BRE2-T-V-583471", "BRE1-Q-V-583471", "BRE1-T-Z-583471",
            "BRE1-T-V-012345", "BRE1-T-V-12345a", "BRE1-T-V-1000000")) {
            assertNull(RemixChallenge.parse(invalid))
        }
    }

    @Test
    fun `only a better terminal result replaces the local best for that code`() {
        val storage = mutableMapOf<String, String>()
        fun records() = RemixRecords({ storage[it].orEmpty() }, { code, value -> storage[code] = value })
        val game = GameSimulation(seed = 583471)
        val challenge = assertNotNull(game.challenge)
        val saved = records()
        assertFalse(saved.submit(game))
        fun play(killFirstWave: Boolean) {
            while (game.state == GameState.PREPARING) {
                game.startWave()
                while (game.state == GameState.RUNNING) {
                    if (game.wave == 1 && killFirstWave) game.enemies.forEach { it.takeDamage(10000.0) }
                    game.update()
                }
            }
        }

        play(false)
        assertTrue(saved.submit(game))
        val first = assertNotNull(saved.best(challenge))
        game.restart()
        play(true)
        assertTrue(saved.submit(game))
        val better = assertNotNull(records().best(challenge))
        assertTrue(better > first)
        game.restart()
        play(false)
        assertFalse(saved.submit(game))
        assertEquals(better, records().best(challenge))
        assertNull(saved.best(challenge.copy(seed = 583472)))
        assertTrue(RemixScore(15, 2, 100) > RemixScore(15, 1, 120))
        assertTrue(RemixScore(15, 2, 120) > RemixScore(15, 2, 100))
    }
}
