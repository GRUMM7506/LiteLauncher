package com.grumm.litelauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.LruCache
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.Executor

/** Грузит иконки в фоне, масштабирует до размера ячейки и кэширует с ограничением по байтам. */
class IconLoader(
    private val context: Context,
    private val executor: Executor,
    private val mainHandler: Handler,
) {
    @Volatile
    private var sizePx = 0

    var badgeStyle = LauncherSettings.BADGE_DOT

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun setIconSize(sizeDp: Int) {
        val updated = (sizeDp * context.resources.displayMetrics.density).toInt()
        if (updated != sizePx) {
            sizePx = updated
            cache.evictAll()
        }
    }

    /** Ставит иконку над подписью. View может быть переиспользована, актуальность проверяется по тегу. */
    fun bind(entry: AppEntry, target: TextView) {
        bindKeyed(entry.key, target) { renderIcon(entry) }
        slotOf(target).setBadge(badgeCount(entry), animate = false)
    }

    /** Значок папки: до четырёх иконок её приложений. */
    fun bindFolder(folder: GridItem.Folder, target: TextView) {
        bindKeyed(folder.key, target) { renderFolder(folder) }
        slotOf(target).setBadge(folder.apps.sumOf(::badgeCount), animate = false)
    }

    /** Обновляет только значок: иконка и разметка не трогаются. */
    fun refreshBadge(entry: AppEntry, target: TextView) = slotOf(target).setBadge(badgeCount(entry), animate = true)

    fun refreshBadge(folder: GridItem.Folder, target: TextView) =
        slotOf(target).setBadge(folder.apps.sumOf(::badgeCount), animate = true)

    private fun badgeCount(entry: AppEntry) =
        if (badgeStyle == LauncherSettings.BADGE_OFF) 0 else NotificationBadges.count(entry.info)

    private fun bindKeyed(key: String, target: TextView, render: () -> Bitmap?) {
        target.tag = key
        val slot = slotOf(target)
        val cached = cache.get(key)
        slot.show(cached)
        if (cached == null) executor.execute { loadAndDeliver(key, target, slot, render) }
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() = cache.evictAll()

    private fun slotOf(target: TextView): IconSlot {
        val current = target.compoundDrawablesRelative[1] as? IconSlot
        if (current != null && current.size == sizePx) return current
        return IconSlot(sizePx, ::badgeStyle).also { target.setCompoundDrawablesRelative(null, it, null, null) }
    }

    private fun loadAndDeliver(key: String, target: TextView, slot: IconSlot, render: () -> Bitmap?) {
        if (target.tag != key) return
        val bitmap = cache.get(key) ?: render()?.also { cache.put(key, it) } ?: return
        mainHandler.post {
            if (target.tag == key) slot.fadeIn(bitmap)
        }
    }

    /** Сторонний ресурс может оказаться битым — лаунчер из-за этого падать не должен. */
    private fun renderIcon(entry: AppEntry): Bitmap? = runCatching {
        val density = context.resources.displayMetrics.densityDpi
        entry.info.getBadgedIcon(density).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun renderFolder(folder: GridItem.Folder): Bitmap? = runCatching {
        val size = sizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val corner = size * FOLDER_CORNER
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOLDER_COLOR }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), corner, corner, background)
        val density = context.resources.displayMetrics.densityDpi
        val gap = (size * FOLDER_GAP).toInt()
        val mini = (size - gap * 3) / 2
        folder.apps.take(GridItem.Folder.PREVIEW_COUNT).forEachIndexed { index, app ->
            val left = gap + (index % 2) * (mini + gap)
            val top = gap + (index / 2) * (mini + gap)
            val icon = app.info.getBadgedIcon(density)
            icon.setBounds(left, top, left + mini, top + mini)
            icon.draw(canvas)
        }
        bitmap
    }.getOrNull()

    /** Постоянное место под иконку: смена картинки не вызывает пересчёт разметки сетки. */
    private class IconSlot(val size: Int, private val style: () -> Int) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var bitmap: Bitmap? = null
        private var fade: ValueAnimator? = null
        private var badge = 0
        private var badgeScale = 1f
        private var pop: ValueAnimator? = null

        init {
            setBounds(0, 0, size, size)
        }

        fun show(icon: Bitmap?) {
            fade?.cancel()
            paint.alpha = 255
            bitmap = icon
            invalidateSelf()
        }

        fun fadeIn(icon: Bitmap) {
            show(icon)
            paint.alpha = 0
            fade = ValueAnimator.ofInt(0, 255).apply {
                duration = FADE_MS
                addUpdateListener {
                    paint.alpha = it.animatedValue as Int
                    invalidateSelf()
                }
                start()
            }
        }

        /** Новый значок появляется с лёгким отскоком. */
        fun setBadge(count: Int, animate: Boolean) {
            val appeared = badge == 0 && count > 0
            if (count == badge) return
            badge = count
            pop?.cancel()
            badgeScale = 1f
            if (appeared && animate) startPop()
            invalidateSelf()
        }

        private fun startPop() {
            pop = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = BADGE_POP_MS
                interpolator = OvershootInterpolator()
                addUpdateListener {
                    badgeScale = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            bitmap?.let { canvas.drawBitmap(it, null, bounds, paint) }
            if (badge > 0 && style() != LauncherSettings.BADGE_OFF) drawBadge(canvas)
        }

        private fun drawBadge(canvas: Canvas) {
            val numbered = style() == LauncherSettings.BADGE_NUMBER
            val radius = size * if (numbered) BADGE_NUMBER_RADIUS else BADGE_DOT_RADIUS
            val x = size - radius
            canvas.drawCircle(x, radius, radius * badgeScale, BADGE_PAINT)
            if (!numbered || badgeScale < 1f) return
            val text = if (badge > MAX_BADGE) "$MAX_BADGE+" else badge.toString()
            BADGE_TEXT.textSize = radius * if (text.length > 1) 1.1f else 1.4f
            canvas.drawText(text, x, radius - (BADGE_TEXT.ascent() + BADGE_TEXT.descent()) / 2, BADGE_TEXT)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth() = size

        override fun getIntrinsicHeight() = size
    }

    private companion object {
        const val CACHE_BYTES = 4 * 1024 * 1024
        const val FADE_MS = 140L
        const val FOLDER_COLOR = 0x40FFFFFF
        const val FOLDER_CORNER = 0.26f
        const val FOLDER_GAP = 0.09f
        const val BADGE_DOT_RADIUS = 0.11f
        const val BADGE_NUMBER_RADIUS = 0.17f
        const val BADGE_POP_MS = 240L
        const val MAX_BADGE = 9
        val BADGE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() }
        val BADGE_TEXT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
}
