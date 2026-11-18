package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Budget-constrained campaign checks, not a replacement for human playtesting. */
class BalanceSimulationTest {
    @Test
    fun `an undefended base loses before completing the mission`() {
        val game = playCampaign { }

        assertEquals(GameState.LOST, game.state)
        assertEquals(0, game.lives)
        assertTrue(game.wave < game.totalWaves)
    }

    @Test
    fun `one fully upgraded pulse tower cannot carry the campaign alone`() {
        val game = playCampaign { simulation ->
            if (simulation.towers.isEmpty()) {
                assertTrue(simulation.buildTower(TowerType.RAPID, 370.0, 270.0).success)
            }
            val tower = simulation.towers.single()
            while (tower.upgradeCost?.let { it <= simulation.gold } == true) {
                assertTrue(simulation.upgradeTower(tower.id).success)
            }
        }

        assertEquals(GameState.LOST, game.state)
    }

    @Test
    fun `a mixed defense can complete the campaign using earned gold`() {
        val game = playCampaign(::fortify)

        assertEquals(GameState.WON, game.state)
        assertEquals(game.totalWaves, game.wave)
        assertTrue(game.lives > 0)
        assertTrue(game.gold >= 0)
        assertEquals(TowerType.entries.toSet(), game.towers.map { it.type }.toSet())
        assertTrue(game.towers.any { it.level > 1 })
    }

    @Test
    fun `the same build orders produce the same campaign outcome`() {
        val first = playCampaign(::fortify)
        val second = playCampaign(::fortify)

        assertEquals(first.state, second.state)
        assertEquals(first.tick, second.tick)
        assertEquals(first.gold, second.gold)
        assertEquals(first.lives, second.lives)
        assertEquals(first.towers.map { it.level }, second.towers.map { it.level })
    }

    @Test
    fun `all maps are winnable on every difficulty with a budget legal mixed defense`() {
        for (battleMap in BattleMap.entries) {
            for (difficulty in Difficulty.entries) {
                val game = playCampaign(GameSimulation(battleMap, difficulty)) {
                    fortifyUsing(it, planFor(battleMap))
                }

                val mission = "$battleMap / $difficulty"
                assertEquals(GameState.WON, game.state, mission)
                assertEquals(game.totalWaves, game.wave, mission)
                assertTrue(game.lives > 0, mission)
                assertTrue(game.gold >= 0, mission)
                assertEquals(TowerType.entries.toSet(), game.towers.map { it.type }.toSet(), mission)
                assertTrue(game.towers.any { it.level > 1 }, mission)
            }
        }
    }

    @Test
    fun `an undefended base loses on every mission option`() {
        for (battleMap in BattleMap.entries) {
            for (difficulty in Difficulty.entries) {
                val game = playCampaign(GameSimulation(battleMap, difficulty)) { }

                assertEquals(GameState.LOST, game.state, "$battleMap / $difficulty")
                assertEquals(0, game.lives)
            }
        }
    }

    @Test
    fun `veteran punishes stopping defense expansion on switchback`() {
        fun play(difficulty: Difficulty) = playCampaign(GameSimulation(BattleMap.SWITCHBACK, difficulty), throughWave = 10) {
            fortifyUsing(it, switchbackPlan.take(4))
        }
        val relaxed = play(Difficulty.RELAXED)
        val normal = play(Difficulty.NORMAL)
        val veteran = play(Difficulty.VETERAN)

        assertEquals(10, relaxed.wave)
        assertEquals(GameState.PREPARING, relaxed.state)
        assertEquals(GameState.LOST, normal.state)
        assertTrue(normal.leakedEnemies > relaxed.leakedEnemies)
        assertTrue(veteran.wave < normal.wave)
        assertEquals(GameState.LOST, veteran.state)
    }

    @Test
    fun `late assaults reward expanding beyond a smaller defense on veteran`() {
        for (map in listOf(BattleMap.CORRIDOR, BattleMap.SWITCHBACK)) {
            fun play(difficulty: Difficulty) = playCampaign(GameSimulation(map, difficulty)) {
                fortifyUsing(it, planFor(map).take(8))
            }

            assertEquals(GameState.WON, play(Difficulty.NORMAL).state, "$map on Normal")
            val veteran = play(Difficulty.VETERAN)
            assertEquals(GameState.LOST, veteran.state, "$map on Veteran")
            assertTrue(veteran.wave > 10, "$map should survive the opening with this plan")
        }
    }

    @Test
    fun `switchback build orders have a reproducible outcome`() {
        fun play() = playCampaign(GameSimulation(BattleMap.SWITCHBACK, Difficulty.NORMAL)) {
            fortifyUsing(it, switchbackPlan)
        }
        val first = play()
        val second = play()

        assertEquals(GameState.WON, first.state)
        assertEquals(first.state, second.state)
        assertEquals(first.tick, second.tick)
        assertEquals(first.gold, second.gold)
        assertEquals(first.lives, second.lives)
        assertEquals(first.kills, second.kills)
        assertEquals(first.leakedEnemies, second.leakedEnemies)
        assertEquals(first.towers.map { it.level }, second.towers.map { it.level })
    }

