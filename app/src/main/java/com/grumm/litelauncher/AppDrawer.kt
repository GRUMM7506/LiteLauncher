package com.grumm.litelauncher

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

/**
 * Полноэкранный список приложений с поиском. Подложка рисуется на корневом контейнере.
 * Открывается без анимации: при программном рендеринге полноэкранная альфа даёт кадры по 33 мс.
 */
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
    private var apps = emptyList<AppEntry>()

    val isOpen get() = view.isVisible

    init {
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
        root.setBackgroundColor(SCRIM_COLOR)
        home.visibility = View.INVISIBLE
        view.isVisible = true
        search.requestFocus()
        search.post { imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun close() {
        if (!isOpen) return
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.text.clear()
        root.background = null
        home.visibility = View.VISIBLE
        view.isVisible = false
        list.scrollToPosition(0)
        onClosed()
    }

    /** Отдаёт строки и их битмапы, пока лаунчер скрыт. Следующее открытие создаст их заново. */
    fun releaseViews() {
        if (isOpen) return
        list.adapter = null
        list.recycledViewPool.clear()
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
    }
}
