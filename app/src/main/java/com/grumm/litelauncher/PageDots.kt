package com.grumm.litelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** Индикатор страниц: яркость точек плавно следует за позицией прокрутки. */
class PageDots(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val radius = RADIUS_DP * density
    private val spacing = SPACING_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var count = 0
    private var position = 0f

    fun setCount(pages: Int) {
        if (pages != count) {
            count = pages
            invalidate()
        }
    }

    fun setPosition(page: Float) {
        position = page
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (count < 2) return
        val startX = (width - (count - 1) * spacing) / 2
        for (index in 0 until count) {
            val closeness = 1f - min(1f, abs(index - position))
            paint.alpha = (MIN_ALPHA + (255 - MIN_ALPHA) * closeness).toInt()
            canvas.drawCircle(startX + index * spacing, height / 2f, radius * (1f + 0.25f * closeness), paint)
        }
    }

    private companion object {
        const val RADIUS_DP = 3f
        const val SPACING_DP = 16f
        const val MIN_ALPHA = 90
    }
}
