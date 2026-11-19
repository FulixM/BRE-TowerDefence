package io.felixmenzel.towerdefence.simulation

/** Bastion's protection opens at fixed distances along The Corridor. */
object Bastion {
    const val HEALTH_MULTIPLIER = 1.22
    const val PROTECTED_DAMAGE_MULTIPLIER = 0.35

    val windows: List<ClosedFloatingPointRange<Double>> = listOf(260.0..455.0, 670.0..900.0)
}
