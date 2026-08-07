package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnemyTest {

    @Test
    fun `enemy dies when health reaches zero`() {
        val enemy = Enemy(
            id = 1L,
            startX = 0.0,
            startY = 0.0,
            speed = 60.0,
            maxHealth = 100.0,
        )

        enemy.takeDamage(120.0)

        assertEquals(0.0, enemy.health)
        assertFalse(enemy.isAlive)
    }

}
