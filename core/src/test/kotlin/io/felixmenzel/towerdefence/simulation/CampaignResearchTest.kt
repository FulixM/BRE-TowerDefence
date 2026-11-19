package io.felixmenzel.towerdefence.simulation

import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignResearchTest {
    @Test
    fun `a later loss still records milestones and repeating the result cannot farm points`() {
        val game = GameSimulation()
        repeat(19) { clearNextWave(game) }
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) {
            game.enemies.filterNot { it.isMarshal }.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
        assertEquals(GameState.LOST, game.state)
        val progress = ResearchProgress().afterMission(game)
        assertEquals(19, progress.best(BattleMap.CORRIDOR, Difficulty.NORMAL))
        assertEquals(19, progress.best(BattleMap.CORRIDOR, Difficulty.RELAXED))
        assertEquals(0, progress.best(BattleMap.CORRIDOR, Difficulty.VETERAN))
        assertEquals(6, progress.available)
        assertEquals(progress, progress.afterMission(game))
        val won = won(BattleMap.CORRIDOR, Difficulty.NORMAL)
        assertEquals(8, progress.afterMission(won).earned)
        assertEquals(progress.afterMission(won), progress.afterMission(won).afterMission(game))
    }

    @Test
    fun `all tracks are affordable through mastery and can be refunded without losing milestones`() {
        var progress = ResearchProgress()
        assertNull(progress.buy(ResearchTrack.FIREPOWER))
        for (map in BattleMap.entries) progress = progress.afterMission(won(map, Difficulty.VETERAN))
        assertEquals(48, progress.earned)
        for (track in ResearchTrack.entries) repeat(ResearchLoadout.MAX_LEVEL) {
            progress = assertNotNull(progress.buy(track))
        }
        assertEquals(0, progress.available)
        assertNull(progress.buy(ResearchTrack.SUPPLY))
        val restored = ResearchProgress.decode(progress.encode())
        assertEquals(progress, restored)
        val refunded = restored.resetUpgrades()
        assertEquals(48, refunded.available)
        assertEquals(20, refunded.best(BattleMap.TWIN_LOCKS, Difficulty.VETERAN))
        assertEquals(ResearchLoadout.EMPTY, refunded.loadout)
        assertEquals(ResearchProgress(), ResearchProgress.decode("1|C:0:0:0|5,0,0,0"))
    }

    @Test
    fun `research changes new classic construction and survives a checkpoint while remix and quick stay fixed`() {
        val loadout = ResearchLoadout(1, 1, 1, 1)
        val game = GameSimulation(research = loadout)
        assertEquals(230, game.gold)
        assertTrue(game.buildTower(TowerType.RAIL, 150.0, 230.0).success)
        val rail = game.towers.single()
        assertEquals(223.0 * 0.94, rail.range, 1e-8)
        assertEquals(94.76, rail.damage, 1e-8)
        assertEquals(112L, rail.attackIntervalTicks)

        var value = ""
        val saves = MissionSaves({ value }, { value = it })
        saves.save(assertNotNull(game.checkpoint()))
        val restored = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals(loadout, restored.research)
        assertEquals(rail.damage, restored.towers.single().damage)
        assertEquals(rail.attackIntervalTicks, restored.towers.single().attackIntervalTicks)
        assertEquals(ResearchLoadout.EMPTY, GameSimulation(seed = 583471, research = loadout).research)
        assertEquals(ResearchLoadout.EMPTY, GameSimulation(mode = MissionMode.QUICK, research = loadout).research)

        val progress = ResearchProgress()
        assertEquals(progress, progress.afterMission(won(BattleMap.CORRIDOR, Difficulty.NORMAL, seed = 583471)))
        assertEquals(progress, progress.afterMission(won(BattleMap.CORRIDOR, Difficulty.NORMAL, mode = MissionMode.QUICK)))
    }

    @Test
    fun `version three checkpoint keeps rail module and defaults to no research`() {
        val game = GameSimulation(railModule = RailModule.RELAY_HUNTER)
        assertTrue(game.buildTower(TowerType.RAIL, 150.0, 230.0).success)
        var value = ""
        val saves = MissionSaves({ value }, { value = it })
        saves.save(assertNotNull(game.checkpoint()))
        // A wave-zero v3 checkpoint has the same layout before the four v4 research integers, v5 terrain flag, v6 grid flag, and CRC.
        val payload = Base64.getDecoder().decode(value).dropLast(22).toByteArray()
        ByteBuffer.wrap(payload).putInt(3)
        val checksum = CRC32().apply { update(payload) }.value.toInt()
        value = Base64.getEncoder().encodeToString(payload + ByteBuffer.allocate(4).putInt(checksum).array())
        val restored = assertNotNull(GameSimulation.restore(assertNotNull(saves.load())))
        assertEquals(RailModule.RELAY_HUNTER, restored.railModule)
        assertEquals(ResearchLoadout.EMPTY, restored.research)
    }

    private fun won(map: BattleMap, difficulty: Difficulty, seed: Int? = null, mode: MissionMode = MissionMode.CAMPAIGN): GameSimulation {
        val game = GameSimulation(map, difficulty, seed, mode = mode)
        repeat(game.totalWaves) { clearNextWave(game) }
        assertEquals(GameState.WON, game.state)
        return game
    }

    private fun clearNextWave(game: GameSimulation) {
        assertTrue(game.startWave().success)
        while (game.state == GameState.RUNNING) {
            game.enemies.forEach { it.takeDamage(10000.0, piercesArmor = true) }
            game.update()
        }
    }
}
