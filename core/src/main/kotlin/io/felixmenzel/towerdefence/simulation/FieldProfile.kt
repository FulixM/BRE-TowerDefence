package io.felixmenzel.towerdefence.simulation

/** Progress is independent of an active defence. The desktop launcher supplies local persistence. */
class FieldProfile(
    initialXp: Int = 0,
    relayHunterUnlocked: Boolean = false,
    private val saveXp: (Int) -> Unit = {},
) {
    var relayHunterUnlocked: Boolean = relayHunterUnlocked
        private set
    var xp: Int = initialXp.coerceAtLeast(0)
        private set
    val rank: Int get() = xp / XP_PER_RANK + 1
    val rankXp: Int get() = xp % XP_PER_RANK

    internal fun award(state: GameState, wave: Int): Int {
        val total = totalAfterMission(state, wave)
        if (total > xp) saveXp(total)
        return applyTotal(total)
    }

    internal fun totalAfterMission(state: GameState, wave: Int): Int {
        val cleared = when (state) {
            GameState.WON -> wave
            GameState.LOST -> (wave - 1).coerceAtLeast(0)
            else -> return xp
        }
        val reward = cleared * 20 + if (state == GameState.WON) 100 else 0
        return (xp.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    internal fun applyTotal(total: Int): Int {
        val earned = total - xp
        xp = total
        return earned
    }

    internal fun relayHunterAfterMission(game: GameSimulation): Boolean =
        relayHunterUnlocked || (game.state == GameState.WON && game.mode == MissionMode.CAMPAIGN &&
            game.battleMap == BattleMap.CORRIDOR && game.wave == Marshal.WAVE)

    internal fun applyRelayHunterUnlock(unlocked: Boolean): Boolean {
        val newlyUnlocked = unlocked && !relayHunterUnlocked
        relayHunterUnlocked = relayHunterUnlocked || unlocked
        return newlyUnlocked
    }

    companion object {
        const val XP_PER_RANK = 100
    }
}
