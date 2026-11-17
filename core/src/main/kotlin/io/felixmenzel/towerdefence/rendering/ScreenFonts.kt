package io.felixmenzel.towerdefence.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** Rasterizes text at framebuffer resolution instead of enlarging a tiny bitmap font. */
class ScreenFonts : Disposable {
    private val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Latin.ttf"))
    private val fonts = mutableMapOf<Int, BitmapFont>()
    private var pixelsPerUnit = 1f

    /** Includes both the fitted viewport scale and the display's HiDPI framebuffer ratio. */
    fun updateDensity(density: Float) {
        require(density.isFinite() && density > 0f)
        if (abs(density - pixelsPerUnit) < 0.0001f) return
        fonts.values.forEach(BitmapFont::dispose)
        fonts.clear()
        pixelsPerUnit = density
    }

    fun get(scale: Float = 1f): BitmapFont {
        require(scale.isFinite() && scale > 0f)
        val size = (18f * scale * pixelsPerUnit).roundToInt().coerceAtLeast(1)
        return fonts.getOrPut(size) {
            val parameters = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = size
                characters = (32..255).map(Int::toChar).joinToString("") + '\u20ac'
                minFilter = TextureFilter.Linear
                magFilter = TextureFilter.Linear
            }
            generator.generateFont(parameters).apply {
                // Coordinates remain in logical game units, but each texel covers one native pixel.
                data.setScale(1f / pixelsPerUnit)
                setUseIntegerPositions(false)
            }
        }
    }

    /** Snap baselines to physical pixels rather than rounding logical coordinates. */
    fun snap(value: Float): Float = round(value * pixelsPerUnit) / pixelsPerUnit

    override fun dispose() {
        fonts.values.forEach(BitmapFont::dispose)
        fonts.clear()
        generator.dispose()
    }
}
