package com.grumm.litelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.LruCache
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.Executor

/** Грузит иконки в фоне, масштабирует до размера ячейки и кэширует с ограничением по байтам. */
class IconLoader(
    private val context: Context,
    private val executor: Executor,
    private val mainHandler: Handler,
) {
    private val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val placeholder = ColorDrawable(0).apply { setBounds(0, 0, sizePx, sizePx) }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Ставит иконку в [target]. View может быть переиспользована, актуальность проверяется по тегу. */
    fun bind(entry: AppEntry, target: TextView, onTop: Boolean) {
        target.tag = entry.key
        val cached = cache.get(entry.key)
        if (cached != null) {
            target.setIcon(cached.toDrawable(), onTop)
            return
        }
        target.setIcon(placeholder, onTop)
        executor.execute { loadAndDeliver(entry, target, onTop) }
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() = cache.evictAll()

    private fun loadAndDeliver(entry: AppEntry, target: TextView, onTop: Boolean) {
        if (target.tag != entry.key) return
        val bitmap = cache.get(entry.key) ?: render(entry)?.also { cache.put(entry.key, it) } ?: return
        mainHandler.post {
            if (target.tag == entry.key) target.setIcon(bitmap.toDrawable(), onTop)
        }
    }

    /** Сторонний ресурс может оказаться битым — лаунчер из-за этого падать не должен. */
    private fun render(entry: AppEntry): Bitmap? = runCatching {
        val density = context.resources.displayMetrics.densityDpi
        entry.info.getBadgedIcon(density).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun Bitmap.toDrawable(): Drawable =
        BitmapDrawable(context.resources, this).apply { setBounds(0, 0, sizePx, sizePx) }

    private fun TextView.setIcon(icon: Drawable, onTop: Boolean) {
        if (onTop) setCompoundDrawablesRelative(null, icon, null, null)
        else setCompoundDrawablesRelative(icon, null, null, null)
    }

    private companion object {
        const val ICON_SIZE_DP = 48
        const val CACHE_BYTES = 4 * 1024 * 1024
    }
}
