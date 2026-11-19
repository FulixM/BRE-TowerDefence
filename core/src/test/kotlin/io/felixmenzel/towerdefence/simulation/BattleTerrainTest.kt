package io.felixmenzel.towerdefence.simulation

import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleTerrainTest {
    @Test
    fun `all maps offer high ground and seeds change only the hills`() {
        for (map in BattleMap.entries) {
            val a = GameSimulation(map, seed = 583471)
            val b = GameSimulation(map, seed = 583471)
            val c = GameSimulation(map, seed = 583472)
            assertEquals(a.terrain.terraces, b.terrain.terraces)
            assertNotEquals(a.terrain.terraces, c.terrain.terraces)
            assertEquals(a.path, c.path)
            for (game in listOf(a, b, c, GameSimulation(map))) {
                assertTrue(game.terrain.terraces.any { terrace ->
                    (-1..1).any { dx -> (-1..1).any { dy ->
                        val x = terrace.x + dx * terrace.summitRadius * 0.6
                        val y = terrace.y + dy * terrace.summitRadius * 0.6
                        game.terrain.heightAt(x, y) == 2 && game.placementError(x, y) == null
                    } }
                }, "No buildable summit on ${game.battleMap} with seed ${game.seed}")
            }
        }
    }

    @Test
    fun `equal heights use reduced base range while downhill reaches farther and uphill stops short`() {
        val game = GameSimulation()
        val terrain = game.terrain
        assertEquals(0.94 * TowerType.RAPID.baseRange, game.baseRange(TowerType.RAPID), 1e-8)
        assertEquals(0, terrain.heightAt(0.0, 166.0))
        assertEquals(1, terrain.heightAt(65.0, 166.0))
        assertEquals(2, terrain.heightAt(140.0, 166.0))
        assertEquals(100.0, terrain.rangeTowards(100.0, 1, 1))
        assertEquals(90.0, terrain.rangeTowards(100.0, 1, 2))
        assertEquals(110.0, terrain.rangeTowards(100.0, 1, 0), 1e-8)

        val uphillTower = Tower(0.0, 166.0, 110.0, 20.0, 1L, terrain = terrain)
        val uphill = enemy(1, 94.0)
        assertNull(uphillTower.update(1L, listOf(uphill)))

        val downhillTower = Tower(140.0, 166.0, 110.0, 20.0, 1L, terrain = terrain)
        val edge = enemy(2, 8.0)
        val outside = enemy(3, 7.99)
        assertNull(downhillTower.update(1L, listOf(outside)))
        assertEquals(listOf(edge.id), assertNotNull(downhillTower.update(1L, listOf(edge, outside))).hitEnemyIds)
    }

    @Test
    fun `inferno and its next wave echo hit low ground and miss the uphill target`() {
        val terrain = GameSimulation().terrain
        val tower = Tower(0.0, 166.0, 110.0, 20.0, 1L, id = 1L, type = TowerType.INFERNO, terrain = terrain)
        val echo = WaveEcho(terrain)
        echo.toggleTower(tower.id)
        echo.startWave(0L)
        val valley = enemy(1, 39.0)
        val summit = enemy(2, 94.0)
        val shot = assertNotNull(tower.update(1L, listOf(valley, summit)))
        assertEquals(listOf(valley.id), shot.hitEnemyIds)
        echo.record(tower, shot)

        echo.finishWave()
        echo.startWave(20L)
        val shots = mutableListOf<Shot>()
        assertEquals(1, echo.replay(21L, listOf(valley, summit), shots))
        assertEquals(listOf(valley.id), shots.single().hitEnemyIds)
        assertEquals(100.0, summit.health)
    }

    @Test
    fun `new checkpoints keep seeded hills and older saves and competitive codes keep flat ground`() {
        val game = GameSimulation(seed = 583471)
        assertTrue(game.buildTower(TowerType.RAPID, 150.0, 230.0).success)
        var encoded = ""
        val saves = MissionSaves({ encoded }, { encoded = it })
        saves.save(assertNotNull(game.checkpoint()))
        val restored = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals(game.terrain.terraces, restored.terrain.terraces)
        assertEquals(game.towers.single().height, restored.towers.single().height)
        assertEquals("BRE3-C-N-583471", restored.challenge?.code)

        val v4 = Base64.getDecoder().decode(encoded).dropLast(6).toByteArray()
        ByteBuffer.wrap(v4).putInt(4)
        val crc = CRC32().apply { update(v4) }.value.toInt()
        encoded = Base64.getEncoder().encodeToString(v4 + ByteBuffer.allocate(4).putInt(crc).array())
        val legacy = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertFalse(legacy.elevatedTerrain)
        assertEquals("BRE1-C-N-583471", legacy.challenge?.code)
        assertEquals(TowerType.RAPID.baseRange, legacy.towers.single().range)
        assertFalse(GameSimulation(mode = MissionMode.QUICK).elevatedTerrain)
    }

    private fun enemy(id: Long, x: Double) =
        Enemy(id, listOf(Waypoint(x, 166.0), Waypoint(850.0, 166.0)), 10.0, 100.0)
}
