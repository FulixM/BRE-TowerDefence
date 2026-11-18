package io.felixmenzel.towerdefence

import com.badlogic.gdx.Gdx
import io.felixmenzel.towerdefence.simulation.FieldProfile

import ktx.app.KtxGame
import ktx.app.KtxScreen
import io.felixmenzel.towerdefence.ui.DefenseScreen

class Main(private val profileName: String = "bre-tower-defence-profile") : KtxGame<KtxScreen>() {
    override fun create() {
        val preferences = Gdx.app.getPreferences(profileName)
        val profile = FieldProfile(preferences.getInteger("xp", 0)) { xp ->
            preferences.putInteger("xp", xp).flush()
        }
        addScreen(DefenseScreen(profile))
        setScreen<DefenseScreen>()
    }
}
