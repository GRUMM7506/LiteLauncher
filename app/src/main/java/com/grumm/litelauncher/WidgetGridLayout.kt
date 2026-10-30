package com.grumm.litelauncher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Положение виджета в ячейках. */
data class WidgetCell(val x: Int, val y: Int, val spanX: Int, val spanY: Int)

/** Сетка виджетов главного экрана. Ячейки квадратные, рядов столько, сколько помещается. */
class WidgetGridLayout(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    class CellParams(var cell: WidgetCell) : LayoutParams(MATCH_PARENT, MATCH_PARENT)

    val cellSize get() = width / COLUMNS

    val rows get() = if (cellSize == 0) 1 else (height / cellSize).coerceAtLeast(1)

    fun isFree(cell: WidgetCell, ignore: View? = null): Boolean {
        if (cell.x < 0 || cell.y < 0 || cell.x + cell.spanX > COLUMNS || cell.y + cell.spanY > rows) return false
        return widgetCells(ignore).none { overlaps(it, cell) }
    }

    /** Первое свободное место сверху вниз, слева направо. */
    fun findFree(spanX: Int, spanY: Int): WidgetCell? {
        for (y in 0..rows - spanY) {
            for (x in 0..COLUMNS - spanX) {
                val cell = WidgetCell(x, y, spanX, spanY)
                if (isFree(cell)) return cell
            }
        }
        return null
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val height = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(width, height)
        val size = width / COLUMNS
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? CellParams
            val childWidth = params?.let { it.cell.spanX * size } ?: width
            val childHeight = params?.let { it.cell.spanY * size } ?: height
            child.measure(exactly(childWidth), exactly(childHeight))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val size = (right - left) / COLUMNS
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? CellParams
            val x = (params?.cell?.x ?: 0) * size
            val y = (params?.cell?.y ?: 0) * size
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun widgetCells(ignore: View?) = (0 until childCount)
        .map(::getChildAt)
        .filter { it !== ignore }
        .mapNotNull { (it.layoutParams as? CellParams)?.cell }

    private fun overlaps(a: WidgetCell, b: WidgetCell) =
        a.x < b.x + b.spanX && b.x < a.x + a.spanX && a.y < b.y + b.spanY && b.y < a.y + a.spanY

    companion object {
        const val COLUMNS = 5
    }
}
