package com.grumm.litelauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Фирменные обои: поле ячеек, как в сетке приложений, среди которых светится большая буква L.
 * Рисуются кодом, поэтому подходят к любому экрану и не занимают места в APK.
 */
class ThemeArt(val width: Int, val height: Int, val night: Boolean) {

    private class Tile(val rect: RectF, val phase: Float, val color: Int)

    private val palette = if (night) NIGHT else DAY
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cell = width / COLUMNS.toFloat()
    private val radius = cell * TILE_RADIUS
    private val rows = ceil(height / cell).toInt()
    private val litTiles = createLitTiles()

    /** Фон без светящихся ячеек: они рисуются поверх и в живых обоях меняют яркость. */
    val base: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { drawBase(Canvas(it)) }

    /** Неподвижный вариант для обычных обоев. */
    fun renderStatic(): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        for (tile in litTiles) drawLit(canvas, tile, STATIC_BRIGHTNESS)
        return result
    }

    /** Кадр живых обоев: по букве L медленно проходит волна света. */
    fun drawFrame(canvas: Canvas, seconds: Float) {
        canvas.drawBitmap(base, 0f, 0f, null)
        val wave = seconds / WAVE_PERIOD_SEC * 2f * PI.toFloat()
        for (tile in litTiles) {
            val brightness = WAVE_MIN + (1f - WAVE_MIN) * (0.5f + 0.5f * sin(wave - tile.phase))
            drawLit(canvas, tile, brightness)
        }
    }

    private fun drawBase(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, 0f, width * 0.4f, height.toFloat(),
            palette.top, palette.bottom, Shader.TileMode.CLAMP,
        )
        canvas.drawPaint(paint)
        drawGlow(canvas, width * 0.1f, height * 0.42f, width * 1.1f, palette.glowA)
        drawGlow(canvas, width * 1.0f, height * 0.92f, width * 1.2f, palette.glowB)
        paint.shader = null
        paint.color = palette.dimTile
        for (row in 0 until rows) {
            for (column in 0 until COLUMNS) canvas.drawRoundRect(tileRect(column, row), radius, radius, paint)
        }
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val transparent = color and 0x00FFFFFF
        paint.shader = RadialGradient(x, y, size, color, transparent, Shader.TileMode.CLAMP)
        canvas.drawPaint(paint)
    }

    private fun drawLit(canvas: Canvas, tile: Tile, brightness: Float) {
        paint.color = tile.color
        paint.alpha = (palette.litAlpha * brightness).toInt()
        canvas.drawRoundRect(tile.rect, radius, radius, paint)
    }

    /** Буква занимает середину экрана: сверху часы, снизу закреплённые приложения. */
    private fun createLitTiles(): List<Tile> {
        val firstRow = (rows * L_TOP).toInt()
        val lastRow = (rows * L_BOTTOM).toInt()
        val stem = (firstRow..lastRow).map { L_COLUMN to it }
        val foot = (L_COLUMN + 1 until COLUMNS - 1).map { it to lastRow }
        val cells = stem + foot
        return cells.mapIndexed { index, (column, row) ->
            val position = index / (cells.size - 1).coerceAtLeast(1).toFloat()
            Tile(tileRect(column, row), position * WAVE_SPREAD, mix(palette.litStart, palette.litEnd, position))
        }
    }

    private fun tileRect(column: Int, row: Int): RectF {
        val inset = cell * (1f - TILE_SIZE) / 2f
        val left = column * cell + inset
        val top = row * cell + inset
        return RectF(left, top, left + cell * TILE_SIZE, top + cell * TILE_SIZE)
    }

    private fun mix(from: Int, to: Int, amount: Float) = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )

    private class Palette(
        val top: Int,
        val bottom: Int,
        val glowA: Int,
        val glowB: Int,
        val dimTile: Int,
        val litStart: Int,
        val litEnd: Int,
        val litAlpha: Int,
    )

    companion object {
        private const val COLUMNS = 5
        private const val TILE_SIZE = 0.62f
        private const val TILE_RADIUS = 0.16f
        private const val L_COLUMN = 1
        private const val L_TOP = 0.27f
        private const val L_BOTTOM = 0.72f
        private const val STATIC_BRIGHTNESS = 1f
        private const val WAVE_MIN = 0.35f
        private const val WAVE_PERIOD_SEC = 9f
        private const val WAVE_SPREAD = 3.2f

        private val NIGHT = Palette(
            top = 0xFF12172E.toInt(),
            bottom = 0xFF1B2747.toInt(),
            glowA = 0x402BC4A8,
            glowB = 0x552F6BE0,
            dimTile = 0x0AFFFFFF,
            litStart = 0xFF8CF7DC.toInt(),
            litEnd = 0xFF3CC4EC.toInt(),
            litAlpha = 150,
        )

        private val DAY = Palette(
            top = 0xFFF2F7F6.toInt(),
            bottom = 0xFFDCEAF4.toInt(),
            glowA = 0x559CEBD8,
            glowB = 0x668CC8F0,
            dimTile = 0x20FFFFFF,
            litStart = 0xFF3CCFAE.toInt(),
            litEnd = 0xFF2B9CE0.toInt(),
            litAlpha = 120,
        )

        fun isNight(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
