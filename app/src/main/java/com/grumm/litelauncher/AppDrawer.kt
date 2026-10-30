package com.grumm.litelauncher

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Полноэкранная постраничная сетка приложений с поиском. */
class AppDrawer(
    val view: View,
    icons: IconLoader,
    private val onLaunch: (AppEntry, View) -> Unit,
    onMenu: (AppEntry, View) -> Unit,
    private val onBack: () -> Unit,
) {
    private val search = view.findViewById<SearchField>(R.id.search)
    private val list = view.findViewById<RecyclerView>(R.id.list)
    private val dots = view.findViewById<PageDots>(R.id.dots)
    private val bottomBar = view.findViewById<View>(R.id.bottom_bar)
    private val adapter = AppAdapter(icons, onLaunch, onMenu)
    private val imm = view.context.getSystemService<InputMethodManager>()!!
    private val density = view.resources.displayMetrics.density
    private var settings = LauncherSettings()
    private var apps = emptyList<AppEntry>()
    private var visibleRows = settings.rows
    private var currentPage = 0

    init {
        setUpList(view.context)
        search.onBack = onBack
        search.addTextChangedListener(filterOnChange())
        search.setOnEditorActionListener { _, actionId, event -> launchFirstMatch(actionId, event) }
        followKeyboard()
    }

    fun applySettings(updated: LauncherSettings) {
        settings = updated
        visibleRows = rowsThatFit()
        applyFilter()
    }

    fun setApps(updated: List<AppEntry>) {
        apps = updated
        applyFilter()
    }

    fun onOpened() {
        if (!settings.autoKeyboard) return
        search.requestFocus()
        search.post { imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun onClosing() {
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
        search.text.clear()
    }

    /** Возвращает сетку на первую страницу. */
    fun onClosed() {
        list.scrollToPosition(0)
        currentPage = 0
        dots.setPosition(0f)
    }

    /** Отдаёт страницы и их битмапы при нехватке памяти. Следующий показ создаст их заново. */
    fun releaseViews() {
        list.adapter = null
        list.recycledViewPool.clear()
    }

    fun restoreViews() {
        if (list.adapter == null) list.adapter = adapter
    }

    private fun setUpList(context: Context) {
        list.setHasFixedSize(true)
        list.itemAnimator = null
        list.setItemViewCacheSize(1)
        list.layoutManager = PagerLayoutManager(context)
        list.adapter = adapter
        SmoothPagerSnapHelper().attachToRecyclerView(list)
        list.addOnScrollListener(pageScrollListener())
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onListResized() }
    }

    private fun onListResized() {
        val fit = rowsThatFit()
        if (fit == visibleRows) return
        visibleRows = fit
        list.post {
            applyFilter()
            list.alpha = 0f
            list.animate().alpha(1f).setDuration(RESIZE_FADE_MS).start()
        }
    }

    /** С открытой клавиатурой рядов меньше, чтобы иконки не сжимались. */
    private fun rowsThatFit(): Int {
        val minCell = (settings.iconSizeDp + LABEL_SPACE_DP) * density
        val fit = if (list.height == 0) settings.rows else (list.height / minCell).toInt()
        return fit.coerceIn(1, settings.rows)
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        val shown = if (query.isEmpty()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
        adapter.submit(shown, settings.columns, visibleRows, settings.showLabels)
        dots.setCount(adapter.itemCount)
    }

    private fun launchFirstMatch(actionId: Int, event: KeyEvent?): Boolean {
        val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        if (actionId != EditorInfo.IME_ACTION_GO && !isEnter) return false
        adapter.first?.let { onLaunch(it, search) }
        return true
    }

    private fun filterOnChange() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            applyFilter()
            onClosed()
        }
    }

    private fun pageScrollListener() = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                imm.hideSoftInputFromWindow(search.windowToken, 0)
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = onPagesMoved()
    }

    private fun onPagesMoved() {
        val width = list.width
        if (width == 0) return
        val position = list.computeHorizontalScrollOffset().toFloat() / width
        dots.setPosition(position)
        for (index in 0 until list.childCount) styleByOffset(list.getChildAt(index), width)
        tickOnPageChange(position.roundToInt())
    }

    /** Уходящая страница слегка уменьшается и гаснет. */
    private fun styleByOffset(page: View, width: Int) {
        val offset = abs(page.left.toFloat() / width).coerceAtMost(1f)
        page.alpha = 1f - offset * PAGE_FADE
        val scale = 1f - offset * PAGE_SHRINK
        page.scaleX = scale
        page.scaleY = scale
    }

    private fun tickOnPageChange(page: Int) {
        if (page == currentPage) return
        currentPage = page
        if (settings.haptics) list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Строка поиска едет вместе с клавиатурой, а не прыгает по окончании её анимации. */
    private fun followKeyboard() {
        bottomBar.setWindowInsetsAnimationCallback(
            object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
                private var startTop = 0
                private var shift = 0f

                override fun onPrepare(animation: WindowInsetsAnimation) {
                    startTop = screenTop()
                }

                override fun onStart(
                    animation: WindowInsetsAnimation,
                    bounds: WindowInsetsAnimation.Bounds,
                ): WindowInsetsAnimation.Bounds {
                    shift = (startTop - screenTop()).toFloat()
                    bottomBar.translationY = shift
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsets,
                    running: MutableList<WindowInsetsAnimation>,
                ): WindowInsets {
                    val ime = running.firstOrNull { it.typeMask and WindowInsets.Type.ime() != 0 }
                    if (ime != null) bottomBar.translationY = shift * (1f - ime.interpolatedFraction)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimation) {
                    bottomBar.translationY = 0f
                }
            },
        )
    }

    private fun screenTop(): Int {
        val location = IntArray(2)
        bottomBar.getLocationOnScreen(location)
        return location[1] - bottomBar.translationY.toInt()
    }

    /** Держит соседние страницы готовыми, чтобы листание не тратило кадр на их разметку. */
    private class PagerLayoutManager(context: Context) :
        LinearLayoutManager(context, HORIZONTAL, false) {

        override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
            extraLayoutSpace[0] = width
            extraLayoutSpace[1] = width
        }
    }

    private companion object {
        const val LABEL_SPACE_DP = 30
        const val PAGE_FADE = 0.4f
        const val PAGE_SHRINK = 0.05f
        const val RESIZE_FADE_MS = 160L
    }
}
