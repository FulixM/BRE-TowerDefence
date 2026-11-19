package io.felixmenzel.towerdefence.simulation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32

/** An intermission snapshot. The next wave starts with no enemies or live shots. */
internal data class MissionCheckpoint(
    val map: BattleMap,
    val difficulty: Difficulty,
    val seed: Int?,
    val fieldRank: Int,
    val tremorModule: TremorModule,
    val useUpperRoute: Boolean,
    val tick: Long,
    val wave: Int,
    val gold: Int,
    val lives: Int,
    val kills: Int,
    val leaked: Int,
    val nextEnemyId: Long,
    val nextTowerId: Long,
    val eliteOffered: Boolean,
    val towers: List<TowerCheckpoint>,
    val echo: EchoCheckpoint,
    val reports: List<WaveReport>,
    val mode: MissionMode = MissionMode.CAMPAIGN,
    val railModule: RailModule = RailModule.STANDARD,
    val research: ResearchLoadout = ResearchLoadout.EMPTY,
    val elevatedTerrain: Boolean = false,
    val gridPlacement: Boolean = false,
)

internal data class TowerCheckpoint(
    val id: Long,
    val type: TowerType,
    val x: Double,
    val y: Double,
    val level: Int,
    val priority: TargetPriority,
    val nextAttackTick: Long,
    val cannonSpecialization: CannonSpecialization = CannonSpecialization.STANDARD,
)

internal data class EchoCheckpoint(val armedTowerId: Long?, val impacts: List<RecordedImpact>)
internal data class RecordedImpact(val delay: Long, val damage: Double, val shot: Shot)

/** A single slot, backed by host storage. Blank and unreadable values represent an empty slot. */
class MissionSaves(
    load: () -> String = { "" },
    private val write: (String) -> Unit = {},
) {
    private val read = load

    internal fun load(): MissionCheckpoint? = MissionCheckpointCodec.decode(read())

    internal fun save(checkpoint: MissionCheckpoint) {
        write(MissionCheckpointCodec.encode(checkpoint))
    }

    internal fun clear() {
        write("")
    }
}

private object MissionCheckpointCodec {
    private const val VERSION = 6
    private const val MAX_ENCODED_LENGTH = 1_000_000
    private const val MAX_TOWERS = 512
    private const val MAX_IMPACTS = 4096

    fun encode(checkpoint: MissionCheckpoint): String {
        require(checkpoint.towers.size <= MAX_TOWERS && checkpoint.echo.impacts.size <= MAX_IMPACTS)
        require(checkpoint.isValid())
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { data ->
            data.writeCheckpoint(checkpoint)
            val payload = bytes.toByteArray()
            data.writeInt(checksum(payload, payload.size))
        }
        val encoded = Base64.getEncoder().encodeToString(bytes.toByteArray())
        require(encoded.length <= MAX_ENCODED_LENGTH)
        return encoded
    }

