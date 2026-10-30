package com.grumm.litelauncher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Корневой контейнер: вертикальные жесты поверх дочерних View, тап и долгое нажатие по пустому месту.
 * Горизонтальные движения остаются сетке приложений.
 */
class GestureRootLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    interface Listener {
        val isDrawerOpen: Boolean
        val swipeDownEnabled: Boolean

        /** Если переход ещё идёт — останавливает его и возвращает true: новый жест подхватит его. */
        fun catchMotion(): Boolean
        fun isDragBlocked(x: Float, y: Float): Boolean
        fun onDragStart()
        fun onDragBy(deltaY: Float)
        fun onDragEnd(velocityY: Float)
        fun onSwipeDown()
        fun onTap()
        fun onLongPress(x: Float, y: Float)
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDownDistance = resources.displayMetrics.density * SWIPE_DOWN_DP
    private val longPressRunnable = Runnable { onLongPressTimeout() }
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var moved = false
    private var consumed = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val listener = listener ?: return false
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startGesture(ev)
                if (listener.catchMotion()) {
                    dragging = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (isVerticalDrag(ev) && wantsDrag(ev, listener)) {
                beginDrag(listener, ev)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopTrackingVelocity()
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val listener = listener ?: return false
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Если движение подхвачено при перехвате, это же DOWN приходит сюда повторно.
                val caught = dragging
                startGesture(ev)
                dragging = caught
                if (!caught && !listener.isDrawerOpen) postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> onMove(ev, listener)
            MotionEvent.ACTION_UP -> onUp(listener)
            MotionEvent.ACTION_CANCEL -> finishDrag(listener, 0f)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        listener?.onTap()
        return true
    }

    private fun startGesture(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        lastY = ev.y
        dragging = false
        moved = false
        consumed = false
        removeCallbacks(longPressRunnable)
    }

    private fun isVerticalDrag(ev: MotionEvent): Boolean {
        val dy = abs(ev.y - downY)
        return dy > touchSlop && dy > abs(ev.x - downX) * VERTICAL_BIAS
    }

    private fun wantsDrag(ev: MotionEvent, listener: Listener): Boolean {
        val down = ev.y > downY
        if (listener.isDrawerOpen) return down
        if (listener.isDragBlocked(downX, downY)) return false
        return !down || listener.swipeDownEnabled
    }

    private fun beginDrag(listener: Listener, ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        moved = true
        lastY = ev.y
        val shadeSwipe = ev.y > downY && !listener.isDrawerOpen
        if (!shadeSwipe) {
            dragging = true
            listener.onDragStart()
        }
    }

    private fun onMove(ev: MotionEvent, listener: Listener) {
        if (!moved && (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)) {
            moved = true
            removeCallbacks(longPressRunnable)
            if (isVerticalDrag(ev) && wantsDrag(ev, listener)) beginDrag(listener, ev)
        }
        if (dragging) {
            listener.onDragBy(ev.y - lastY)
            lastY = ev.y
        } else if (!consumed && !listener.isDrawerOpen && ev.y - downY > swipeDownDistance) {
            consumed = true
            if (listener.swipeDownEnabled) listener.onSwipeDown()
        }
    }

    private fun onUp(listener: Listener) {
        removeCallbacks(longPressRunnable)
        if (dragging) {
            velocity?.computeCurrentVelocity(1000)
            finishDrag(listener, velocity?.yVelocity ?: 0f)
        } else if (!moved && !consumed) {
            performClick()
        }
        stopTrackingVelocity()
    }

    private fun finishDrag(listener: Listener, velocityY: Float) {
        removeCallbacks(longPressRunnable)
        if (dragging) listener.onDragEnd(velocityY)
        dragging = false
    }

    private fun onLongPressTimeout() {
        consumed = true
        moved = true
        listener?.onLongPress(downX, downY)
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) stopTrackingVelocity()
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        tracker.addMovement(ev)
    }

    private fun stopTrackingVelocity() {
        velocity?.recycle()
        velocity = null
    }

    private companion object {
        const val SWIPE_DOWN_DP = 48
        const val LONG_PRESS_MS = 400L
        const val VERTICAL_BIAS = 1.2f
    }
}
