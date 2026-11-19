package io.felixmenzel.towerdefence.simulation

import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildGridTest {
    @Test
    fun `one tower occupies a whole square and adjacent squares remain usable`() {
        val game = GameSimulation()
        val first = assertNotNull(game.buildTower(TowerType.RAPID, 102.0, 193.0).towerId)
        val tower = game.towers.single()
        assertEquals(Waypoint(120.0, 214.0), Waypoint(tower.x, tower.y))
        assertEquals(tower, game.towerAt(141.9, 235.9))

        val gold = game.gold
        assertFalse(game.buildTower(TowerType.RAPID, 141.9, 235.9).success)
        assertEquals(gold, game.gold)
        assertTrue(game.buildTower(TowerType.RAPID, 142.0, 220.0).success)
        assertEquals(164.0, game.towers.last().x)

        assertTrue(game.sellTower(first).success)
        assertNull(game.towerAt(105.0, 196.0))
        assertTrue(game.buildTower(TowerType.RAPID, 141.0, 200.0).success)
    }

    @Test
    fun `grid checks both routes including the inactive route and rejects off grid input`() {
        val game = GameSimulation(BattleMap.TWIN_LOCKS)
        for ((x, y) in listOf(330.0 to 145.0, 330.0 to 450.0, 890.0 to 220.0,
            0.0 to 220.0, 500.0 to Double.NaN)) {
            assertFalse(game.buildTower(TowerType.RAPID, x, y).success)
        }
        assertEquals(game.startingGold, game.gold)
        assertTrue(game.towers.isEmpty())
    }

    @Test
    fun `version five checkpoint keeps exact legacy positions and new saves enforce centres`() {
        val legacy = GameSimulation(seed = 583471, gridPlacement = false)
        assertTrue(legacy.buildTower(TowerType.RAPID, 151.0, 231.0).success)
        var value = ""
        val saves = MissionSaves({ value }, { value = it })
        val snapshot = assertNotNull(legacy.checkpoint())
        saves.save(snapshot)
        val payload = Base64.getDecoder().decode(value).dropLast(17).toByteArray()
        ByteBuffer.wrap(payload).putInt(5)
        val checksum = CRC32().apply { update(payload) }.value.toInt()
        value = Base64.getEncoder().encodeToString(payload + ByteBuffer.allocate(4).putInt(checksum).array())

        val restored = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals("BRE2-C-N-583471", restored.challenge?.code)
        assertNull(restored.lastBuildRefund)
        assertEquals(151.0, restored.towers.single().x)
        assertEquals(231.0, restored.towers.single().y)
        assertNull(GameSimulation.restore(snapshot.copy(gridPlacement = true)))
    }
}
