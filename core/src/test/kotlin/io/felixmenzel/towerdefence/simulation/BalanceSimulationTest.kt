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
    fun `a mixed defense can complete all ten waves using earned gold`() {
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
    fun `both maps are winnable on every difficulty with a budget legal mixed defense`() {
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
        fun play(difficulty: Difficulty) = playCampaign(GameSimulation(BattleMap.SWITCHBACK, difficulty)) {
            fortifyUsing(it, switchbackPlan.take(4))
        }
        val relaxed = play(Difficulty.RELAXED)
        val normal = play(Difficulty.NORMAL)
        val veteran = play(Difficulty.VETERAN)

        assertEquals(GameState.WON, relaxed.state)
        assertEquals(GameState.WON, normal.state)
        assertTrue(normal.leakedEnemies > relaxed.leakedEnemies)
        assertEquals(GameState.LOST, veteran.state)
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
        playCampaign(GameSimulation(), prepare)

    private fun playCampaign(game: GameSimulation, prepare: (GameSimulation) -> Unit): GameSimulation {
        var wavesStarted = 0
        while (game.state != GameState.WON && game.state != GameState.LOST) {
            assertEquals(GameState.PREPARING, game.state)
            prepare(game)
            assertTrue(game.gold >= 0, "Build orders must never spend unavailable gold")
            assertTrue(game.startWave().success)
            wavesStarted++
            assertTrue(wavesStarted <= game.totalWaves, "Campaign must terminate")
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
    }

    private data class Placement(val type: TowerType, val x: Double, val y: Double)

    private val switchbackPlan = listOf(
        Placement(TowerType.RAPID, 320.0, 330.0),
        Placement(TowerType.RAPID, 420.0, 240.0),
        Placement(TowerType.CANNON, 580.0, 310.0),
        Placement(TowerType.FROST, 320.0, 240.0),
        Placement(TowerType.CANNON, 650.0, 290.0),
        Placement(TowerType.RAPID, 160.0, 320.0),
        Placement(TowerType.CANNON, 420.0, 470.0),
        Placement(TowerType.RAPID, 580.0, 470.0),
        Placement(TowerType.FROST, 650.0, 470.0),
        Placement(TowerType.CANNON, 810.0, 320.0),
        Placement(TowerType.RAPID, 160.0, 230.0),
        Placement(TowerType.CANNON, 580.0, 80.0),
    )

    private val buildPlan = listOf(
        Placement(TowerType.RAPID, 240.0, 240.0),
        Placement(TowerType.CANNON, 370.0, 270.0),
        Placement(TowerType.FROST, 530.0, 270.0),
        Placement(TowerType.RAPID, 670.0, 180.0),
        Placement(TowerType.CANNON, 370.0, 430.0),
        Placement(TowerType.RAPID, 230.0, 350.0),
        Placement(TowerType.CANNON, 680.0, 350.0),
        Placement(TowerType.RAPID, 530.0, 100.0),
        Placement(TowerType.FROST, 380.0, 90.0),
        Placement(TowerType.CANNON, 160.0, 230.0),
        Placement(TowerType.RAPID, 450.0, 430.0),
        Placement(TowerType.CANNON, 700.0, 30.0),
    )
}
