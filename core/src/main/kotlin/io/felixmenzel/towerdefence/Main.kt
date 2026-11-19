package io.felixmenzel.towerdefence

import com.badlogic.gdx.Gdx
import io.felixmenzel.towerdefence.simulation.FieldProfile
import io.felixmenzel.towerdefence.simulation.MissionSaves
import io.felixmenzel.towerdefence.simulation.RemixRecords
import io.felixmenzel.towerdefence.simulation.ResearchProgress

import ktx.app.KtxGame
import ktx.app.KtxScreen
import io.felixmenzel.towerdefence.ui.DefenseScreen

class Main(private val profileName: String = "bre-tower-defence-profile") : KtxGame<KtxScreen>() {
    override fun create() {
        val preferences = Gdx.app.getPreferences(profileName)
        val profile = FieldProfile(
            preferences.getInteger("xp", 0), preferences.getBoolean(RELAY_HUNTER_KEY, false),
            ResearchProgress.decode(preferences.getString(RESEARCH_KEY, "")),
        ) { xp ->
            preferences.putInteger("xp", xp).flush()
        }
        val records = RemixRecords(
            load = { preferences.getString("remix.$it", "") },
            save = { code, value -> preferences.putString("remix.$code", value).flush() },
        )
        val saves = MissionSaves(
            load = { preferences.getString(CHECKPOINT_KEY, "") },
            write = { value -> preferences.putString(CHECKPOINT_KEY, value).flush() },
        )
        fun persistChanges(update: () -> Unit) {
            val previous = preferences.get().toMap()
            try {
                update()
                preferences.flush()
            } catch (failure: RuntimeException) {
                preferences.clear()
                preferences.put(previous)
                throw failure
            }
        }

        addScreen(DefenseScreen(profile, records, saves,
            sfxEnabled = preferences.getBoolean("audio.sfx", true),
            persistSfx = { enabled -> persistChanges { preferences.putBoolean("audio.sfx", enabled) } },
            persistResearch = { research ->
                persistChanges { preferences.putString(RESEARCH_KEY, research.encode()) }
            },
            persistResult = { xp, best, relayHunter, research ->
                // Keep profile rewards and the checkpoint in the same preferences flush.
                persistChanges {
                    preferences.putInteger("xp", xp)
                    preferences.putBoolean(RELAY_HUNTER_KEY, relayHunter)
                    preferences.putString(RESEARCH_KEY, research.encode())
                    if (best != null) preferences.putString("remix.${best.first}", best.second)
                    preferences.remove(CHECKPOINT_KEY)
                }
            },
        ))
        setScreen<DefenseScreen>()
    }

    private companion object {
        const val RESEARCH_KEY = "workshop.research"
        const val RELAY_HUNTER_KEY = "workshop.relay-hunter"
        const val CHECKPOINT_KEY = "mission.checkpoint"
    }
}
