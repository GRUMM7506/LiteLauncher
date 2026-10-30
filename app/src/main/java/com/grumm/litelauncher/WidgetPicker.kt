package com.grumm.litelauncher

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Collator
import java.util.concurrent.Executor

/** Выбор виджета: список с картинками, а по нажатию — предпросмотр в настоящем размере. */
class WidgetPicker(
    private val activity: Activity,
    private val panel: WidgetPanel,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val rows: List<Row>,
    private val onClosed: () -> Unit,
) {
    /** Строка списка: заголовок приложения или виджет. */
    class Row(val appLabel: String, val label: String, val info: AppWidgetProviderInfo?)

    private val dialog = Dialog(activity, R.style.Theme_LiteLauncher_Picker)
    private val root = LayoutInflater.from(activity).inflate(R.layout.dialog_widget_picker, null)
    private val list = root.findViewById<RecyclerView>(R.id.picker_list)
    private val detail = root.findViewById<View>(R.id.picker_detail)
    private val previewFrame = root.findViewById<FrameLayout>(R.id.picker_preview)
    private val detailTitle = root.findViewById<TextView>(R.id.picker_detail_title)
    private val sizeLabel = root.findViewById<TextView>(R.id.picker_size)
    private val density = activity.resources.displayMetrics.density
    private val thumbnails = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private var chosen: AppWidgetProviderInfo? = null
    private var live: WidgetPanel.LivePreview? = null
    private var spanX = 1
    private var spanY = 1

    init {
        setUpList()
        root.findViewById<View>(R.id.picker_add).setOnClickListener { addChosen() }
        root.findViewById<View>(R.id.picker_wider).setOnClickListener { resize(1, 0) }
        root.findViewById<View>(R.id.picker_narrower).setOnClickListener { resize(-1, 0) }
        root.findViewById<View>(R.id.picker_taller).setOnClickListener { resize(0, 1) }
        root.findViewById<View>(R.id.picker_shorter).setOnClickListener { resize(0, -1) }
        dialog.setContentView(root)
        dialog.setOnKeyListener { _, keyCode, event -> onKey(keyCode, event) }
        dialog.setOnDismissListener {
            discardLive()
            thumbnails.evictAll()
            onClosed()
        }
    }

    fun show() = dialog.show()

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun setUpList() {
        val manager = GridLayoutManager(activity, COLUMNS)
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (rows[position].info == null) COLUMNS else 1
        }
        list.layoutManager = manager
        list.adapter = RowAdapter()
    }

    private fun onKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK || !detail.isVisible) return false
        if (event.action == KeyEvent.ACTION_UP) showList()
        return true
    }

    private fun showList() {
        discardLive()
        chosen = null
        detail.isVisible = false
        list.isVisible = true
    }

    private fun showDetail(row: Row) {
        val info = row.info ?: return
        chosen = info
        val (defaultX, defaultY) = panel.defaultSpan(info)
        spanX = defaultX
        spanY = defaultY.coerceAtMost(panel.maxRows)
        detailTitle.text = row.label
        list.isVisible = false
        detail.isVisible = true
        previewFrame.removeAllViews()
        live = panel.createLivePreview(info)
        val shown = live?.view ?: staticPreview(info)
        previewFrame.addView(shown)
        previewFrame.addView(touchShield())
        applySize()
    }

    /** Предпросмотр не должен открывать приложение по нажатию. */
    private fun touchShield() = View(activity).apply {
        isClickable = true
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun staticPreview(info: AppWidgetProviderInfo) = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        val density = activity.resources.displayMetrics.densityDpi
        setImageDrawable(info.loadPreviewImage(activity, density) ?: info.loadIcon(activity, density))
    }

    private fun resize(dx: Int, dy: Int) {
        val info = chosen ?: return
        val limits = panel.limitsOf(info)
        if (limits.canResizeX) spanX = (spanX + dx).coerceIn(limits.minSpanX, WidgetGridLayout.COLUMNS)
        if (limits.canResizeY) spanY = (spanY + dy).coerceIn(limits.minSpanY, panel.maxRows)
        applySize()
    }

    private fun applySize() {
        sizeLabel.text = activity.getString(R.string.widget_size, spanX, spanY)
        val cell = panel.cellSizePx()
        previewFrame.layoutParams = previewFrame.layoutParams.apply {
            width = spanX * cell
            height = spanY * cell
        }
        live?.let { panel.reportSize(it.id, it.view, spanX, spanY) }
    }

    private fun addChosen() {
        val info = chosen ?: return
        val preview = live
        live = null
        previewFrame.removeAllViews()
        if (preview != null) panel.addPreview(preview, spanX, spanY) else panel.add(info, spanX, spanY)
        dialog.dismiss()
    }

    private fun discardLive() {
        live?.let(panel::discardPreview)
        live = null
        previewFrame.removeAllViews()
    }

    private fun loadThumbnail(info: AppWidgetProviderInfo, target: ImageView, key: String) {
        target.tag = key
        val cached = thumbnails.get(key)
        target.setImageBitmap(cached)
        if (cached != null) return
        executor.execute {
            val bitmap = renderThumbnail(info)
            if (bitmap != null) thumbnails.put(key, bitmap)
            mainHandler.post { if (target.tag == key) target.setImageBitmap(bitmap) }
        }
    }

    /** Картинки виджетов бывают большими: держим в памяти только уменьшенные копии. */
    private fun renderThumbnail(info: AppWidgetProviderInfo): Bitmap? = runCatching {
        val dpi = activity.resources.displayMetrics.densityDpi
        val drawable: Drawable = info.loadPreviewImage(activity, 0) ?: info.loadIcon(activity, dpi)
        val maxSize = (THUMBNAIL_DP * density).toInt()
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = maxSize / maxOf(width, height).toFloat()
        drawable.toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
    }.getOrNull()

    private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows[position].info == null) TYPE_HEADER else TYPE_CARD

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val layout = if (viewType == TYPE_HEADER) R.layout.item_widget_header else R.layout.item_widget_card
            return RowHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = rows[position]
            val info = row.info
            if (info == null) {
                (holder.itemView as TextView).text = row.appLabel
                return
            }
            holder.bindCard(row, info)
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindCard(row: Row, info: AppWidgetProviderInfo) {
            itemView.findViewById<TextView>(R.id.widget_label).text = row.label
            val (spanX, spanY) = panel.defaultSpan(info)
            itemView.findViewById<TextView>(R.id.widget_span).text =
                activity.getString(R.string.widget_size, spanX, spanY)
            loadThumbnail(info, itemView.findViewById(R.id.widget_thumbnail), info.provider.flattenToString())
            itemView.setOnClickListener { showDetail(row) }
        }
    }

    companion object {
        private const val COLUMNS = 2
        private const val TYPE_HEADER = 0
        private const val TYPE_CARD = 1
        private const val THUMBNAIL_DP = 160
        private const val THUMBNAIL_CACHE_BYTES = 4 * 1024 * 1024

        /** Группирует виджеты по приложениям. Названия читаются с диска, только в фоновом потоке. */
        fun loadRows(context: Context, providers: List<AppWidgetProviderInfo>): List<Row> {
            val pm = context.packageManager
            val collator = Collator.getInstance()
            val byApp = providers.groupBy { provider ->
                runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString()
                }.getOrDefault(provider.provider.packageName)
            }
            return byApp.entries.sortedWith(compareBy(collator) { it.key }).flatMap { (app, widgets) ->
                listOf(Row(app, app, null)) + widgets.map { Row(app, it.loadLabel(pm), it) }
            }
        }
    }
}
