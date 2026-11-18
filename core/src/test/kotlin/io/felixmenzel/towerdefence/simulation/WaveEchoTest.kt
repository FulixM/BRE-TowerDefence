package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaveEchoTest {
    @Test
    fun `recorded shots keep their offsets and coordinates after preparation and sale`() {
        val game = GameSimulation(difficulty = Difficulty.RELAXED)
        val id = assertNotNull(game.buildTower(TowerType.RAPID, 100.0, 105.0).towerId)
        assertTrue(game.selectEchoTower(id).success)
        repeat(43) { game.update() }
        val recordingStart = game.tick
        assertTrue(game.startWave().success)
        val recording = mutableListOf<Shot>()
        finishWave(game) {
            recording += game.shots.filter { it.tick == game.tick }
        }
        assertTrue(recording.size >= 2)
        assertTrue(recording.none { it.isEcho })
        assertTrue(game.sellTower(id).success)
        repeat(239) { game.update() }

        val replayStart = game.tick
        assertTrue(game.startWave().success)
        val replayed = mutableListOf<Shot>()
        game.update()
        val firstEnemy = game.enemies.single()
        val firstEcho = game.shots.single { it.tick == game.tick }
        assertTrue(firstEcho.isEcho)
        assertEquals(listOf(firstEnemy.id), firstEcho.hitEnemyIds)
        assertEquals(firstEnemy.maxHealth - TowerType.RAPID.baseDamage * 0.5, firstEnemy.health, 0.000001)
        replayed += firstEcho
        finishWave(game) {
            replayed += game.shots.filter { it.tick == game.tick }
        }

        assertEquals(recording.map { it.tick - recordingStart }, replayed.map { it.tick - replayStart })
        assertEquals(
            recording.map { listOf(it.fromX, it.fromY, it.toX, it.toY) },
            replayed.map { listOf(it.fromX, it.fromY, it.toX, it.toY) },
        )
        assertTrue(replayed.all { it.isEcho })
        assertTrue(game.startWave().success)
        repeat(100) {
            game.update()
            assertTrue(game.shots.none { it.isEcho && it.tick == game.tick })
        }
    }

    @Test
    fun `fixed point shot fires on time and misses a target outside the impact radius`() {
        val echo = WaveEcho()
        val tower = Tower(0.0, 50.0, 200.0, 20.0, 30, id = 8)
        echo.toggleTower(tower.id)
        echo.startWave(100)
        val originalEnemy = enemy(1, 80.0)
        val recorded = assertNotNull(tower.update(108, listOf(originalEnemy)))
        echo.record(tower, recorded)
        echo.finishWave()
        echo.startWave(500)

        val outside = enemy(2, 93.0)
        val shots = mutableListOf<Shot>()
        echo.replay(507, listOf(outside), shots)
        assertTrue(shots.isEmpty())
        echo.replay(508, listOf(outside), shots)
        assertEquals(recorded.toX, shots.single().toX)
        assertTrue(shots.single().isEcho)
        assertTrue(shots.single().hitEnemyIds.isEmpty())
        assertEquals(100.0, outside.health)

        echo.replay(509, listOf(enemy(3, 80.0)), shots)
        assertEquals(1, shots.size)
    }

    @Test
    fun `replay uses the original splash center and current enemies`() {
        val echo = WaveEcho()
        val cannon = Tower(0.0, 40.0, 160.0, 34.0, 60, id = 7, type = TowerType.CANNON)
        echo.toggleTower(cannon.id)
        echo.startWave(0)
        echo.record(cannon, assertNotNull(cannon.update(5, listOf(enemy(1, 90.0)))))
        echo.finishWave()
        echo.startWave(80)
        val inside = enemy(2, 145.0)
        val outside = enemy(3, 151.0)
        val shots = mutableListOf<Shot>()
        echo.replay(85, listOf(inside, outside), shots)
        assertEquals(listOf(2L), shots.single().hitEnemyIds)
        assertEquals(90.0, shots.single().toX)
        assertEquals(83.0, inside.health)
        assertEquals(100.0, outside.health)
    }

    @Test
    fun `arc echo preserves recorded jumps even when the first impact misses`() {
        val echo = WaveEcho()
        val arc = Tower(0.0, 0.0, 80.0, 20.0, 30, id = 9, type = TowerType.ARC)
        echo.toggleTower(arc.id)
        echo.startWave(0)
        val original = assertNotNull(arc.update(1, listOf(enemy(1, 65.0), enemy(2, 115.0), enemy(3, 170.0))))
        echo.record(arc, original)
        echo.finishWave()
        echo.startWave(80)
        val secondImpact = enemy(4, 115.0)
        val thirdImpact = enemy(5, 170.0)
        val shots = mutableListOf<Shot>()

        echo.replay(81, listOf(secondImpact, thirdImpact), shots)

        assertEquals(original.arcJumps, shots.single().arcJumps)
        assertEquals(listOf(4L, 5L), shots.single().hitEnemyIds)
        assertEquals(93.5, secondImpact.health)
        assertEquals(96.0, thirdImpact.health)
    }

    @Test
    fun `arming selects only one tower and restart clears echo state`() {
        val game = GameSimulation()
        val first = assertNotNull(game.buildTower(TowerType.RAPID, 100.0, 105.0).towerId)
        val second = assertNotNull(game.buildTower(TowerType.RAPID, 175.0, 105.0).towerId)
        assertTrue(game.selectEchoTower(first).success)
        assertTrue(game.selectEchoTower(second).success)
        assertEquals(second, game.echo.armedTowerId)
        assertTrue(game.selectEchoTower(second).success)
        assertNull(game.echo.armedTowerId)
        assertTrue(game.selectEchoTower(first).success)
        assertTrue(game.startWave().success)
        assertFalse(game.selectEchoTower(second).success)
        game.update()
        assertTrue(game.echo.recordedCount > 0)
        game.restart()
        assertNull(game.echo.armedTowerId)
        assertNull(game.echo.recordingTowerId)
        assertEquals(0, game.echo.recordedCount)
        assertEquals(0, game.echo.remainingCount)
    }

    private fun finishWave(game: GameSimulation, onTick: () -> Unit) {
        repeat(7200) {
            if (game.state != GameState.RUNNING) return
            game.update()
            onTick()
        }
        error("Wave did not finish")
    }

    private fun enemy(id: Long, x: Double) =
        Enemy(id, listOf(Waypoint(x, 0.0), Waypoint(1000.0, 0.0)), 0.0, 100.0)
}
