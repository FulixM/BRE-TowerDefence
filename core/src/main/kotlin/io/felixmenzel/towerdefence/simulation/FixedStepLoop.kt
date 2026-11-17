package io.felixmenzel.towerdefence.simulation

import kotlin.math.min

/** Fixed simulation updates with bounded catch-up and independent render interpolation. */
class FixedStepLoop(private val updateSimulation: () -> Unit) {
    private var accumulatorSeconds = 0.0
    var interpolationAlpha = 0.0
        private set

    fun advance(frameTimeSeconds: Double): Int {
        require(frameTimeSeconds.isFinite() && frameTimeSeconds >= 0.0)
        accumulatorSeconds += min(frameTimeSeconds, MAX_FRAME_TIME_SECONDS)
        var updates = 0
        while (accumulatorSeconds + 1e-12 >= GameSimulation.STEP_SECONDS) {
            updateSimulation()
            accumulatorSeconds = (accumulatorSeconds - GameSimulation.STEP_SECONDS).coerceAtLeast(0.0)
            updates++
        }
        interpolationAlpha = accumulatorSeconds / GameSimulation.STEP_SECONDS
        return updates
    }

    fun reset() {
        accumulatorSeconds = 0.0
        interpolationAlpha = 0.0
    }

    private companion object {
        const val MAX_FRAME_TIME_SECONDS = 0.25
    }
}
