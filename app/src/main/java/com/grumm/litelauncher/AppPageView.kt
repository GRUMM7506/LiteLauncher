package com.grumm.litelauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Одна страница сетки: ячейки равного размера, заполняются построчно. */
class AppPageView(
    context: Context,
    private val icons: IconLoader,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
) : ViewGroup(context) {

    private var columns = 1
    private var rows = 1
    private var entries = emptyList<AppEntry>()

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        clipChildren = false
    }

    fun bind(page: List<AppEntry>, columns: Int, rows: Int, showLabels: Boolean) {
        if (columns != this.columns || rows != this.rows) requestLayout()
        this.columns = columns
        this.rows = rows
        entries = page
        // Переиспользованная страница могла остаться в состоянии «уходит за край».
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        ensureCellCount(columns * rows)
        for (index in 0 until childCount) {
            val cell = getChildAt(index) as TextView
            val entry = page.getOrNull(index)
            cell.visibility = if (entry == null) INVISIBLE else VISIBLE
            if (entry == null) cell.tag = null else bindCell(cell, entry, showLabels)
        }
    }

    // Страницы не перекрывают ячейки, поэтому при затухании не нужен промежуточный слой.
    override fun hasOverlappingRendering() = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val height = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(width, height)
        val cellWidth = MeasureSpec.makeMeasureSpec(width / columns, MeasureSpec.EXACTLY)
        val cellHeight = MeasureSpec.makeMeasureSpec(height / rows, MeasureSpec.EXACTLY)
        for (index in 0 until childCount) {
            val cell = getChildAt(index) as TextView
            centerContent(cell, height / rows)
            cell.measure(cellWidth, cellHeight)
        }
    }

    /** TextView прижимает верхний drawable к верху, поэтому блок «иконка + подпись» центрируем отступом. */
    private fun centerContent(cell: TextView, cellHeight: Int) {
        val iconHeight = cell.compoundDrawablesRelative[1]?.bounds?.height() ?: 0
        val content = iconHeight + cell.compoundDrawablePadding + cell.lineHeight
        val top = ((cellHeight - content) / 2).coerceAtLeast(0)
        if (cell.paddingTop != top) cell.setPadding(cell.paddingLeft, top, cell.paddingRight, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val cellWidth = (right - left) / columns
        val cellHeight = (bottom - top) / rows
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (index in 0 until childCount) {
            val column = index % columns
            val x = (if (rtl) columns - 1 - column else column) * cellWidth
            val y = index / columns * cellHeight
            getChildAt(index).layout(x, y, x + cellWidth, y + cellHeight)
        }
    }

    private fun ensureCellCount(count: Int) {
        while (childCount > count) removeViewAt(childCount - 1)
        while (childCount < count) addView(newCell(childCount))
    }

    private fun newCell(index: Int): View {
        val cell = LayoutInflater.from(context).inflate(R.layout.item_cell, this, false)
        cell.setOnClickListener { view -> entries.getOrNull(index)?.let { onClick(it, view) } }
        cell.setOnLongClickListener { view ->
            entries.getOrNull(index)?.let { onLongClick(it, view) }
            true
        }
        return cell
    }

    private fun bindCell(cell: TextView, entry: AppEntry, showLabels: Boolean) {
        val label = if (showLabels) entry.label else ""
        if (cell.text.toString() != label) cell.text = label
        icons.bind(entry, cell)
    }
}
