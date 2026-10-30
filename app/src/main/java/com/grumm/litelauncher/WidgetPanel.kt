package com.grumm.litelauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.edit
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.ceil

/** Виджеты на главном экране. Хост создаётся только когда виджеты действительно нужны. */
class WidgetPanel(
    private val activity: Activity,
    private val grid: WidgetGridLayout,
    private val executor: Executor,
    private val mainHandler: Handler,
) : WidgetEditor.Listener {

    /** Виджет, уже привязанный к хосту: его можно показать вживую до добавления на экран. */
    class LivePreview(val id: Int, val view: AppWidgetHostView)

    private val manager by lazy(LazyThreadSafetyMode.NONE) { AppWidgetManager.getInstance(activity) }
    private val host by lazy(LazyThreadSafetyMode.NONE) { LauncherWidgetHost(activity) }
    private val placed = mutableMapOf<View, Int>()
    private val density = activity.resources.displayMetrics.density
    private var pendingId = NO_WIDGET
    private var pendingSpanX = 1
    private var pendingSpanY = 1
    private var started = false
    private var editor: WidgetEditor? = null
    private var picker: WidgetPicker? = null

    val isEditing get() = editor != null

    fun restore(saved: List<WidgetCellEntry>) {
        saved.forEach { entry ->
            val info = manager.getAppWidgetInfo(entry.id)
            if (info != null) addView(entry.id, info, entry.cell)
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

    /** Закрывает всё, что открыто поверх главного экрана. */
    fun dismissOverlays() {
        endEditing()
        picker?.dismiss()
    }

    fun endEditing() {
        val current = editor ?: return
        editor = null
        grid.removeView(current)
    }

    /** Жест, начатый на виджете, остаётся ему: внутри могут быть прокручиваемые списки. */
    fun contains(x: Float, y: Float): Boolean {
        if (placed.isEmpty()) return false
        val location = IntArray(2)
        for (child in placed.keys) {
            child.getLocationOnScreen(location)
            val inside = x >= location[0] && x < location[0] + child.width &&
                y >= location[1] && y < location[1] + child.height
            if (inside) return true
        }
        return false
    }

    fun showPicker() {
        started = true
        host.startListening()
        executor.execute {
            val rows = WidgetPicker.loadRows(activity, manager.installedProviders)
            mainHandler.post { openPicker(rows) }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        val id = pendingId
        pendingId = NO_WIDGET
        when {
            id == NO_WIDGET -> Unit
            resultCode != Activity.RESULT_OK -> host.deleteAppWidgetId(id)
            requestCode == REQUEST_BIND -> configureOrPlace(id)
            else -> place(id, pendingSpanX, pendingSpanY)
        }
        return true
    }

    /** Размер по умолчанию в ячейках: столько, сколько просит сам виджет. */
    fun defaultSpan(info: AppWidgetProviderInfo): Pair<Int, Int> =
        spanFor(info.minWidth) to spanFor(info.minHeight)

    fun limitsOf(info: AppWidgetProviderInfo): WidgetEditor.Limits {
        val (defaultX, defaultY) = defaultSpan(info)
        val canResizeX = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val canResizeY = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val minX = if (info.minResizeWidth > 0) spanFor(info.minResizeWidth) else defaultX
        val minY = if (info.minResizeHeight > 0) spanFor(info.minResizeHeight) else defaultY
        return WidgetEditor.Limits(canResizeX, canResizeY, minX.coerceAtMost(defaultX), minY.coerceAtMost(defaultY))
    }

    val maxRows get() = grid.rows

    override fun onCellChanged(view: View) {
        val id = placed[view] ?: return
        reportSize(id, view as AppWidgetHostView, cellOf(view).spanX, cellOf(view).spanY)
        save()
    }

    override fun onRemove(view: View) {
        val id = placed.remove(view) ?: return
        endEditing()
        grid.removeView(view)
        host.deleteAppWidgetId(id)
        save()
    }

    override fun onDone() = endEditing()

    private fun openPicker(rows: List<WidgetPicker.Row>) {
        if (rows.isEmpty()) {
            Toast.makeText(activity, R.string.no_widgets, Toast.LENGTH_SHORT).show()
            return
        }
        picker = WidgetPicker(activity, this, executor, mainHandler, rows) { picker = null }.also { it.show() }
    }

    /**
     * Привязывает виджет сразу, если система разрешает, и отдаёт живой вид для предпросмотра.
     * Виджеты с настройкой до настройки выглядят пустыми, поэтому для них живого вида нет.
     */
    fun createLivePreview(info: AppWidgetProviderInfo): LivePreview? {
        if (info.configure != null) return null
        val id = host.allocateAppWidgetId()
        if (!manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            host.deleteAppWidgetId(id)
            return null
        }
        return LivePreview(id, host.createView(activity, id, info))
    }

    fun discardPreview(preview: LivePreview) = host.deleteAppWidgetId(preview.id)

    fun addPreview(preview: LivePreview, spanX: Int, spanY: Int) = place(preview.id, spanX, spanY)

    fun add(info: AppWidgetProviderInfo, spanX: Int, spanY: Int) {
        pendingSpanX = spanX
        pendingSpanY = spanY
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

    fun reportSize(id: Int, view: AppWidgetHostView, spanX: Int, spanY: Int) {
        val widthDp = spanX * cellSizePx() / density
        val heightDp = spanY * cellSizePx() / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            return
        }
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.toInt())
        })
    }

    /** До первой разметки сетки её размер неизвестен, поэтому считаем от ширины экрана. */
    fun cellSizePx(): Int {
        if (grid.cellSize > 0) return grid.cellSize
        val padding = (HOME_PADDING_DP * 2 * density).toInt()
        return (activity.resources.displayMetrics.widthPixels - padding) / WidgetGridLayout.COLUMNS
    }

    private fun spanFor(sizePx: Int) =
        ceil(sizePx / cellSizePx().toFloat()).toInt().coerceIn(1, WidgetGridLayout.COLUMNS)

    private fun configureOrPlace(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure == null) {
            place(id, pendingSpanX, pendingSpanY)
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

    private fun place(id: Int, spanX: Int, spanY: Int) {
        val info = manager.getAppWidgetInfo(id) ?: return
        val cell = grid.findFree(spanX, spanY.coerceAtMost(grid.rows))
        if (cell == null) {
            host.deleteAppWidgetId(id)
            Toast.makeText(activity, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        addView(id, info, cell)
        save()
        start()
    }

    private fun addView(id: Int, info: AppWidgetProviderInfo, cell: WidgetCell) {
        val view = host.createView(activity, id, info) as AppWidgetHostView
        view.layoutParams = WidgetGridLayout.CellParams(cell)
        view.setOnLongClickListener {
            startEditing(view, info)
            true
        }
        grid.addView(view)
        placed[view] = id
        reportSize(id, view, cell.spanX, cell.spanY)
    }

    private fun startEditing(view: View, info: AppWidgetProviderInfo) {
        endEditing()
        val created = WidgetEditor(activity, grid, view, limitsOf(info), this)
        grid.addView(created)
        editor = created
        (view as? LongPressWidgetView)?.let { created.startMove(it.downRawX, it.downRawY) }
    }

    private fun forwardDrag(event: MotionEvent) {
        editor?.handleTouch(event)
    }

    private fun cellOf(view: View) = (view.layoutParams as WidgetGridLayout.CellParams).cell

    private fun save() {
        val value = placed.entries.joinToString(ENTRY_SEPARATOR) { (view, id) ->
            val cell = cellOf(view)
            listOf(id, cell.x, cell.y, cell.spanX, cell.spanY).joinToString(FIELD_SEPARATOR)
        }
        prefs(activity).edit { putString(KEY_WIDGETS, value) }
    }

    private inner class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = LongPressWidgetView(context, ::forwardDrag)
    }

    /**
     * Содержимое виджета забирает касания себе, поэтому долгое нажатие ловим до него.
     * Касание всегда остаётся здесь, иначе на пустом месте виджета сработает ещё и меню главного экрана.
     */
    private class LongPressWidgetView(
        context: Context,
        private val onDrag: (MotionEvent) -> Unit,
    ) : AppWidgetHostView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPress = Runnable {
            fired = true
            performLongClick()
        }
        private var downX = 0f
        private var downY = 0f
        private var fired = false
        var downRawX = 0f
            private set
        var downRawY = 0f
            private set

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            trackLongPress(ev)
            super.dispatchTouchEvent(ev)
            return true
        }

        override fun onInterceptTouchEvent(ev: MotionEvent) = fired

        // После долгого нажатия палец продолжает двигать виджет, уже через редактор.
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (fired) onDrag(event)
            return true
        }

        private fun trackLongPress(ev: MotionEvent) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    downX = ev.x
                    downY = ev.y
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            }
        }
    }

    /** Запись о сохранённом виджете. */
    class WidgetCellEntry(val id: Int, val cell: WidgetCell)

    companion object {
        private const val HOST_ID = 1024
        private const val REQUEST_BIND = 201
        private const val REQUEST_CONFIGURE = 202
        private const val NO_WIDGET = -1
        private const val HOME_PADDING_DP = 8
        private const val PREFS_NAME = "widgets"
        private const val KEY_WIDGETS = "placed"
        private const val ENTRY_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = ":"

        /** Первое чтение SharedPreferences — это диск, поэтому только в фоновом потоке. */
        fun loadSaved(context: Context): List<WidgetCellEntry> {
            val saved = prefs(context).getString(KEY_WIDGETS, null).orEmpty()
            var nextLegacyRow = 0
            return saved.split(ENTRY_SEPARATOR).mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR).map { it.toIntOrNull() ?: return@mapNotNull null }
                when (parts.size) {
                    5 -> WidgetCellEntry(parts[0], WidgetCell(parts[1], parts[2], parts[3], parts[4]))
                    2 -> legacyEntry(context, parts[0], parts[1], nextLegacyRow).also {
                        nextLegacyRow += it.cell.spanY
                    }
                    else -> null
                }
            }
        }

        /** В версии 1.0.0 виджеты лежали стопкой на всю ширину и хранили только высоту. */
        private fun legacyEntry(context: Context, id: Int, heightPx: Int, row: Int): WidgetCellEntry {
            val cell = context.resources.displayMetrics.widthPixels / WidgetGridLayout.COLUMNS
            val spanY = ceil(heightPx / cell.toFloat()).toInt().coerceAtLeast(1)
            return WidgetCellEntry(id, WidgetCell(0, row, WidgetGridLayout.COLUMNS, spanY))
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
