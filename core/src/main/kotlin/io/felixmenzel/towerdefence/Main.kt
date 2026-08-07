package io.felixmenzel.towerdefence

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
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
    private val enemyTexture = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val towerTexture = Texture("img.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()

    private val simulation = GameSimulation()
    private val fixedStepLoop =
        FixedStepLoop(
            updateSimulation = simulation::update,
        )

    override fun render(delta: Float) {
        fixedStepLoop.advance(
            frameTimeSeconds = delta.toDouble(),
        )

        renderWorld(
            alpha = fixedStepLoop.interpolationAlpha,
        )
        updateDebugTitle()
    }

    private fun renderWorld(alpha: Double) {
        clearScreen(
            red = 0.7f,
            green = 0.7f,
            blue = 0.7f,
        )
        batch.use { batch ->
            for (enemy in simulation.enemies) {
                if (!enemy.isAlive || enemy.hasReachedGoal) {
                    continue
                }

                val renderedX = enemy.interpolatedX(alpha)
                val renderedY = enemy.interpolatedY(alpha)

                batch.draw(
                    enemyTexture,
                    renderedX.toFloat(),
                    renderedY.toFloat(),
                    64f,
                    64f,
                )
            }

            batch.draw(
                towerTexture,
                (simulation.tower.x - 32.0).toFloat(),
                (simulation.tower.y - 32.0).toFloat(),
                64f,
                64f,
            )
        }
        shapeRenderer.projectionMatrix = batch.projectionMatrix

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE

        shapeRenderer.circle(
            simulation.tower.x.toFloat(),
            simulation.tower.y.toFloat(),
            simulation.tower.range.toFloat(),
            64,
        )
        shapeRenderer.end()
    }

    override fun resume() {
        fixedStepLoop.reset()
    }

    override fun dispose() {
        enemyTexture.disposeSafely()
        towerTexture.disposeSafely()
        shapeRenderer.disposeSafely()
        batch.disposeSafely()
    }

    private fun updateDebugTitle() {
        Gdx.graphics.setTitle(
            "Lebende Gegner: ${simulation.enemies.count { it.isAlive }}" +
                "HP: ${
                    simulation.enemies
                        .firstOrNull { it.isAlive }
                        ?.health
                        ?.toString() ?: "keiner"
                }",
        )
    }
}
