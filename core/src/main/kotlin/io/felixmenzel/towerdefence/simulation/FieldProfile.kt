package io.felixmenzel.towerdefence.simulation

/** Progress is independent of an active defence. The desktop launcher supplies local persistence. */
class FieldProfile(initialXp: Int = 0, private val saveXp: (Int) -> Unit = {}) {
    var xp: Int = initialXp.coerceAtLeast(0)
        private set
    val rank: Int get() = xp / XP_PER_RANK + 1
    val rankXp: Int get() = xp % XP_PER_RANK

    internal fun award(state: GameState, wave: Int): Int {
        val cleared = when (state) {
            GameState.WON -> wave
            GameState.LOST -> (wave - 1).coerceAtLeast(0)
            else -> return 0
        }
        val reward = cleared * 20 + if (state == GameState.WON) 100 else 0
        val updated = (xp.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val earned = updated - xp
        if (earned > 0) {
            saveXp(updated)
            xp = updated
        }
        return earned
    }

    companion object {
        const val XP_PER_RANK = 100
    }
}
