package com.grumm.litelauncher

import android.app.WallpaperManager
import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.Executor

/** Обычные обои под текущую тему: Android 12 не умеет сам менять картинку при смене темы. */
object ThemedWallpaper {
    private const val PREFS_NAME = "themed_wallpaper"
    /** Номер в ключе меняется вместе с рисунком, чтобы обновление приложения перерисовало обои. */
    private const val KEY_APPLIED = "applied_mode_2"
    private const val MODE_NONE = 0
    private const val MODE_DAY = 1
    private const val MODE_NIGHT = 2

    /** Ставит картинку, если включено и нужная ещё не стоит. Безопасно вызывать часто. */
    fun sync(context: Context, executor: Executor, enabled: Boolean, width: Int, height: Int) {
        val appContext = context.applicationContext
        val wanted = when {
            !enabled -> MODE_NONE
            ThemeArt.isNight(context) -> MODE_NIGHT
            else -> MODE_DAY
        }
        executor.execute {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_APPLIED, MODE_NONE) != wanted) {
                if (wanted != MODE_NONE) apply(appContext, width, height, wanted == MODE_NIGHT)
                prefs.edit { putInt(KEY_APPLIED, wanted) }
            }
        }
    }

    private fun apply(context: Context, width: Int, height: Int, night: Boolean) {
        val art = ThemeArt(width, height, night)
        val bitmap = art.renderStatic()
        runCatching {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
        }
        bitmap.recycle()
        art.base.recycle()
    }
}
