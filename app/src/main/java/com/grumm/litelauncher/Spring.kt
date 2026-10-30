package com.grumm.litelauncher

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import kotlin.math.abs
import kotlin.math.exp

/**
 * Критически демпфированная пружина. Подхватывает текущую скорость, поэтому движение
 * можно прервать или развернуть на лету без рывка.
 */
class Spring(
    private val onUpdate: (Float) -> Unit,
    private val onRest: () -> Unit,
) {
    /** Жёсткость: чем больше, тем быстрее движение. */
    var omega = 19f

    var value = 0f
        private set
    var velocity = 0f
        private set
    private var target = 0f

    private val ticker = TimeAnimator().apply {
        setTimeListener { _, _, deltaMs -> step(deltaMs / 1000f) }
    }

    val isRunning get() = ticker.isStarted

    fun animateTo(newTarget: Float, startVelocity: Float = velocity) {
        target = newTarget
        velocity = startVelocity
        if (!ValueAnimator.areAnimatorsEnabled()) {
            snapTo(newTarget)
        } else if (!ticker.isStarted) {
            ticker.start()
        }
    }

    /** Останавливает движение, оставляя текущее значение. */
    fun stop() {
        ticker.cancel()
        velocity = 0f
    }

    fun moveTo(newValue: Float) {
        value = newValue
        onUpdate(value)
    }

    fun snapTo(newValue: Float) {
        stop()
        target = newValue
        moveTo(newValue)
        onRest()
    }

    private fun step(dt: Float) {
        val offset = value - target
        val c = velocity + omega * offset
        val decay = exp(-omega * dt)
        val next = (offset + c * dt) * decay
        velocity = (c - omega * (offset + c * dt)) * decay
        value = target + next
        if (abs(next) < REST_DISTANCE && abs(velocity) < REST_VELOCITY) {
            snapTo(target)
        } else {
            onUpdate(value)
        }
    }

    private companion object {
        const val REST_DISTANCE = 0.001f
        const val REST_VELOCITY = 0.01f
    }
}
