package com.grumm.litelauncher

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Адаптер страниц сетки. Приложения раскладываются по страницам уже здесь. */
class AppAdapter(
    private val icons: IconLoader,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var pages = emptyList<List<AppEntry>>()
    private var columns = 1
    private var rows = 1
    private var showLabels = true

    init {
        setHasStableIds(true)
    }

    val first get() = pages.firstOrNull()?.firstOrNull()

    // Фильтрация меняет раскладку целиком, а стабильные ID сохраняют переиспользование страниц.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(apps: List<AppEntry>, columns: Int, rows: Int, showLabels: Boolean) {
        this.columns = columns
        this.rows = rows
        this.showLabels = showLabels
        pages = apps.chunked(columns * rows)
        notifyDataSetChanged()
    }

    override fun getItemCount() = pages.size

    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(AppPageView(parent.context, icons, onClick, onLongClick))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.page.bind(pages[position], columns, rows, showLabels)
    }

    class Holder(val page: AppPageView) : RecyclerView.ViewHolder(page)
}
