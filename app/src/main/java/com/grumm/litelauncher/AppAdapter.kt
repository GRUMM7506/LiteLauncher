package com.grumm.litelauncher

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Адаптер страниц сетки. Ячейки раскладываются по страницам уже здесь. */
class AppAdapter(
    private val icons: IconLoader,
    private val onClick: (GridItem, View) -> Unit,
    private val onLongClick: (GridItem, View) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var pages = emptyList<List<GridItem>>()
    private var columns = 1
    private var rows = 1
    private var showLabels = true

    init {
        setHasStableIds(true)
    }

    val first get() = pages.firstOrNull()?.firstOrNull()

    // Фильтрация меняет раскладку целиком, а стабильные ID сохраняют переиспользование страниц.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<GridItem>, columns: Int, rows: Int, showLabels: Boolean) {
        this.columns = columns
        this.rows = rows
        this.showLabels = showLabels
        pages = items.chunked(columns * rows)
        notifyDataSetChanged()
    }

    /** Обновляет значки, не перепривязывая страницы целиком. */
    fun refreshBadges() = notifyItemRangeChanged(0, itemCount, PAYLOAD_BADGES)

    override fun getItemCount() = pages.size

    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(AppPageView(parent.context, icons, onClick, onLongClick))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.page.bind(pages[position], columns, rows, showLabels)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position) else holder.page.refreshBadges()
    }

    class Holder(val page: AppPageView) : RecyclerView.ViewHolder(page)

    private companion object {
        const val PAYLOAD_BADGES = "badges"
    }
}
