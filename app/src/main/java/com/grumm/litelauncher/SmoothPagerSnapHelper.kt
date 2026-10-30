package com.grumm.litelauncher

import android.util.DisplayMetrics
import android.view.View
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Докрутка до страницы с мягким торможением: чем меньше осталось, тем короче движение. */
class SmoothPagerSnapHelper : PagerSnapHelper() {

    private var recycler: RecyclerView? = null
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        recycler = recyclerView
        super.attachToRecyclerView(recyclerView)
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
        val context = recycler?.context ?: return null
        return object : LinearSmoothScroller(context) {
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                val distance = calculateDistanceToFinalSnap(layoutManager, targetView) ?: return
                action.update(distance[0], distance[1], durationFor(abs(distance[0])), easing)
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics) =
                SPEED_MS_PER_INCH / displayMetrics.densityDpi
        }
    }

    private fun durationFor(distance: Int): Int {
        val width = recycler?.width?.coerceAtLeast(1) ?: 1
        return (MIN_MS + (MAX_MS - MIN_MS) * distance / width).coerceAtLeast(1)
    }

    private companion object {
        const val MIN_MS = 180
        const val MAX_MS = 340
        const val SPEED_MS_PER_INCH = 60f
    }
}
