package io.felixmenzel.towerdefence

import ktx.app.KtxGame
import ktx.app.KtxScreen
import io.felixmenzel.towerdefence.ui.DefenseScreen

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        addScreen(DefenseScreen())
        setScreen<DefenseScreen>()
    }
}
