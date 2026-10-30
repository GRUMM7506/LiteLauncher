package com.grumm.litelauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** Всплывающее меню: раскрывается от места нажатия и умеет показывать иконки ярлыков. */
object ActionPopup {
    /** Пункт меню. [startsGroup] отделяет его от предыдущего линией. */
    class Row(
        val label: CharSequence,
        val icon: Drawable? = null,
        val startsGroup: Boolean = false,
        val action: () -> Unit,
    )

    private const val WIDTH_DP = 240
    private const val MARGIN_DP = 12
    private const val ICON_DP = 24
    private const val DIVIDER_ALPHA = 0x26FFFFFF
    private const val START_SCALE = 0.9f
    private const val OPEN_MS = 180L
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun show(anchor: View, rows: List<Row>) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val width = (WIDTH_DP * density).toInt()
        val popup = PopupWindow(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isFocusable = true
            isOutsideTouchable = true
            elevation = 8 * density
            animationStyle = 0
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val content = buildContent(context, rows, popup, density)
        popup.contentView = content
        place(popup, content, anchor, width, density)
    }

    private fun buildContent(context: Context, rows: List<Row>, popup: PopupWindow, density: Float): View {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_popup)
            clipToOutline = true
        }
        rows.forEachIndexed { index, row ->
            if (row.startsGroup && index > 0) list.addView(divider(context, density))
            list.addView(rowView(list, row, popup, density))
        }
        return list
    }

    private fun divider(context: Context, density: Float) = View(context).apply {
        setBackgroundColor(DIVIDER_ALPHA)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt().coerceAtLeast(1))
    }

    private fun rowView(parent: ViewGroup, row: Row, popup: PopupWindow, density: Float): View {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_popup_row, parent, false) as TextView
        view.text = row.label
        row.icon?.let { icon ->
            val size = (ICON_DP * density).toInt()
            icon.setBounds(0, 0, size, size)
            view.setCompoundDrawablesRelative(icon, null, null, null)
        }
        view.setOnClickListener {
            popup.dismiss()
            row.action()
        }
        return view
    }

    /** Над нажатием, если помещается, иначе под ним. */
    private fun place(popup: PopupWindow, content: View, anchor: View, width: Int, density: Float) {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), unspecified)
        val root = anchor.rootView
        val margin = (MARGIN_DP * density).toInt()
        val location = IntArray(2).also(anchor::getLocationOnScreen)
        val height = content.measuredHeight
        val above = location[1] - height - margin >= margin
        val y = if (above) location[1] - height - margin else location[1] + anchor.height + margin
        val x = (location[0] + anchor.width / 2 - width / 2).coerceIn(margin, (root.width - width - margin).coerceAtLeast(margin))
        popup.showAtLocation(root, 0, x, y.coerceAtMost((root.height - height - margin).coerceAtLeast(margin)))
        animateIn(content, (location[0] + anchor.width / 2 - x).toFloat(), if (above) height.toFloat() else 0f)
    }

    private fun animateIn(content: View, pivotX: Float, pivotY: Float) {
        content.pivotX = pivotX
        content.pivotY = pivotY
        content.alpha = 0f
        content.scaleX = START_SCALE
        content.scaleY = START_SCALE
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(easing).setDuration(OPEN_MS).start()
    }
}