    private fun playCampaign(prepare: (GameSimulation) -> Unit): GameSimulation =
        playCampaign(GameSimulation(), prepare = prepare)

    private fun playCampaign(
        game: GameSimulation,
        throughWave: Int = game.totalWaves,
        prepare: (GameSimulation) -> Unit,
    ): GameSimulation {
        while (game.state == GameState.PREPARING && game.wave < throughWave) {
            prepare(game)
            assertTrue(game.gold >= 0, "Build orders must never spend unavailable gold")
            assertTrue(game.startWave().success)
            var waveTicks = 0
            while (game.state == GameState.RUNNING) {
                game.update()
                waveTicks++
                assertTrue(waveTicks <= GameSimulation.UPS * 120, "A wave must finish within two simulation minutes")
            }
        }
        return game
    }

    private fun fortify(game: GameSimulation) = fortifyUsing(game, buildPlan)

    private fun fortifyUsing(game: GameSimulation, plan: List<Placement>) {
        // Fill the central bends first, then cover the exit. Commands enforce every cost.
        for ((index, placement) in plan.withIndex()) {
            if (index < game.towers.size) continue
            if (game.gold < placement.type.cost) break
            assertTrue(game.buildTower(placement.type, placement.x, placement.y).success, "Invalid build-plan position: $placement")
        }
        // Earlier investments earn range/damage upgrades without free money or direct state edits.
        for (tower in game.towers) {
            while (tower.upgradeCost?.let { it <= game.gold } == true) {
                assertTrue(game.upgradeTower(tower.id).success)
            }
        }
    }

    private fun planFor(battleMap: BattleMap): List<Placement> = when (battleMap) {
        BattleMap.CORRIDOR -> buildPlan
        BattleMap.SWITCHBACK -> switchbackPlan
        BattleMap.CROSSWIND -> crosswindPlan
    }

    private data class Placement(val type: TowerType, val x: Double, val y: Double)

    private val switchbackPlan = listOf(
        Placement(TowerType.RAPID, 320.0, 330.0),
        Placement(TowerType.RAPID, 440.0, 225.0),
        Placement(TowerType.CANNON, 580.0, 310.0),
        Placement(TowerType.FROST, 320.0, 240.0),
        Placement(TowerType.RAIL, 650.0, 290.0),
        Placement(TowerType.ARC, 554.0, 219.0),
        Placement(TowerType.VENOM, 440.0, 344.0),
        Placement(TowerType.INFERNO, 555.0, 438.0),
        Placement(TowerType.BEACON, 625.0, 340.0),
        Placement(TowerType.CANNON, 810.0, 320.0),
        Placement(TowerType.RAPID, 160.0, 230.0),
        Placement(TowerType.CANNON, 580.0, 80.0),
        Placement(TowerType.SUNDER, 675.0, 443.0),
    )

    private val buildPlan = listOf(
        Placement(TowerType.RAPID, 240.0, 240.0),
        Placement(TowerType.RAPID, 370.0, 270.0),
        Placement(TowerType.CANNON, 530.0, 270.0),
        Placement(TowerType.FROST, 670.0, 180.0),
        Placement(TowerType.RAIL, 370.0, 430.0),
        Placement(TowerType.ARC, 230.0, 350.0),
        Placement(TowerType.VENOM, 680.0, 350.0),
        Placement(TowerType.INFERNO, 555.0, 307.0),
        Placement(TowerType.BEACON, 530.0, 400.0),
        Placement(TowerType.CANNON, 160.0, 230.0),
        Placement(TowerType.RAPID, 450.0, 430.0),
        Placement(TowerType.CANNON, 700.0, 30.0),
        Placement(TowerType.SUNDER, 435.0, 265.0),
    )

    private val crosswindPlan = listOf(
        Placement(TowerType.RAPID, 125.0, 185.0),
        Placement(TowerType.INFERNO, 482.0, 295.0),
        Placement(TowerType.FROST, 475.0, 432.0),
        Placement(TowerType.RAPID, 318.0, 180.0),
        Placement(TowerType.SUNDER, 574.0, 340.0),
        Placement(TowerType.CANNON, 394.0, 278.0),
        Placement(TowerType.CANNON, 700.0, 225.0),
        Placement(TowerType.RAPID, 194.0, 205.0),
        Placement(TowerType.VENOM, 552.0, 205.0),
        Placement(TowerType.BEACON, 600.0, 105.0),
        Placement(TowerType.CANNON, 752.0, 90.0),
        Placement(TowerType.ARC, 414.0, 429.0),
        Placement(TowerType.RAIL, 285.0, 352.0),
    )
}
