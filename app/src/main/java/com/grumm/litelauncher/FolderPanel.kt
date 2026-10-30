package com.grumm.litelauncher

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil

/** Открытая папка: панель поверх сетки. Большие папки листаются так же, как сетка. */
class FolderPanel(
    private val view: View,
    icons: IconLoader,
    private val onLaunch: (AppEntry, View) -> Unit,
    private val onMenu: (AppEntry, View) -> Unit,
) {
    private val panel = view.findViewById<View>(R.id.folder_panel)
    private val title = view.findViewById<TextView>(R.id.folder_title)
    private val list = view.findViewById<RecyclerView>(R.id.folder_list)
    private val dots = view.findViewById<PageDots>(R.id.folder_dots)
    private val density = view.resources.displayMetrics.density
    private val spring = Spring(::style, ::onRest)
    private val adapter = AppAdapter(icons, ::onClick, ::onLongClick)
    private var opening = false

    val isOpen get() = opening

    init {
        view.setOnClickListener { close() }
        panel.isClickable = true
        list.setHasFixedSize(true)
        list.itemAnimator = null
        list.layoutManager = PagerLayoutManager(view.context)
        list.adapter = adapter
        SmoothPagerSnapHelper().attachToRecyclerView(list)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (list.width > 0) dots.setPosition(list.computeHorizontalScrollOffset().toFloat() / list.width)
            }
        })
    }

    fun open(folder: GridItem.Folder, settings: LauncherSettings) {
        val columns = settings.columns.coerceAtMost(MAX_COLUMNS)
        val rows = ceil(folder.apps.size / columns.toFloat()).toInt().coerceIn(1, MAX_ROWS)
        title.text = folder.label
        list.layoutParams = list.layoutParams.apply {
            height = (rows * (settings.iconSizeDp + CELL_SPACE_DP) * density).toInt()
        }
        adapter.submit(folder.apps.map { GridItem.App(it) }, columns, rows, settings.showLabels)
        dots.setCount(adapter.itemCount)
        dots.setPosition(0f)
        list.scrollToPosition(0)
        spring.omega = settings.springOmega
        opening = true
        view.isVisible = true
        spring.animateTo(1f)
    }

    fun close() {
        if (!opening) return
        opening = false
        spring.animateTo(0f)
    }

    fun closeNow() {
        opening = false
        spring.snapTo(0f)
    }

    private fun onClick(item: GridItem, source: View) {
        if (item is GridItem.App) onLaunch(item.entry, source)
    }

    private fun onLongClick(item: GridItem, source: View) {
        if (item is GridItem.App) onMenu(item.entry, source)
    }

    private fun style(value: Float) {
        val progress = value.coerceIn(0f, 1f)
        view.alpha = progress
        val scale = START_SCALE + (1f - START_SCALE) * progress
        panel.scaleX = scale
        panel.scaleY = scale
    }

    private fun onRest() {
        if (!opening) view.isVisible = false
    }

    private companion object {
        const val MAX_COLUMNS = 4
        const val MAX_ROWS = 5
        const val CELL_SPACE_DP = 42
        const val START_SCALE = 0.88f
    }
}
