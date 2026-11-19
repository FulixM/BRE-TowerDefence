package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnemyEventTest {
    @Test
    fun `reports spawns deaths and breaches once at the resolving tick`() {
        val game = GameSimulation()
        val events = mutableListOf<Pair<EnemyEvent, Long>>()
        val record: (EnemyEvent, Enemy) -> Unit = { event, enemy -> events.add(event to enemy.id) }
        assertTrue(game.startWave().success)
        game.update(record)
        val first = game.enemies.single()
        first.takeDamage(1000.0)
        game.update(record)
        assertEquals(listOf(EnemyEvent.SPAWNED to first.id, EnemyEvent.DEFEATED to first.id), events)

        while (game.enemies.isEmpty()) game.update(record)
        val second = game.enemies.single()
        second.update(120.0)
        game.update(record)
        game.update(record)
        assertEquals(listOf(
            EnemyEvent.SPAWNED to first.id,
            EnemyEvent.DEFEATED to first.id,
            EnemyEvent.SPAWNED to second.id,
            EnemyEvent.LEAKED to second.id,
        ), events)
    }
}
