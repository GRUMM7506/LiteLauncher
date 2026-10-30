package com.grumm.litelauncher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Корневой контейнер: распознаёт свайп вверх поверх дочерних View и тап по пустому месту. */
class SwipeUpLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    var gesturesEnabled = true
    var onOpenRequest: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistance = resources.displayMetrics.density * SWIPE_DISTANCE_DP
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var fired = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(ev)
            MotionEvent.ACTION_MOVE -> return isUpwardDrag(ev)
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(ev)
            MotionEvent.ACTION_MOVE -> trackMove(ev)
            MotionEvent.ACTION_UP -> if (!moved && !fired) performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onOpenRequest?.invoke()
        return true
    }

    private fun startGesture(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        moved = false
        fired = false
    }

    private fun isUpwardDrag(ev: MotionEvent): Boolean {
        val dy = downY - ev.y
        return dy > touchSlop && dy > abs(ev.x - downX)
    }

    private fun trackMove(ev: MotionEvent) {
        if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) moved = true
        if (!fired && downY - ev.y > swipeDistance) {
            fired = true
            onOpenRequest?.invoke()
        }
    }

    private companion object {
        const val SWIPE_DISTANCE_DP = 48
    }
}
