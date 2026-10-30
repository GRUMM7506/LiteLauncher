package com.grumm.litelauncher

import android.content.Context
import androidx.core.content.edit

/** Настройки внешнего вида и поведения. Первое чтение — это диск, поэтому только в фоновом потоке. */
data class LauncherSettings(
    val columns: Int = 5,
    val rows: Int = 7,
    val iconSizeDp: Int = 52,
    val showLabels: Boolean = true,
    val scrimPercent: Int = 70,
    val clockSizeSp: Int = 72,
    val motion: Int = MOTION_NORMAL,
    val haptics: Boolean = true,
    val autoKeyboard: Boolean = false,
    val swipeDownShade: Boolean = true,
    val hidden: Set<String> = emptySet(),
) {
    val springOmega get() = when (motion) {
        MOTION_RELAXED -> 14f
        MOTION_FAST -> 26f
        else -> 19f
    }

    fun store(context: Context) {
        prefs(context).edit {
            putInt(KEY_COLUMNS, columns)
            putInt(KEY_ROWS, rows)
            putInt(KEY_ICON, iconSizeDp)
            putBoolean(KEY_LABELS, showLabels)
            putInt(KEY_SCRIM, scrimPercent)
            putInt(KEY_CLOCK, clockSizeSp)
            putInt(KEY_MOTION, motion)
            putBoolean(KEY_HAPTICS, haptics)
            putBoolean(KEY_KEYBOARD, autoKeyboard)
            putBoolean(KEY_SHADE, swipeDownShade)
            putStringSet(KEY_HIDDEN, hidden)
        }
    }

    companion object {
        const val MOTION_RELAXED = 0
        const val MOTION_NORMAL = 1
        const val MOTION_FAST = 2

        private const val PREFS_NAME = "settings"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_ROWS = "rows"
        private const val KEY_ICON = "icon_size"
        private const val KEY_LABELS = "labels"
        private const val KEY_SCRIM = "scrim"
        private const val KEY_CLOCK = "clock_size"
        private const val KEY_MOTION = "motion"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_KEYBOARD = "auto_keyboard"
        private const val KEY_SHADE = "swipe_down_shade"
        private const val KEY_HIDDEN = "hidden"

        fun load(context: Context): LauncherSettings {
            val prefs = prefs(context)
            val defaults = LauncherSettings()
            return LauncherSettings(
                columns = prefs.getInt(KEY_COLUMNS, defaults.columns),
                rows = prefs.getInt(KEY_ROWS, defaults.rows),
                iconSizeDp = prefs.getInt(KEY_ICON, defaults.iconSizeDp),
                showLabels = prefs.getBoolean(KEY_LABELS, defaults.showLabels),
                scrimPercent = prefs.getInt(KEY_SCRIM, defaults.scrimPercent),
                clockSizeSp = prefs.getInt(KEY_CLOCK, defaults.clockSizeSp),
                motion = prefs.getInt(KEY_MOTION, defaults.motion),
                haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
                autoKeyboard = prefs.getBoolean(KEY_KEYBOARD, defaults.autoKeyboard),
                swipeDownShade = prefs.getBoolean(KEY_SHADE, defaults.swipeDownShade),
                hidden = prefs.getStringSet(KEY_HIDDEN, null).orEmpty().toSet(),
            )
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
