package io.felixmenzel.towerdefence.simulation

import java.util.Locale

data class RemixChallenge(val map: BattleMap, val difficulty: Difficulty, val seed: Int) {
    init {
        require(seed in SEED_RANGE)
    }

    val code: String get() = "BRE1-${map.code}-${difficulty.code}-$seed"

    companion object {
        val SEED_RANGE = 100000..999999

        fun parse(code: String): RemixChallenge? {
            val normalized = code.trim()
            if (normalized.length != 15) return null
            val parts = normalized.uppercase(Locale.ROOT).split('-')
            if (parts.size != 4 || parts[0] != "BRE1" || parts[3].length != 6) return null
            val map = BattleMap.entries.firstOrNull { it.code == parts[1] } ?: return null
            val difficulty = Difficulty.entries.firstOrNull { it.code == parts[2] } ?: return null
            val seed = parts[3].toIntOrNull()?.takeIf { it in SEED_RANGE } ?: return null
            return RemixChallenge(map, difficulty, seed)
        }
    }
}

data class RemixScore(val cleared: Int, val lives: Int, val kills: Int) : Comparable<RemixScore> {
    override fun compareTo(other: RemixScore) = compareValuesBy(this, other, { it.cleared }, { it.lives }, { it.kills })

    internal fun encode() = "$cleared,$lives,$kills"

    companion object {
        internal fun decode(value: String): RemixScore? {
            val fields = value.split(',').map { it.toIntOrNull() ?: return null }
            if (fields.size != 3 || fields.any { it < 0 } || fields[0] > Waves.total) return null
            return RemixScore(fields[0], fields[1], fields[2])
        }
    }
}

/** One best result per challenge. The launcher supplies persistent key/value storage. */
class RemixRecords(
    private val load: (String) -> String = { "" },
    private val save: (String, String) -> Unit = { _, _ -> },
) {
    internal fun best(challenge: RemixChallenge): RemixScore? = RemixScore.decode(load(challenge.code))

    internal fun submit(game: GameSimulation): Boolean {
        val challenge = game.challenge ?: return false
        val cleared = when (game.state) {
            GameState.WON -> game.wave
            GameState.LOST -> game.wave - 1
            else -> return false
        }
        val score = RemixScore(cleared, game.lives, game.kills)
        val previous = best(challenge)
        if (previous != null && score <= previous) return false
        save(challenge.code, score.encode())
        return true
    }
}
