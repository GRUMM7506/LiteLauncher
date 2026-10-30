package com.grumm.litelauncher

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import java.util.concurrent.Executor

/** Сообщает, нужен ли на главном экране тёмный текст: на светлых обоях белый не читается. */
class WallpaperContrast(
    context: Context,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val onChanged: (darkText: Boolean) -> Unit,
) {
    private val manager = WallpaperManager.getInstance(context)
    private val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) deliver(colors)
    }

    fun start() {
        manager.addOnColorsChangedListener(listener, mainHandler)
        executor.execute {
            val colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            mainHandler.post { deliver(colors) }
        }
    }

    fun stop() = manager.removeOnColorsChangedListener(listener)

    private fun deliver(colors: WallpaperColors?) {
        onChanged(supportsDarkText(colors))
    }

    private fun supportsDarkText(colors: WallpaperColors?): Boolean {
        if (colors == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }
}
