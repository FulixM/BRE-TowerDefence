package io.felixmenzel.towerdefence

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.felixmenzel.towerdefence.simulation.FixedStepLoop
import io.felixmenzel.towerdefence.simulation.GameSimulation
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.async.KtxAsync
import ktx.graphics.use

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(FirstScreen())
        setScreen<FirstScreen>()
    }
}

class FirstScreen : KtxScreen {
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()


    private val simulation = GameSimulation()
    private val fixedStepLoop = FixedStepLoop(
        updateSimulation = simulation::update,
    )

    override fun render(delta: Float) {
        fixedStepLoop.advance(
            frameTimeSeconds = delta.toDouble(),
        )

        renderWorld()
    }

        private fun renderWorld() {
            clearScreen(
                red = 0.7f,
                green = 0.7f,
                blue = 0.7f,
            )

            batch.use {
                it.draw(
                    image,
                    simulation.enemyX.toFloat(),
                    160f,
                )
            }
        }


        override fun resume() {
            fixedStepLoop.reset()
        }

        override fun dispose() {
            image.disposeSafely()
            batch.disposeSafely()
        }
    }
