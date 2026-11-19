package io.felixmenzel.towerdefence.simulation

/** Bastion's protection opens at fixed distances along The Corridor. */
object Bastion {
    const val WAVE = 15
    const val HEALTH_MULTIPLIER = 1.22
    const val PROTECTED_DAMAGE_MULTIPLIER = 0.35

    val windows: List<ClosedFloatingPointRange<Double>> = listOf(260.0..455.0, 670.0..900.0)
}

/** Relays precede the Marshal and keep its armor active between the damage windows. */
object Marshal {
    const val WAVE = 20
    const val SLOT = 20
    const val HEALTH_MULTIPLIER = 1.30
    const val PROTECTED_DAMAGE_MULTIPLIER = 0.28

    val relaySlots = SLOT - 2 until SLOT
    val windows: List<ClosedFloatingPointRange<Double>> = listOf(340.0..425.0, 755.0..845.0)
}
