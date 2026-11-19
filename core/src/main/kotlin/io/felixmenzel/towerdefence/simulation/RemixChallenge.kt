package io.felixmenzel.towerdefence.simulation

import java.util.Locale

data class RemixChallenge(val map: BattleMap, val difficulty: Difficulty, val seed: Int, val elevatedTerrain: Boolean = true) {
    init {
        require(seed in SEED_RANGE)
    }

    val code: String get() = "BRE${if (elevatedTerrain) 2 else 1}-${map.code}-${difficulty.code}-$seed"

    companion object {
        val SEED_RANGE = 100000..999999

        fun parse(code: String): RemixChallenge? {
            val normalized = code.trim()
            if (normalized.length != 15) return null
            val parts = normalized.uppercase(Locale.ROOT).split('-')
            if (parts.size != 4 || parts[0] !in setOf("BRE1", "BRE2") || parts[3].length != 6) return null
            val map = BattleMap.entries.firstOrNull { it.code == parts[1] } ?: return null
            val difficulty = Difficulty.entries.firstOrNull { it.code == parts[2] } ?: return null
            val seed = parts[3].toIntOrNull()?.takeIf { it in SEED_RANGE } ?: return null
            return RemixChallenge(map, difficulty, seed, parts[0] == "BRE2")
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
    internal fun best(challenge: RemixChallenge): RemixScore? = best(challenge.code)

    internal fun quickBest(difficulty: Difficulty): RemixScore? = best(quickKey(difficulty))

    private fun best(key: String): RemixScore? = RemixScore.decode(load(key))

    private fun quickKey(difficulty: Difficulty) = "QUICK1-${difficulty.code}"

    internal fun submit(game: GameSimulation): Boolean {
        val (code, score) = improvedRecord(game) ?: return false
        save(code, score)
        return true
    }

    internal fun improvedRecord(game: GameSimulation): Pair<String, String>? {
        val key = if (game.mode == MissionMode.QUICK) quickKey(game.difficulty) else game.challenge?.code ?: return null
        val cleared = when (game.state) {
            GameState.WON -> game.wave
            GameState.LOST -> game.wave - 1
            else -> return null
        }
        val score = RemixScore(cleared, game.lives, game.kills)
        val previous = best(key)
        if (previous != null && score <= previous) return null
        return key to score.encode()
    }
}
