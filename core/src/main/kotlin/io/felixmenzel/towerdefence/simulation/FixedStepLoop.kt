package io.felixmenzel.towerdefence.simulation

import kotlin.math.min


class FixedStepLoop(
    private val updateSimulation: () -> Unit,
) {
    private var accumulatorSeconds = 0.0

    var interpolationAlpha: Double = 0.0
        private set

    fun advance(frameTimeSeconds: Double): Int {
        // Nach einem Breakpoint oder verschobenen Fenster nicht plötzlich
        // mehrere Sekunden Simulation nachholen.
        val acceptedFrameTime = min(frameTimeSeconds, MAX_FRAME_TIME_SECONDS)
        accumulatorSeconds += acceptedFrameTime

        var updates = 0

        while (accumulatorSeconds >= GameSimulation.STEP_SECONDS) {
            updateSimulation()
            accumulatorSeconds -= GameSimulation.STEP_SECONDS
            updates++
        }

        interpolationAlpha =
            accumulatorSeconds / GameSimulation.STEP_SECONDS

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
