package io.felixmenzel.towerdefence.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import io.felixmenzel.towerdefence.rendering.ScreenFonts
import io.felixmenzel.towerdefence.ui.HudPalette.BORDER
import io.felixmenzel.towerdefence.ui.HudPalette.BUTTON
import io.felixmenzel.towerdefence.ui.HudPalette.HOVER
import io.felixmenzel.towerdefence.ui.HudPalette.MUTED
import io.felixmenzel.towerdefence.ui.HudPalette.WHITE

/** Draws HUD layers and resolves clicks against the last rendered layout. */
internal class HudCanvas(private val fonts: ScreenFonts) {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val glyph = GlyphLayout()
    private val buttons = mutableListOf<Button>()
    private val labels = mutableListOf<Label>()
    private var overlayStart = 0
    private var cursor: Vector2? = null

    fun beginFrame(projection: Matrix4, cursor: Vector2?) {
        this.cursor = cursor
        buttons.clear()
        labels.clear()
        shapes.projectionMatrix = projection
        batch.projectionMatrix = projection
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    fun endHud() {
        overlayStart = buttons.size
        shapes.end()
        drawText(0, overlayStart)
    }

    fun beginOverlay() {
        // The overlay covers both the HUD shapes and its text.
        labels.clear()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    fun endOverlay() {
        shapes.end()
        drawText(overlayStart, buttons.size)
    }

    fun click(point: Vector2, overlayOnly: Boolean): Boolean {
        val firstButton = if (overlayOnly) overlayStart else 0
        for (index in buttons.lastIndex downTo firstButton) {
            val button = buttons[index]
            if (button.bounds.contains(point)) {
                if (button.enabled) button.action()
                return true
            }
        }
        return false
    }

    fun line(fromX: Float, fromY: Float, toX: Float, toY: Float, width: Float, color: Color) {
        shapes.color = color
        shapes.rectLine(fromX, fromY, toX, toY, width)
    }

    fun button(title: String, x: Float, y: Float, width: Float, height: Float,
        enabled: Boolean = true, accent: Color = BORDER, scale: Float = 0.9f,
        selected: Boolean = false, labelOffsetY: Float = 0f, action: () -> Unit) {
        val bounds = Rectangle(x, y, width, height)
        val hover = cursor?.let(bounds::contains) == true
        rect(x, y, width, height, if (enabled && (hover || selected)) HOVER else BUTTON)
        rect(x, y, width, 2f, if (enabled) accent else BORDER)
        buttons += Button(title, bounds, enabled, scale, labelOffsetY, action)
    }

    fun rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        shapes.color = color
        shapes.rect(x, y, width, height)
    }

    fun text(value: String, x: Float, y: Float, scale: Float = 1f, color: Color = WHITE, width: Float = 0f) {
        labels += Label(value, x, y, scale, color, width)
    }

    private fun drawText(firstButton: Int, lastButton: Int) {
        batch.begin()
        for (label in labels) {
            val font = fonts.get(label.scale)
            font.color = label.color
            if (label.width > 0f) font.draw(batch, label.text, fonts.snap(label.x), fonts.snap(label.y), label.width, Align.left, true)
            else font.draw(batch, label.text, fonts.snap(label.x), fonts.snap(label.y))
        }
        for (index in firstButton until lastButton) {
            val button = buttons[index]
            val font = fonts.get(button.scale)
            font.color = if (button.enabled) WHITE else MUTED
            glyph.setText(font, button.label)
            font.draw(batch, button.label, fonts.snap(button.bounds.x + (button.bounds.width - glyph.width) / 2f),
                fonts.snap(button.bounds.y + (button.bounds.height + glyph.height) / 2f + button.labelOffsetY))
        }
        batch.end()
    }

    fun dispose() {
        batch.dispose()
        shapes.dispose()
    }

    private data class Label(val text: String, val x: Float, val y: Float, val scale: Float, val color: Color, val width: Float)
    private data class Button(val label: String, val bounds: Rectangle, val enabled: Boolean,
        val scale: Float, val labelOffsetY: Float, val action: () -> Unit)
}
