package com.grumm.litelauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.edit
import java.util.concurrent.Executor
import kotlin.math.abs

/** Виджеты на главном экране. Хост создаётся только когда есть хотя бы один виджет. */
class WidgetPanel(
    private val activity: Activity,
    private val container: LinearLayout,
    private val executor: Executor,
    private val mainHandler: Handler,
) {
    private class Placed(val id: Int, var heightPx: Int)

    private val manager by lazy(LazyThreadSafetyMode.NONE) { AppWidgetManager.getInstance(activity) }
    private val host by lazy(LazyThreadSafetyMode.NONE) { LauncherWidgetHost(activity) }
    private val placed = mutableListOf<Placed>()
    private val density = activity.resources.displayMetrics.density
    private var pendingId = NO_WIDGET
    private var started = false

    fun restore(saved: List<Pair<Int, Int>>) {
        saved.forEach { (id, height) ->
            val info = manager.getAppWidgetInfo(id)
            if (info != null) addView(Placed(id, height), info)
        }
        start()
    }

    fun start() {
        if (placed.isEmpty() && !started) return
        started = true
        host.startListening()
    }

    fun stop() {
        if (started) host.stopListening()
    }

    /** Жест, начатый на виджете, остаётся ему: внутри могут быть прокручиваемые списки. */
    fun contains(x: Float, y: Float): Boolean {
        if (placed.isEmpty()) return false
        val location = IntArray(2)
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            child.getLocationOnScreen(location)
            val inside = x >= location[0] && x < location[0] + child.width &&
                y >= location[1] && y < location[1] + child.height
            if (inside) return true
        }
        return false
    }

    fun showPicker() = executor.execute {
        val pm = activity.packageManager
        val providers = manager.installedProviders
        val labels = providers.map { "${appLabel(it.provider.packageName)}: ${it.loadLabel(pm)}" }
        val sorted = providers.indices.sortedBy { labels[it] }
        mainHandler.post { showPickerDialog(sorted.map { providers[it] }, sorted.map { labels[it] }) }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        val id = pendingId
        pendingId = NO_WIDGET
        when {
            id == NO_WIDGET -> Unit
            resultCode != Activity.RESULT_OK -> host.deleteAppWidgetId(id)
            requestCode == REQUEST_BIND -> configureOrPlace(id)
            else -> place(id)
        }
        return true
    }

    private fun appLabel(packageName: String): CharSequence = runCatching {
        val pm = activity.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
    }.getOrDefault(packageName)

    private fun showPickerDialog(providers: List<AppWidgetProviderInfo>, labels: List<String>) {
        if (providers.isEmpty()) {
            Toast.makeText(activity, R.string.no_widgets, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.menu_add_widget)
            .setItems(labels.toTypedArray()) { _, which -> bind(providers[which]) }
            .show()
    }

    private fun bind(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrPlace(id)
            return
        }
        pendingId = id
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        activity.startActivityForResult(intent, REQUEST_BIND)
    }

    private fun configureOrPlace(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure == null) {
            place(id)
            return
        }
        pendingId = id
        try {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
        } catch (_: ActivityNotFoundException) {
            failAdding(id)
        } catch (_: SecurityException) {
            failAdding(id)
        }
    }

    private fun failAdding(id: Int) {
        pendingId = NO_WIDGET
        host.deleteAppWidgetId(id)
        Toast.makeText(activity, R.string.widget_failed, Toast.LENGTH_SHORT).show()
    }

    private fun place(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: return
        val widget = Placed(id, defaultHeight(info))
        addView(widget, info)
        save()
        start()
    }

    private fun defaultHeight(info: AppWidgetProviderInfo) =
        info.minHeight.coerceIn(minHeightPx(), container.height.coerceAtLeast(minHeightPx()))

    private fun minHeightPx() = (MIN_HEIGHT_DP * density).toInt()

    private fun addView(widget: Placed, info: AppWidgetProviderInfo) {
        val view = host.createView(activity, widget.id, info)
        view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, widget.heightPx)
        view.setOnLongClickListener {
            showWidgetMenu(widget, it)
            true
        }
        container.addView(view)
        placed += widget
        reportSize(widget)
    }

    private fun showWidgetMenu(widget: Placed, anchor: View) {
        PopupMenu(activity, anchor).apply {
            menu.add(0, ITEM_TALLER, 0, R.string.menu_widget_taller)
            menu.add(0, ITEM_SHORTER, 1, R.string.menu_widget_shorter)
            menu.add(0, ITEM_REMOVE, 2, R.string.menu_widget_remove)
            setOnMenuItemClickListener { onMenuItem(it.itemId, widget, anchor) }
            show()
        }
    }

    private fun onMenuItem(itemId: Int, widget: Placed, view: View): Boolean {
        val step = (RESIZE_STEP_DP * density).toInt()
        when (itemId) {
            ITEM_TALLER -> resize(widget, view, widget.heightPx + step)
            ITEM_SHORTER -> resize(widget, view, widget.heightPx - step)
            ITEM_REMOVE -> remove(widget, view)
        }
        return true
    }

    private fun resize(widget: Placed, view: View, height: Int) {
        widget.heightPx = height.coerceIn(minHeightPx(), container.height.coerceAtLeast(minHeightPx()))
        view.layoutParams = view.layoutParams.apply { this.height = widget.heightPx }
        reportSize(widget)
        save()
    }

    private fun remove(widget: Placed, view: View) {
        container.removeView(view)
        placed -= widget
        host.deleteAppWidgetId(widget.id)
        save()
    }

    private fun reportSize(widget: Placed) {
        val widthDp = (activity.resources.displayMetrics.widthPixels / density).toInt()
        val heightDp = (widget.heightPx / density).toInt()
        manager.updateAppWidgetOptions(widget.id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        })
    }

    private fun save() {
        val value = placed.joinToString(ENTRY_SEPARATOR) { "${it.id}$FIELD_SEPARATOR${it.heightPx}" }
        prefs(activity).edit { putString(KEY_WIDGETS, value) }
    }

    private class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = LongPressWidgetView(context)
    }

    /**
     * Содержимое виджета забирает касания себе, поэтому долгое нажатие ловим до него.
     * Касание всегда остаётся здесь, иначе на пустом месте виджета сработает ещё и меню главного экрана.
     */
    private class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPress = Runnable {
            fired = true
            performLongClick()
        }
        private var downX = 0f
        private var downY = 0f
        private var fired = false

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            trackLongPress(ev)
            super.dispatchTouchEvent(ev)
            return true
        }

        override fun onInterceptTouchEvent(ev: MotionEvent) = fired

        private fun trackLongPress(ev: MotionEvent) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    downX = ev.x
                    downY = ev.y
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            }
        }
    }

    companion object {
        private const val HOST_ID = 1024
        private const val REQUEST_BIND = 201
        private const val REQUEST_CONFIGURE = 202
        private const val NO_WIDGET = -1
        private const val MIN_HEIGHT_DP = 48
        private const val RESIZE_STEP_DP = 48
        private const val ITEM_TALLER = 1
        private const val ITEM_SHORTER = 2
        private const val ITEM_REMOVE = 3
        private const val PREFS_NAME = "widgets"
        private const val KEY_WIDGETS = "placed"
        private const val ENTRY_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = ":"

        /** Первое чтение SharedPreferences — это диск, поэтому только в фоновом потоке. */
        fun loadSaved(context: Context): List<Pair<Int, Int>> {
            val saved = prefs(context).getString(KEY_WIDGETS, null).orEmpty()
            return saved.split(ENTRY_SEPARATOR).mapNotNull(::parseEntry)
        }

        private fun parseEntry(entry: String): Pair<Int, Int>? {
            val parts = entry.split(FIELD_SEPARATOR)
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val height = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return id to height
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
