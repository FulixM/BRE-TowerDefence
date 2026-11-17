package io.felixmenzel.towerdefence.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixedStepLoopTest {
    @Test
    fun `different frame rates produce identical simulation ticks`() {
        for (fps in listOf(30, 60, 144)) {
            var ticks = 0
            val loop = FixedStepLoop { ticks++ }
            repeat(fps * 10) { loop.advance(1.0 / fps) }
            assertEquals(600, ticks, "fps=$fps")
            assertTrue(loop.interpolationAlpha >= 0.0 && loop.interpolationAlpha < 1.0)
        }
    }

    @Test
    fun `substeps accumulate and provide interpolation`() {
        var ticks = 0
        val loop = FixedStepLoop { ticks++ }
        assertEquals(0, loop.advance(GameSimulation.STEP_SECONDS / 2.0))
        assertEquals(0.5, loop.interpolationAlpha, 1e-9)
        assertEquals(1, loop.advance(GameSimulation.STEP_SECONDS / 2.0))
        assertEquals(1, ticks)
        assertEquals(0.0, loop.interpolationAlpha, 1e-9)
    }

    @Test
    fun `long frame has bounded catchup and discards excess time`() {
        var ticks = 0
        val loop = FixedStepLoop { ticks++ }
        assertEquals(15, loop.advance(60.0))
        loop.advance(0.0)
        assertEquals(15, ticks)
    }

    @Test
    fun `reset discards fractional time`() {
        var ticks = 0
        val loop = FixedStepLoop { ticks++ }
        loop.advance(GameSimulation.STEP_SECONDS * 0.75)
        loop.reset()
        assertEquals(0.0, loop.interpolationAlpha)
        loop.advance(GameSimulation.STEP_SECONDS * 0.5)
        assertEquals(0, ticks)
    }

    @Test
    fun `invalid frame times are rejected without advancing simulation`() {
        var ticks = 0
        val loop = FixedStepLoop { ticks++ }
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { loop.advance(value) }
        }
        assertEquals(0, ticks)
        assertEquals(1, loop.advance(GameSimulation.STEP_SECONDS))
    }
}
