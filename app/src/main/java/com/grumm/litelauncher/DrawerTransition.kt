package com.grumm.litelauncher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.view.isInvisible
import kotlin.math.abs
import kotlin.math.min

/** Переход между главным экраном и сеткой. Прогресс ведёт либо палец, либо пружина. */
class DrawerTransition(
    private val root: View,
    private val home: View,
    private val drawer: View,
    private val onSettled: (open: Boolean) -> Unit,
) {
    private val density = root.resources.displayMetrics.density
    private val dragDistance = DRAG_DISTANCE_DP * density
    private val homeShift = HOME_SHIFT_DP * density
    private val drawerShift = DRAWER_SHIFT_DP * density
    private val scrim = ColorDrawable(Color.BLACK).apply { alpha = 0 }
    private val spring = Spring(::apply, ::onRest)
    private var scrimMax = 0.7f

    /** Куда движется переход: true — сетка открыта или открывается. */
    var isOpen = false
        private set

    val isAnimating get() = spring.isRunning

    init {
        root.background = scrim
    }

    fun applySettings(settings: LauncherSettings) {
        scrimMax = settings.scrimPercent / 100f
        spring.omega = settings.springOmega
        scrim.alpha = (spring.value.coerceIn(0f, 1f) * scrimMax * 255).toInt()
    }

    /** Палец подхватил переход: движение останавливается там, где оказалось. */
    fun beginDrag() {
        home.animate().cancel()
        spring.stop()
    }

    fun dragBy(deltaY: Float) {
        spring.moveTo((spring.value - deltaY / dragDistance).coerceIn(0f, 1f))
    }

    fun release(velocityY: Float) {
        val velocity = -velocityY / dragDistance
        val open = if (abs(velocity) > FLING_VELOCITY) velocity > 0 else spring.value > 0.5f
        settle(open, velocity)
    }

    fun settle(open: Boolean, velocity: Float = spring.velocity) {
        home.animate().cancel()
        isOpen = open
        spring.animateTo(if (open) 1f else 0f, velocity)
    }

    /** Без анимации: экран всё равно не виден пользователю. */
    fun snapClosed() {
        isOpen = false
        spring.snapTo(0f)
    }

    /** Сильный бросок может на миг увести пружину за границы, поэтому значение ограничиваем. */
    private fun apply(value: Float) {
        val progress = value.coerceIn(0f, 1f)
        scrim.alpha = (progress * scrimMax * 255).toInt()
        styleHome(progress)
        styleDrawer(progress)
    }

    private fun styleHome(progress: Float) {
        home.isInvisible = progress >= 1f
        home.alpha = 1f - min(1f, progress * HOME_FADE_SPEED)
        home.translationY = -progress * homeShift
        val scale = 1f - progress * HOME_SHRINK
        home.scaleX = scale
        home.scaleY = scale
    }

    private fun styleDrawer(progress: Float) {
        drawer.isInvisible = progress <= 0f
        drawer.alpha = progress
        drawer.translationY = (1f - progress) * drawerShift
        val scale = 1f - (1f - progress) * DRAWER_SHRINK
        drawer.scaleX = scale
        drawer.scaleY = scale
    }

    private fun onRest() = onSettled(isOpen)

    private companion object {
        const val DRAG_DISTANCE_DP = 320
        const val HOME_SHIFT_DP = 48
        const val DRAWER_SHIFT_DP = 96
        const val HOME_FADE_SPEED = 1.6f
        const val HOME_SHRINK = 0.06f
        const val DRAWER_SHRINK = 0.06f
        const val FLING_VELOCITY = 1.2f
    }
}
