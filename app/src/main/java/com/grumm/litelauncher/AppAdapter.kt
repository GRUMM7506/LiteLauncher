package com.grumm.litelauncher

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val icons: IconLoader,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var items = emptyList<AppEntry>()

    init {
        setHasStableIds(true)
    }

    val first get() = items.firstOrNull()

    // Фильтрация меняет список целиком, а стабильные ID сохраняют переиспользование строк.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(apps: List<AppEntry>) {
        items = apps
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false) as TextView
        val holder = Holder(row)
        row.setOnClickListener { holder.entry?.let { onClick(it, row) } }
        row.setOnLongClickListener { view ->
            holder.entry?.let { onLongClick(it, view) }
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.entry = entry
        holder.row.text = entry.label
        icons.bind(entry, holder.row, onTop = false)
    }

    class Holder(val row: TextView) : RecyclerView.ViewHolder(row) {
        var entry: AppEntry? = null
    }
}
