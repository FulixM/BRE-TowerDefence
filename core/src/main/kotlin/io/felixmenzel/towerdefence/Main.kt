package io.felixmenzel.towerdefence

import com.badlogic.gdx.Gdx
import io.felixmenzel.towerdefence.simulation.FieldProfile
import io.felixmenzel.towerdefence.simulation.MissionSaves
import io.felixmenzel.towerdefence.simulation.RemixRecords

import ktx.app.KtxGame
import ktx.app.KtxScreen
import io.felixmenzel.towerdefence.ui.DefenseScreen

class Main(private val profileName: String = "bre-tower-defence-profile") : KtxGame<KtxScreen>() {
    override fun create() {
        val preferences = Gdx.app.getPreferences(profileName)
        val profile = FieldProfile(preferences.getInteger("xp", 0)) { xp ->
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
        addScreen(DefenseScreen(profile, records, saves) { xp, best ->
            // Close the replayable checkpoint and write the reward in the same preferences flush.
            val previous = preferences.get().toMap()
            try {
                preferences.putInteger("xp", xp)
                if (best != null) preferences.putString("remix.${best.first}", best.second)
                preferences.remove(CHECKPOINT_KEY)
                preferences.flush()
            } catch (failure: RuntimeException) {
                preferences.clear()
                preferences.put(previous)
                throw failure
            }
        })
        setScreen<DefenseScreen>()
    }

    private companion object {
        const val CHECKPOINT_KEY = "mission.checkpoint"
    }
}
