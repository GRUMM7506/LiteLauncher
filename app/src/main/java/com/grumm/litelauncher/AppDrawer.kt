package com.grumm.litelauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Полноэкранный список приложений с поиском. Подложка рисуется на корневом контейнере. */
class AppDrawer(
    private val view: View,
    private val root: ViewGroup,
    private val home: View,
    icons: IconLoader,
    private val onLaunch: (AppEntry, View) -> Unit,
    onMenu: (AppEntry, View) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val search = view.findViewById<SearchField>(R.id.search)
    private val list = view.findViewById<RecyclerView>(R.id.list)
    private val adapter = AppAdapter(icons, onLaunch, onMenu)
    private val imm = view.context.getSystemService<InputMethodManager>()!!
    private val scrim = ColorDrawable(SCRIM_COLOR).apply { alpha = 0 }
    private var apps = emptyList<AppEntry>()
    private var fade: ValueAnimator? = null

    val isOpen get() = view.isVisible

    init {
        root.background = scrim
        list.setHasFixedSize(true)
        list.itemAnimator = null
        list.layoutManager = LinearLayoutManager(view.context)
        list.adapter = adapter
        list.addOnScrollListener(hideKeyboardOnDrag())
        search.onBack = ::close
        search.addTextChangedListener(filterOnChange())
        search.setOnEditorActionListener { _, actionId, event -> launchFirstMatch(actionId, event) }
    }

    fun setApps(updated: List<AppEntry>) {
        apps = updated
        applyFilter()
    }

    fun open() {
        if (isOpen) return
        if (list.adapter == null) list.adapter = adapter
        view.isVisible = true
        search.requestFocus()
        search.post { imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
        animate(from = 0f, to = 1f) { home.visibility = View.INVISIBLE }
    }

    fun close() = closeInternal(animated = true)

    fun closeNow() = closeInternal(animated = false)

    /** Отдаёт строки и их битмапы, пока лаунчер скрыт. Следующее открытие создаст их заново. */
    fun releaseViews() {
        if (isOpen) return
        list.adapter = null
        list.recycledViewPool.clear()
    }

    private fun closeInternal(animated: Boolean) {
        if (!isOpen) return
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.text.clear()
        home.visibility = View.VISIBLE
        fade?.cancel()
        if (animated) animate(from = 1f, to = 0f, ::finishClose) else finishClose()
    }

    private fun finishClose() {
        scrim.alpha = 0
        view.alpha = 1f
        view.isVisible = false
        list.scrollToPosition(0)
        onClosed()
    }

    private fun animate(from: Float, to: Float, onEnd: () -> Unit) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(from, to).apply {
            duration = FADE_MS
            addUpdateListener { setFade(it.animatedValue as Float) }
            addListener(onEndListener(onEnd))
            start()
        }
    }

    private fun setFade(value: Float) {
        view.alpha = value
        scrim.alpha = (value * 255).toInt()
    }

    private fun onEndListener(onEnd: () -> Unit) = object : AnimatorListenerAdapter() {
        private var cancelled = false

        override fun onAnimationCancel(animation: Animator) {
            cancelled = true
        }

        override fun onAnimationEnd(animation: Animator) {
            if (!cancelled) onEnd()
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        adapter.submit(if (query.isEmpty()) apps else apps.filter { it.label.contains(query, ignoreCase = true) })
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
        override fun afterTextChanged(s: Editable?) = applyFilter()
    }

    private fun hideKeyboardOnDrag() = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                imm.hideSoftInputFromWindow(search.windowToken, 0)
            }
        }
    }

    private companion object {
        const val SCRIM_COLOR = 0xB3000000.toInt()
        const val FADE_MS = 90L
    }
}
