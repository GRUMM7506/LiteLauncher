package com.grumm.litelauncher

import android.app.WallpaperColors
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Живые обои. Работают в отдельном процессе, чтобы не увеличивать память лаунчера,
 * и рисуют только пока видны: волна света медленная, поэтому хватает низкой частоты кадров.
 */
class LiteWallpaperService : WallpaperService() {

    private val engines = mutableSetOf<LiteEngine>()

    override fun onCreateEngine(): Engine = LiteEngine().also { engines += it }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        engines.forEach { it.syncTheme() }
    }

    private inner class LiteEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val frame = Runnable { drawFrame() }
        private var art: ThemeArt? = null
        private var running = false

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            rebuild(width, height)
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            running = visible
            handler.removeCallbacks(frame)
            if (visible) {
                syncTheme()
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            running = false
            handler.removeCallbacks(frame)
        }

        override fun onDestroy() {
            running = false
            handler.removeCallbacks(frame)
            engines -= this
            art?.base?.recycle()
            art = null
        }

        override fun onComputeColors(): WallpaperColors? = art?.base?.let(WallpaperColors::fromBitmap)

        fun syncTheme() {
            val current = art ?: return
            if (current.night == ThemeArt.isNight(this@LiteWallpaperService)) return
            rebuild(current.width, current.height)
            if (running) drawFrame()
        }

        private fun rebuild(width: Int, height: Int) {
            val current = art
            val night = ThemeArt.isNight(this@LiteWallpaperService)
            if (current != null && current.width == width && current.height == height && current.night == night) return
            val previous: Bitmap? = current?.base
            art = ThemeArt(width, height, night)
            previous?.recycle()
            notifyColorsChanged()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val current = art ?: return
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                current.drawFrame(canvas, SystemClock.uptimeMillis() / 1000f)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            if (running) handler.postDelayed(frame, FRAME_MS)
        }
    }

    private companion object {
        const val FRAME_MS = 66L
    }
}
