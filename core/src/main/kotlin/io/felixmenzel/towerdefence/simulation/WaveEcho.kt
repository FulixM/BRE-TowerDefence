package io.felixmenzel.towerdefence.simulation

/** One recording per wave. Recorded impacts belong to the following wave only. */
internal class WaveEcho {
    var armedTowerId: Long? = null
        private set
    var recordingTowerId: Long? = null
        private set

    private var waveStartTick = 0L
    private var recording = mutableListOf<RecordedImpact>()
    private var playback: List<RecordedImpact> = emptyList()
    private var cursor = 0

    val recordedCount: Int get() = recording.size
    val remainingCount: Int get() = playback.size - cursor
    fun nextShot(preparing: Boolean): Shot? =
        (if (preparing) recording.firstOrNull() else playback.getOrNull(cursor))?.shot

    fun recordedImpacts(): Sequence<Shot> = recording.asSequence().take(8).map { it.shot }

    internal fun checkpoint() = EchoCheckpoint(armedTowerId, recording.toList())

    internal fun restore(checkpoint: EchoCheckpoint) {
        reset()
        armedTowerId = checkpoint.armedTowerId
        recording.addAll(checkpoint.impacts)
    }

    fun toggleTower(id: Long): Boolean {
        armedTowerId = if (armedTowerId == id) null else id
        return armedTowerId != null
    }

    fun onTowerSold(id: Long) {
        if (armedTowerId == id) armedTowerId = null
        if (recordingTowerId == id) recordingTowerId = null
    }

    fun startWave(tick: Long) {
        waveStartTick = tick
        playback = recording
        recording = mutableListOf()
        cursor = 0
        recordingTowerId = armedTowerId
        armedTowerId = null
    }

    fun record(tower: Tower, shot: Shot) {
        if (tower.id != recordingTowerId) return
        recording.add(RecordedImpact(shot.tick - waveStartTick, tower.damage, shot.copy(hitEnemyIds = emptyList())))
    }

    fun replay(tick: Long, enemies: List<Enemy>, shots: MutableList<Shot>): Int {
        var hits = 0
        val elapsed = tick - waveStartTick
        while (cursor < playback.size && playback[cursor].delay <= elapsed) {
            val shot = playback[cursor++].resolve(tick, enemies)
            shots.add(shot)
            hits += shot.hitEnemyIds.size
        }
        return hits
    }

    fun finishWave() {
        playback = emptyList()
        cursor = 0
        recordingTowerId = null
    }

    fun reset() {
        finishWave()
        recording.clear()
        armedTowerId = null
        waveStartTick = 0L
    }

    private fun RecordedImpact.resolve(tick: Long, enemies: List<Enemy>): Shot {
        val hitIds = when (shot.type) {
            TowerType.CANNON -> hitArea(enemies, shot.toX, shot.toY, Tower.SPLASH_RADIUS)
            TowerType.TREMOR -> hitArea(enemies, shot.toX, shot.toY, shot.tremorModule.radius)
            TowerType.INFERNO -> hitArea(enemies, shot.fromX, shot.fromY, shot.areaRadius)
            else -> buildList {
                val impacts = listOf(Waypoint(shot.toX, shot.toY)) + shot.arcJumps
                for ((index, impact) in impacts.withIndex()) {
                    val target = enemies.asSequence()
                        .filter { it.canBeHit && it.id !in this && distanceSquared(it, impact.x, impact.y) <= POINT_RADIUS * POINT_RADIUS }
                        .minWithOrNull(compareBy({ distanceSquared(it, impact.x, impact.y) }, { it.id }))
                        ?: continue
                    applyTowerHit(shot.type, target, damage * DAMAGE_FACTOR, index)
                    add(target.id)
                }
            }
        }
        return shot.copy(tick = tick, hitEnemyIds = hitIds, isEcho = true)
    }

    private fun RecordedImpact.hitArea(enemies: List<Enemy>, x: Double, y: Double, radius: Double): List<Long> = buildList {
        for (enemy in enemies) {
            if (enemy.canBeHit && distanceSquared(enemy, x, y) <= radius * radius) {
                applyTowerHit(shot.type, enemy, damage * DAMAGE_FACTOR, tremorModule = shot.tremorModule)
                add(enemy.id)
            }
        }
    }

    private val Enemy.canBeHit: Boolean get() = isAlive && !hasReachedGoal

    private fun distanceSquared(enemy: Enemy, x: Double, y: Double): Double {
        val dx = enemy.x - x
        val dy = enemy.y - y
        return dx * dx + dy * dy
    }

    companion object {
        private const val DAMAGE_FACTOR = 0.5
        private const val POINT_RADIUS = 12.0
    }
}
