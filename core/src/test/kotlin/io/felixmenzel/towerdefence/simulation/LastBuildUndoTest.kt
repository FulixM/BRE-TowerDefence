package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastBuildUndoTest {
    @Test
    fun `undo refunds one last build and frees its square without rewinding tower IDs`() {
        val game = GameSimulation()
        val first = assertNotNull(game.buildTower(TowerType.RAPID, 140.0, 208.0).towerId)
        val second = assertNotNull(game.buildTower(TowerType.RAPID, 450.0, 250.0).towerId)

        assertFalse(game.buildTower(TowerType.RAPID, 450.0, 250.0).success)
        val gold = game.gold
        assertEquals(TowerType.RAPID.cost, game.lastBuildRefund)
        assertEquals(second, game.undoLastBuild().towerId)
        assertEquals(gold + TowerType.RAPID.cost, game.gold)
        assertEquals(listOf(first), game.towers.map { it.id })
        assertNull(game.towerAt(450.0, 250.0))

        assertFalse(game.undoLastBuild().success)
        assertEquals(gold + TowerType.RAPID.cost, game.gold)
        val replacement = assertNotNull(game.buildTower(TowerType.RAPID, 450.0, 250.0).towerId)
        assertTrue(replacement > second)
    }

    @Test
    fun `modifying or selling a new tower invalidates its refund`() {
        val edits: List<(GameSimulation, Long) -> Unit> = listOf(
            { game, id -> assertTrue(game.upgradeTower(id).success) },
            { game, id -> repeat(TargetPriority.entries.size) { assertTrue(game.cycleTargetPriority(id).success) } },
            { game, id -> repeat(2) { assertTrue(game.selectEchoTower(id).success) } },
            { game, id -> assertTrue(game.sellTower(id).success) },
        )
        for (edit in edits) {
            val game = GameSimulation()
            val id = assertNotNull(game.buildTower(TowerType.RAPID, 140.0, 208.0).towerId)
            edit(game, id)
            val gold = game.gold
            assertNull(game.lastBuildRefund)
            assertFalse(game.undoLastBuild().success)
            assertEquals(gold, game.gold)
        }
    }

    @Test
    fun `starting a wave clears the option and combat builds are ineligible`() {
        val game = GameSimulation()
        val tower = game.buildTower(TowerType.RAPID, 140.0, 208.0)
        assertTrue(tower.success)
        assertTrue(game.startWave().success)
        assertFalse(game.undoLastBuild().success)
        assertTrue(game.buildTower(TowerType.RAPID, 450.0, 250.0).success)
        assertNull(game.lastBuildRefund)
        while (game.state == GameState.RUNNING) game.update()
        assertEquals(GameState.PREPARING, game.state)
        assertFalse(game.undoLastBuild().success)
        assertTrue(game.buildTower(TowerType.RAPID, 750.0, 250.0).success)
        assertTrue(game.undoLastBuild().success)
    }

    @Test
    fun `a preparation save preserves eligibility and records a completed refund once`() {
        val game = GameSimulation()
        val id = assertNotNull(game.buildTower(TowerType.RAPID, 140.0, 208.0).towerId)
        var value = ""
        val saves = MissionSaves({ value }, { value = it })
        saves.save(assertNotNull(game.checkpoint()))
        val restored = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))

        assertEquals(id, restored.undoLastBuild().towerId)
        assertEquals(restored.startingGold, restored.gold)
        saves.save(assertNotNull(restored.checkpoint()))
        val afterRefund = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertTrue(afterRefund.towers.isEmpty())
        assertFalse(afterRefund.undoLastBuild().success)
    }
}
