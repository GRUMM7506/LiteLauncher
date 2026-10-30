package com.grumm.litelauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Режим правки виджета: рамка с ручками поверх сетки. Рамка идёт за пальцем плавно,
 * а сам виджет занимает ячейки, ближайшие к её краям.
 */
@SuppressLint("ViewConstructor")
class WidgetEditor(
    context: Context,
    private val grid: WidgetGridLayout,
    private val widget: View,
    private val limits: Limits,
    private val listener: Listener,
) : View(context) {

    class Limits(val canResizeX: Boolean, val canResizeY: Boolean, val minSpanX: Int, val minSpanY: Int)

    interface Listener {
        /** Виджет занял новые ячейки. */
        fun onCellChanged(view: View)
        fun onRemove(view: View)
        fun onDone()
    }

    private enum class Mode { NONE, MOVE, LEFT, TOP, RIGHT, BOTTOM }

    private val density = resources.displayMetrics.density
    private val handleRadius = HANDLE_DP * density
    private val touchRadius = TOUCH_DP * density
    private val corner = CORNER_DP * density
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ACCENT
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GHOST }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = REMOVE }
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val frame = RectF()
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var settle: ValueAnimator? = null

    private val params get() = widget.layoutParams as WidgetGridLayout.CellParams

    private var target = params.cell

    init {
        frame.set(rectOf(params.cell))
    }

    /** Долгое нажатие сразу переходит в перетаскивание, палец ещё на экране. */
    fun startMove(rawX: Float, rawY: Float) {
        mode = Mode.MOVE
        lastX = rawX
        lastY = rawY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = handleTouch(event)

    fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onUp()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == Mode.MOVE) canvas.drawRoundRect(rectOf(target), corner, corner, ghostPaint)
        canvas.drawRoundRect(frame, corner, corner, framePaint)
        if (limits.canResizeX) {
            drawHandle(canvas, frame.left, frame.centerY())
            drawHandle(canvas, frame.right, frame.centerY())
        }
        if (limits.canResizeY) {
            drawHandle(canvas, frame.centerX(), frame.top)
            drawHandle(canvas, frame.centerX(), frame.bottom)
        }
        drawRemoveButton(canvas)
    }

    private fun onDown(event: MotionEvent) {
        settle?.cancel()
        lastX = event.rawX
        lastY = event.rawY
        val x = event.x
        val y = event.y
        if (hypot(x - removeX(), y - removeY()) < touchRadius) {
            listener.onRemove(widget)
            return
        }
        mode = hitTest(x, y)
        if (mode == Mode.NONE) listener.onDone()
    }

    private fun hitTest(x: Float, y: Float): Mode = when {
        limits.canResizeX && isNear(x, y, frame.left, frame.centerY()) -> Mode.LEFT
        limits.canResizeX && isNear(x, y, frame.right, frame.centerY()) -> Mode.RIGHT
        limits.canResizeY && isNear(x, y, frame.centerX(), frame.top) -> Mode.TOP
        limits.canResizeY && isNear(x, y, frame.centerX(), frame.bottom) -> Mode.BOTTOM
        frame.contains(x, y) -> Mode.MOVE
        else -> Mode.NONE
    }

    private fun isNear(x: Float, y: Float, pointX: Float, pointY: Float) = hypot(x - pointX, y - pointY) < touchRadius

    private fun onMove(event: MotionEvent) {
        val dx = event.rawX - lastX
        val dy = event.rawY - lastY
        lastX = event.rawX
        lastY = event.rawY
        if (mode == Mode.MOVE) dragBy(dx, dy) else if (mode != Mode.NONE) resizeBy(dx, dy)
        invalidate()
    }

    private fun dragBy(dx: Float, dy: Float) {
        val moveX = dx.coerceIn(-frame.left, width - frame.right)
        val moveY = dy.coerceIn(-frame.top, height - frame.bottom)
        frame.offset(moveX, moveY)
        widget.translationX = frame.left - rectOf(params.cell).left
        widget.translationY = frame.top - rectOf(params.cell).top
        val size = grid.cellSize.toFloat()
        val cell = target.copy(x = (frame.left / size).roundToInt(), y = (frame.top / size).roundToInt())
        if (grid.isFree(cell, widget)) target = cell
    }

    private fun resizeBy(dx: Float, dy: Float) {
        val size = grid.cellSize.toFloat()
        val minWidth = limits.minSpanX * size
        val minHeight = limits.minSpanY * size
        when (mode) {
            Mode.LEFT -> frame.left = (frame.left + dx).coerceIn(0f, frame.right - minWidth)
            Mode.RIGHT -> frame.right = (frame.right + dx).coerceIn(frame.left + minWidth, width.toFloat())
            Mode.TOP -> frame.top = (frame.top + dy).coerceIn(0f, frame.bottom - minHeight)
            Mode.BOTTOM -> frame.bottom = (frame.bottom + dy).coerceIn(frame.top + minHeight, height.toFloat())
            else -> Unit
        }
        applyNearestCell(size)
    }

    private fun applyNearestCell(size: Float) {
        val left = (frame.left / size).roundToInt()
        val top = (frame.top / size).roundToInt()
        val cell = WidgetCell(
            left,
            top,
            (frame.right / size).roundToInt() - left,
            (frame.bottom / size).roundToInt() - top,
        )
        if (cell == params.cell || !grid.isFree(cell, widget)) return
        target = cell
        commit(cell)
    }

    private fun commit(cell: WidgetCell) {
        params.cell = cell
        widget.requestLayout()
        listener.onCellChanged(widget)
    }

    private fun onUp() {
        val moved = mode == Mode.MOVE
        mode = Mode.NONE
        if (moved) dropWidget()
        settleFrame()
    }

    /** Виджет уже в новых ячейках, а сдвиг сохраняет его под пальцем и плавно сходит на нет. */
    private fun dropWidget() {
        if (target != params.cell) commit(target)
        val rest = rectOf(target)
        widget.translationX = frame.left - rest.left
        widget.translationY = frame.top - rest.top
        widget.animate().translationX(0f).translationY(0f)
            .setInterpolator(easing).setDuration(SETTLE_MS).start()
    }

    private fun settleFrame() {
        val from = RectF(frame)
        val to = rectOf(params.cell)
        settle = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SETTLE_MS
            interpolator = easing
            addUpdateListener {
                val t = it.animatedValue as Float
                frame.set(
                    from.left + (to.left - from.left) * t,
                    from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t,
                    from.bottom + (to.bottom - from.bottom) * t,
                )
                invalidate()
            }
            start()
        }
    }

    private fun rectOf(cell: WidgetCell): RectF {
        val size = grid.cellSize.toFloat()
        return RectF(cell.x * size, cell.y * size, (cell.x + cell.spanX) * size, (cell.y + cell.spanY) * size)
    }

    /** Ручки у края сетки сдвигаем внутрь, иначе они обрежутся. */
    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val cx = x.coerceIn(handleRadius, width - handleRadius)
        val cy = y.coerceIn(handleRadius, height - handleRadius)
        canvas.drawCircle(cx, cy, handleRadius, fillPaint)
    }

    private fun removeX() = (frame.right - touchRadius / 2).coerceIn(handleRadius, width - handleRadius)

    private fun removeY() = (frame.top + touchRadius / 2).coerceIn(handleRadius, height - handleRadius)

    private fun drawRemoveButton(canvas: Canvas) {
        val x = removeX()
        val y = removeY()
        canvas.drawCircle(x, y, handleRadius * 1.5f, removePaint)
        val arm = handleRadius * 0.6f
        canvas.drawLine(x - arm, y - arm, x + arm, y + arm, crossPaint)
        canvas.drawLine(x - arm, y + arm, x + arm, y - arm, crossPaint)
    }

    private companion object {
        const val HANDLE_DP = 7
        const val TOUCH_DP = 28
        const val CORNER_DP = 16
        const val SETTLE_MS = 200L
        const val ACCENT = 0xFF8CF7DC.toInt()
        const val GHOST = 0x338CF7DC
        const val REMOVE = 0xFFD9534F.toInt()
    }
}
