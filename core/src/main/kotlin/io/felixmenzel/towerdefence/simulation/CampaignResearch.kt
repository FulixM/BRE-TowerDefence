package io.felixmenzel.towerdefence.simulation

enum class ResearchTrack(val title: String) {
    FIREPOWER("Firepower"),
    RELOAD("Reload"),
    RANGE("Targeting range"),
    SUPPLY("Starting gold");

    fun benefit(level: Int): String = when (this) {
        FIREPOWER -> "+${level * 3}% damage"
        RELOAD -> "${if (level == 0) "0" else "-${level * 2}"}% reload time"
        RANGE -> "+${level * 3} units"
        SUPPLY -> "+${level * 10} gold"
    }
}

data class ResearchLoadout(
    val firepower: Int = 0,
    val reload: Int = 0,
    val range: Int = 0,
    val supply: Int = 0,
) {
    init {
        require(ResearchTrack.entries.all { level(it) in 0..MAX_LEVEL })
    }

    val startingGoldBonus: Int get() = supply * 10
    val reloadFactor: Double get() = 1.0 - reload * 0.02

    fun rangeFor(type: TowerType) = type.baseRange + range * 3
    fun damageFor(type: TowerType) = type.baseDamage * (1.0 + firepower * 0.03)

    fun level(track: ResearchTrack): Int = when (track) {
        ResearchTrack.FIREPOWER -> firepower
        ResearchTrack.RELOAD -> reload
        ResearchTrack.RANGE -> range
        ResearchTrack.SUPPLY -> supply
    }

    fun nextCost(track: ResearchTrack): Int? = COSTS.getOrNull(level(track))

    internal fun upgraded(track: ResearchTrack): ResearchLoadout = when (track) {
        ResearchTrack.FIREPOWER -> copy(firepower = firepower + 1)
        ResearchTrack.RELOAD -> copy(reload = reload + 1)
        ResearchTrack.RANGE -> copy(range = range + 1)
        ResearchTrack.SUPPLY -> copy(supply = supply + 1)
    }

    internal fun spent(): Int = ResearchTrack.entries.sumOf { COSTS.take(level(it)).sum() }

    companion object {
        const val MAX_LEVEL = 5
        private val COSTS = listOf(1, 2, 2, 3, 4)
        val EMPTY = ResearchLoadout()

        fun appliesTo(mode: MissionMode, seed: Int?): Boolean = mode == MissionMode.CAMPAIGN && seed == null
    }
}

/** Research points are derived from personal bests, so retrying a milestone cannot pay twice. */
@ConsistentCopyVisibility
data class ResearchProgress private constructor(
    private val bestWaves: List<Int>,
    val loadout: ResearchLoadout,
) {
    constructor() : this(List(BattleMap.entries.size * Difficulty.entries.size) { 0 }, ResearchLoadout.EMPTY)

    val earned: Int get() = bestWaves.sumOf { it / WAVES_PER_POINT }
    val available: Int get() = earned - loadout.spent()

    fun best(map: BattleMap, difficulty: Difficulty): Int = bestWaves[slot(map, difficulty)]

    fun afterMission(game: GameSimulation): ResearchProgress {
        if (!ResearchLoadout.appliesTo(game.mode, game.seed)) return this
        val cleared = when (game.state) {
            GameState.WON -> game.wave
            GameState.LOST -> (game.wave - 1).coerceAtLeast(0)
            else -> return this
        }
        val updated = bestWaves.toMutableList()
        for (difficulty in Difficulty.entries.take(game.difficulty.ordinal + 1)) {
            val index = slot(game.battleMap, difficulty)
            updated[index] = maxOf(updated[index], cleared)
        }
        return if (updated == bestWaves) this else copy(bestWaves = updated)
    }

    fun buy(track: ResearchTrack): ResearchProgress? {
        val cost = loadout.nextCost(track) ?: return null
        if (cost > available) return null
        return copy(loadout = loadout.upgraded(track))
    }

    fun resetUpgrades(): ResearchProgress = copy(loadout = ResearchLoadout.EMPTY)

    internal fun encode(): String = "1|" + BattleMap.entries.joinToString(",") { map ->
        map.code + ":" + Difficulty.entries.joinToString(":") { best(map, it).toString() }
    } + "|" + ResearchTrack.entries.joinToString(",") { loadout.level(it).toString() }

    companion object {
        const val WAVES_PER_POINT = 5

        private fun slot(map: BattleMap, difficulty: Difficulty) = map.ordinal * Difficulty.entries.size + difficulty.ordinal

        internal fun decode(value: String): ResearchProgress {
            val empty = ResearchProgress()
            if (value.length > 512) return empty
            val sections = value.split('|')
            if (sections.size != 3 || sections[0] != "1") return empty
            val levels = sections[2].split(',').map { it.toIntOrNull() ?: return empty }
            if (levels.size != 4 || levels.any { it !in 0..ResearchLoadout.MAX_LEVEL }) return empty
            val values = empty.bestWaves.toMutableList()
            val maps = mutableSetOf<BattleMap>()
            for (entry in sections[1].split(',')) {
                val fields = entry.split(':')
                val map = BattleMap.entries.firstOrNull { it.code == fields.first() } ?: return empty
                if (fields.size != Difficulty.entries.size + 1 || !maps.add(map)) return empty
                var easier = MissionMode.CAMPAIGN.waveCount
                for (difficulty in Difficulty.entries) {
                    val wave = fields[difficulty.ordinal + 1].toIntOrNull() ?: return empty
                    if (wave !in 0..easier) return empty
                    values[slot(map, difficulty)] = wave
                    easier = wave
                }
            }
            val progress = ResearchProgress(values, ResearchLoadout(levels[0], levels[1], levels[2], levels[3]))
            return if (progress.available >= 0) progress else empty
        }
    }
}
