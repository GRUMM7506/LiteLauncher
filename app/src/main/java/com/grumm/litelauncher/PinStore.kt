package com.grumm.litelauncher

import android.content.Context
import androidx.core.content.edit

/** Закреплённые приложения в порядке добавления. */
class PinStore(private val context: Context) {

    var keys = emptyList<String>()
        private set

    /** Первое чтение SharedPreferences — это диск, поэтому только в фоновом потоке. */
    fun load(): List<String> {
        val saved = prefs().getString(KEY_PINS, null).orEmpty()
        return saved.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    fun setLoaded(loaded: List<String>) {
        keys = loaded
    }

    fun isPinned(key: String) = key in keys

    val isFull get() = keys.size >= MAX_PINS

    fun toggle(key: String) = save(if (isPinned(key)) keys - key else keys + key)

    fun removeAll(removed: Collection<String>) = save(keys - removed.toSet())

    private fun save(updated: List<String>) {
        keys = updated
        prefs().edit { putString(KEY_PINS, updated.joinToString(SEPARATOR)) }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val MAX_PINS = 6
        private const val PREFS_NAME = "pins"
        private const val KEY_PINS = "keys"
        private const val SEPARATOR = "\n"
    }
}