    fun decode(value: String): MissionCheckpoint? {
        if (value.isEmpty() || value.length > MAX_ENCODED_LENGTH) return null
        return try {
            val bytes = Base64.getDecoder().decode(value)
            val payloadSize = bytes.size - Int.SIZE_BYTES
            if (payloadSize < Int.SIZE_BYTES || checksum(bytes, payloadSize) != ByteBuffer.wrap(bytes, payloadSize, Int.SIZE_BYTES).int) {
                return null
            }
            DataInputStream(ByteArrayInputStream(bytes, 0, payloadSize)).use { data ->
                val checkpoint = data.readCheckpoint()
                checkpoint.takeIf { data.read() == -1 && it.isValid() }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun checksum(bytes: ByteArray, length: Int) = CRC32().apply { update(bytes, 0, length) }.value.toInt()

    private fun DataOutputStream.writeCheckpoint(checkpoint: MissionCheckpoint) {
        writeInt(VERSION)
        writeUTF(checkpoint.map.name)
        writeUTF(checkpoint.difficulty.name)
        writeBoolean(checkpoint.seed != null)
        checkpoint.seed?.let(::writeInt)
        writeInt(checkpoint.fieldRank)
        writeUTF(checkpoint.tremorModule.name)
        writeBoolean(checkpoint.useUpperRoute)
        writeLong(checkpoint.tick)
        writeInt(checkpoint.wave)
        writeInt(checkpoint.gold)
        writeInt(checkpoint.lives)
        writeInt(checkpoint.kills)
        writeInt(checkpoint.leaked)
        writeLong(checkpoint.nextEnemyId)
        writeLong(checkpoint.nextTowerId)
        writeBoolean(checkpoint.eliteOffered)
        writeInt(checkpoint.towers.size)
        for (tower in checkpoint.towers) writeTower(tower)
        writeLong(checkpoint.echo.armedTowerId ?: 0L)
        writeInt(checkpoint.echo.impacts.size)
        for (impact in checkpoint.echo.impacts) writeImpact(impact)
        writeInt(checkpoint.reports.size)
        for (report in checkpoint.reports) {
            writeInt(report.wave)
            writeInt(report.leaked)
            writeInt(report.echoHits)
            writeBoolean(report.elite)
            writeBoolean(report.cleared)
        }
        writeUTF(checkpoint.mode.name)
        writeUTF(checkpoint.railModule.name)
        ResearchTrack.entries.forEach { writeInt(checkpoint.research.level(it)) }
        writeBoolean(checkpoint.elevatedTerrain)
        writeBoolean(checkpoint.gridPlacement)
    }

    private fun DataInputStream.readCheckpoint(): MissionCheckpoint {
        val version = readInt()
        require(version in 1..VERSION)
        val map = BattleMap.valueOf(readUTF())
        val difficulty = Difficulty.valueOf(readUTF())
        val seed = if (readFlag()) readInt() else null
        val fieldRank = readInt()
        val tremorModule = TremorModule.valueOf(readUTF())
        val useUpperRoute = readFlag()
        val tick = readLong()
        val wave = readInt()
        val gold = readInt()
        val lives = readInt()
        val kills = readInt()
        val leaked = readInt()
        val nextEnemyId = readLong()
        val nextTowerId = readLong()
        val eliteOffered = readFlag()
        val towers = boundedList(MAX_TOWERS) { readTower(version) }
        val armedTowerId = readLong().takeUnless { it == 0L }
        val echo = EchoCheckpoint(armedTowerId, boundedList(MAX_IMPACTS) { readImpact(version) })
        val reports = boundedList(GameSimulation.TOTAL_WAVES) {
            WaveReport(readInt(), readInt(), readInt(), readFlag(), readFlag())
        }
        return MissionCheckpoint(
            map, difficulty, seed, fieldRank, tremorModule, useUpperRoute, tick, wave, gold, lives,
            kills, leaked, nextEnemyId, nextTowerId, eliteOffered, towers, echo, reports,
            if (version == 1) MissionMode.CAMPAIGN else MissionMode.valueOf(readUTF()),
            if (version < 3) RailModule.STANDARD else RailModule.valueOf(readUTF()),
            if (version < 4) ResearchLoadout.EMPTY else ResearchLoadout(readInt(), readInt(), readInt(), readInt()),
            version >= 5 && readFlag(),
            version >= 6 && readFlag(),
        )
    }

    private fun DataOutputStream.writeTower(tower: TowerCheckpoint) {
        writeLong(tower.id)
        writeUTF(tower.type.name)
        writeDouble(tower.x)
        writeDouble(tower.y)
        writeInt(tower.level)
        writeUTF(tower.priority.name)
        writeLong(tower.nextAttackTick)
        writeUTF(tower.cannonSpecialization.name)
    }

    private fun DataInputStream.readTower(version: Int) = TowerCheckpoint(
        readLong(), TowerType.valueOf(readUTF()), readDouble(), readDouble(), readInt(),
        TargetPriority.valueOf(readUTF()), readLong(),
        if (version == 1) CannonSpecialization.STANDARD else CannonSpecialization.valueOf(readUTF()),
    )

    private fun DataOutputStream.writeImpact(impact: RecordedImpact) {
        val shot = impact.shot
        writeLong(impact.delay)
        writeDouble(impact.damage)
        writeDouble(shot.fromX)
        writeDouble(shot.fromY)
        writeDouble(shot.toX)
        writeDouble(shot.toY)
        writeUTF(shot.type.name)
        writeLong(shot.tick)
        writeInt(shot.arcJumps.size)
        for (jump in shot.arcJumps) {
            writeDouble(jump.x)
            writeDouble(jump.y)
        }
        writeDouble(shot.areaRadius)
        writeUTF(shot.tremorModule.name)
        writeUTF(shot.railModule.name)
    }

    private fun DataInputStream.readImpact(version: Int): RecordedImpact {
        val delay = readLong()
        val damage = readDouble()
        val fromX = readDouble()
        val fromY = readDouble()
        val toX = readDouble()
        val toY = readDouble()
        val type = TowerType.valueOf(readUTF())
        val tick = readLong()
        val jumps = boundedList(2) { Waypoint(readDouble(), readDouble()) }
        val radius = readDouble()
        val module = TremorModule.valueOf(readUTF())
        val rail = if (version < 3) RailModule.STANDARD else RailModule.valueOf(readUTF())
        val shot = Shot(fromX, fromY, toX, toY, type, tick, emptyList(), jumps, radius,
            tremorModule = module, railModule = rail)
        return RecordedImpact(delay, damage, shot)
    }

    private fun DataInputStream.readFlag(): Boolean {
        val value = readUnsignedByte()
        require(value <= 1)
        return value == 1
    }

    private fun <T> DataInputStream.boundedList(maximum: Int, item: () -> T): List<T> {
        val count = readInt()
        require(count in 0..maximum)
        return List(count) { item() }
    }

    private fun MissionCheckpoint.isValid(): Boolean {
        if (!mode.supports(map, seed) || elevatedTerrain && mode != MissionMode.CAMPAIGN || wave !in 0 until mode.waveCount || tick !in 0..Long.MAX_VALUE / 2) return false
        if (seed != null && gridPlacement && !elevatedTerrain) return false
        if (!ResearchLoadout.appliesTo(mode, seed) && research != ResearchLoadout.EMPTY) return false
        if (gold < 0 || lives !in 1..difficulty.startingLives || kills < 0 || leaked < 0) return false
        if (fieldRank < 1 || (fieldRank < TremorModule.REQUIRED_RANK && tremorModule != TremorModule.STANDARD)) return false
        if (useUpperRoute && map.alternatePath == null) return false
        if (nextEnemyId !in 1..Long.MAX_VALUE / 2 || nextTowerId !in 1..Long.MAX_VALUE / 2) return false

        val towerIds = towers.map { it.id }.toSet()
        if (towerIds.size != towers.size || echo.armedTowerId?.let { it !in towerIds } == true) return false
        if (towers.any { tower ->
                tower.id !in 1 until nextTowerId || tower.level !in 1..Tower.MAX_LEVEL ||
                    tower.type.requiredRank > fieldRank || !onBoard(tower.x, tower.y, GameSimulation.BUILD_MARGIN) ||
                    tower.nextAttackTick !in 0..tick + railModule.maxAttackInterval(tower.type) ||
                    tower.cannonSpecialization != CannonSpecialization.STANDARD &&
                        (tower.type != TowerType.CANNON || tower.level != Tower.MAX_LEVEL)
            }
        ) return false

        if (reports.size != wave || reports.withIndex().any { (index, report) ->
                report.wave != index + 1 || !report.cleared || report.leaked < 0 || report.echoHits < 0
            }
        ) return false
        if (echo.impacts.zipWithNext().any { (first, next) -> first.delay > next.delay }) return false
        return echo.impacts.all { impact ->
            val shot = impact.shot
            shot.tick in 0..tick && impact.delay in 0..shot.tick && impact.damage.isFinite() && impact.damage >= 0 &&
                onBoard(shot.fromX, shot.fromY) && onBoard(shot.toX, shot.toY) &&
                shot.arcJumps.all { jump -> onBoard(jump.x, jump.y) } && shot.areaRadius in 0.0..GameSimulation.WORLD_WIDTH
        }
    }

    private fun onBoard(x: Double, y: Double, margin: Double = 0.0) =
        x in margin..GameSimulation.WORLD_WIDTH - margin && y in margin..GameSimulation.WORLD_HEIGHT - margin
}
