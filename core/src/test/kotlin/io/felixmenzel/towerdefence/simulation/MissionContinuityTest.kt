package io.felixmenzel.towerdefence.simulation

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionContinuityTest {
    @Test
    fun `checkpoint restores the mission and reproduces both ordinary fire and pending echoes`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS, Difficulty.RELAXED, 583471, 3, TremorModule.WIDE)
        assertNull(game.checkpoint())
        assertTrue(game.switchRoute().success)
        val id = assertNotNull(game.buildTower(TowerType.TREMOR, 125.0, 235.0).towerId)
        assertTrue(game.upgradeTower(id).success)
        assertTrue(game.cycleTargetPriority(id).success)
        assertTrue(game.selectEchoTower(id).success)
        assertTrue(game.startWave().success)
        assertNull(game.checkpoint())
        clearWave(game)
        assertTrue(game.selectEchoTower(id).success)

        val checkpoint = assertNotNull(game.checkpoint())
        assertTrue(checkpoint.echo.impacts.isNotEmpty())
        assertEquals(id, checkpoint.echo.armedTowerId)
        assertTrue(checkpoint.echo.impacts.all { it.shot.tremorModule == TremorModule.WIDE })

        var stored = ""
        val saves = MissionSaves({ stored }, { stored = it })
        saves.save(checkpoint)
        val decoded = assertNotNull(MissionSaves({ stored }, {}).load())
        assertEquals(checkpoint, decoded)
        val resumed = assertNotNull(GameSimulation.restore(decoded))
        assertEquals(checkpoint, resumed.checkpoint())
        assertEquals(game.path, resumed.path)

        val nextId = assertNotNull(game.buildTower(TowerType.RAPID, 745.0, 390.0).towerId)
        assertEquals(nextId, resumed.buildTower(TowerType.RAPID, 745.0, 390.0).towerId)
        assertTrue(nextId > id)
        assertTrue(game.startWave().success)
        assertTrue(resumed.startWave().success)
        var echoHits = 0
        repeat(700) {
            game.update()
            resumed.update()
            val shots = game.shots.filter { it.tick == game.tick }
            assertEquals(shots, resumed.shots.filter { it.tick == resumed.tick })
            echoHits += shots.filter { it.isEcho }.sumOf { it.hitEnemyIds.size }
        }
        assertTrue(echoHits > 0)
        assertEquals(game.enemies.map { Triple(it.id, it.health, it.slowFactor) },
            resumed.enemies.map { Triple(it.id, it.health, it.slowFactor) })
        clearWave(game)
        clearWave(resumed)
        assertEquals(game.checkpoint(), resumed.checkpoint())
        assertTrue(resumed.waveReports.last().echoHits >= echoHits)
        assertTrue(resumed.waveReports.last().cleared)
        assertEquals(0, resumed.waveReports.last().leaked)
    }

    @Test
    fun `slot rejects corrupt truncated and unsupported data when read after saving`() {
        val game = GameSimulation()
        assertTrue(game.buildTower(TowerType.RAPID, 115.0, 100.0).success)
        val checkpoint = assertNotNull(game.checkpoint())
        var stored = ""
        val saves = MissionSaves({ stored }, { stored = it })
        assertNull(saves.load())
        saves.save(checkpoint)
        val valid = stored
        val bytes = Base64.getDecoder().decode(valid)
        val unsupported = Base64.getEncoder().encodeToString(bytes.copyOf().apply { fill(0, 0, 4) })
        for (candidate in listOf("not Base64!", valid.take(valid.length / 2), unsupported)) {
            stored = candidate
            assertNull(saves.load())
        }
        stored = valid
        assertEquals(checkpoint, saves.load())
        saves.clear()
        assertTrue(stored.isEmpty())
        assertNull(saves.load())
    }

    @Test
    fun `accepting the elite offer affects the first three arrivals and awards bonus only on clear`() {
        val regular = GameSimulation(seed = 583471)
        val elite = GameSimulation(seed = 583471)
        assertFalse(elite.canOfferElite)
        assertFalse(elite.toggleElite().success)
        prepareEliteWave(regular)
        prepareEliteWave(elite)
        assertTrue(elite.canOfferElite)
        assertTrue(elite.toggleElite().success)
        assertTrue(elite.eliteOffered)
        val restored = assertNotNull(GameSimulation.restore(assertNotNull(elite.checkpoint())))
        assertTrue(restored.eliteOffered)
        val goldBefore = restored.gold
        assertTrue(restored.startWave().success)
        assertTrue(regular.startWave().success)
        assertFalse(restored.canOfferElite)
        assertFalse(restored.toggleElite().success)

        for (index in 0..3) {
            val before = restored.remainingToSpawn
            repeat(GameSimulation.UPS) {
                if (restored.remainingToSpawn == before) {
                    restored.update()
                    regular.update()
                }
            }
            assertEquals(before - 1, restored.remainingToSpawn)
            assertEquals(regular.remainingToSpawn, restored.remainingToSpawn)
            assertEquals(regular.tick, restored.tick)
            val normalEnemy = regular.enemies.last()
            val acceptedEnemy = restored.enemies.last()
            assertEquals(normalEnemy.type, acceptedEnemy.type)
            assertEquals(index < 3, acceptedEnemy.isElite)
            val multiplier = if (index < 3) 1.6 else 1.0
            assertEquals(normalEnemy.maxHealth * multiplier, acceptedEnemy.maxHealth, 0.0001)
        }
        assertEquals(goldBefore, restored.gold)
        clearWave(regular)
        clearWave(restored)
        assertEquals(GameSimulation.ELITE_REWARD, restored.gold - regular.gold)
        assertEquals(WaveReport(GameSimulation.ELITE_WAVE, 0, 0, true, true), restored.waveReports.last())
        assertEquals(WaveReport(GameSimulation.ELITE_WAVE, 0, 0, false, true), regular.waveReports.last())
        assertFalse(restored.toggleElite().success)
    }

    @Test
    fun `failed elite wave reports leaks and grants no clear bonus`() {
        val game = GameSimulation(difficulty = Difficulty.VETERAN)
        prepareEliteWave(game)
        val goldBefore = game.gold
        assertTrue(game.toggleElite().success)
        assertTrue(game.startWave().success)
        runWave(game)
        assertEquals(GameState.LOST, game.state)
        assertEquals(goldBefore, game.gold)
        val report = game.waveReports.last()
        assertEquals(GameSimulation.ELITE_WAVE, report.wave)
        assertEquals(game.leakedEnemies, report.leaked)
        assertTrue(report.leaked >= game.difficulty.startingLives)
        assertEquals(0, report.echoHits)
        assertTrue(report.elite)
        assertFalse(report.cleared)
        assertNull(game.checkpoint())
        game.restart()
        assertTrue(game.waveReports.isEmpty())
        assertFalse(game.eliteOffered)
    }

    private fun prepareEliteWave(game: GameSimulation) {
        repeat(GameSimulation.ELITE_WAVE - 1) {
            assertTrue(game.startWave().success)
            clearWave(game)
        }
    }

    private fun clearWave(game: GameSimulation) {
        runWave(game) { game.enemies.forEach { it.takeDamage(1_000_000.0, piercesArmor = true) } }
        assertEquals(GameState.PREPARING, game.state)
    }

    private fun runWave(game: GameSimulation, beforeTick: () -> Unit = {}) {
        repeat(7000) {
            if (game.state != GameState.RUNNING) return
            beforeTick()
            game.update()
        }
        error("Wave ${game.wave} did not finish")
    }
}
