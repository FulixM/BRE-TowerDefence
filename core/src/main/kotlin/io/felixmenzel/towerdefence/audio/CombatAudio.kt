package io.felixmenzel.towerdefence.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Disposable
import io.felixmenzel.towerdefence.simulation.Enemy
import io.felixmenzel.towerdefence.simulation.EnemyEvent
import io.felixmenzel.towerdefence.simulation.EnemyType
import io.felixmenzel.towerdefence.simulation.GameSimulation
import io.felixmenzel.towerdefence.simulation.Shot
import io.felixmenzel.towerdefence.simulation.TowerType

internal class CombatAudio(enabled: Boolean) : Disposable {
    private val clips = Cue.entries.associateWith { cue ->
        try {
            Gdx.audio.newSound(Gdx.files.internal("audio/${cue.file}.wav"))
        } catch (error: RuntimeException) {
            Gdx.app.error("CombatAudio", "Could not load ${cue.file}", error)
            null
        }
    }
    private val nextPlay = FloatArray(Cue.entries.size)
    private var clock = 0f
    private var playsThisFrame = 0

    var enabled = enabled
        set(value) {
            field = value
            if (!value) stop()
        }

    fun beginFrame(delta: Float) {
        clock += delta
        playsThisFrame = 0
    }

    fun shot(shot: Shot) {
        if (!enabled) return
        val cue = when (shot.type) {
            TowerType.RAPID -> Cue.PULSE
            TowerType.CANNON -> Cue.IMPACT
            TowerType.FROST -> Cue.FROST
            TowerType.RAIL -> Cue.RAIL
            TowerType.ARC -> Cue.ARC
            TowerType.VENOM -> Cue.VENOM
            TowerType.INFERNO -> Cue.INFERNO
            TowerType.BEACON -> Cue.BEACON
            TowerType.SUNDER -> Cue.SUNDER
            TowerType.TREMOR -> Cue.TREMOR
        }
        play(cue, shot.fromX, if (shot.isEcho) 0.13f else 0.25f, if (shot.isEcho) 0.78f else 1f)
    }

    fun enemy(event: EnemyEvent, enemy: Enemy) {
        if (!enabled) return
        when (event) {
            EnemyEvent.SPAWNED -> when {
                enemy.isBoss -> play(Cue.ARRIVAL, enemy.x, 0.49f, 0.83f)
                enemy.isCommander -> play(Cue.ARRIVAL, enemy.x, 0.37f, 1.13f)
                else -> Unit
            }
            EnemyEvent.DEFEATED -> {
                val pitch = when (enemy.type) {
                    EnemyType.FAST -> 1.24f
                    EnemyType.ARMORED, EnemyType.PLATED, EnemyType.VANGUARD -> 0.79f
                    else -> 1f
                }
                play(Cue.DEFEAT, enemy.x, if (enemy.isBoss) 0.41f else 0.16f, pitch)
            }
            EnemyEvent.LEAKED -> play(Cue.BREACH, enemy.x, 0.44f, 1f)
        }
    }

    private fun play(cue: Cue, x: Double, gain: Float, pitch: Float) {
        val limit = if (cue == Cue.BREACH || cue == Cue.ARRIVAL) 8 else 6
        if (playsThisFrame >= limit || clock < nextPlay[cue.ordinal]) return
        val clip = clips[cue] ?: return
        val pan = ((x / GameSimulation.WORLD_WIDTH * 2.0 - 1.0) * 0.65).toFloat().coerceIn(-1f, 1f)
        clip.play(gain, pitch, pan)
        nextPlay[cue.ordinal] = clock + 0.065f
        playsThisFrame++
    }

    fun stop() = clips.values.forEach { it?.stop() }

    override fun dispose() = clips.values.forEach { it?.dispose() }

    private enum class Cue(val file: String) {
        PULSE("pulse"), IMPACT("impact"), FROST("frost"), RAIL("rail"), ARC("arc"), VENOM("venom"),
        INFERNO("inferno"), BEACON("beacon"), SUNDER("sunder"), TREMOR("tremor"),
        DEFEAT("defeat"), ARRIVAL("arrival"), BREACH("breach"),
    }
}
