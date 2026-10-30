package com.grumm.litelauncher

import android.content.pm.LauncherActivityInfo

/** Приложение в списке. [key] уникален среди профилей и сохраняется в закреплённых. */
class AppEntry(val info: LauncherActivityInfo, val label: String, val key: String) {
    val category: AppCategory = AppCategory.of(info.applicationInfo)
}

/** Ячейка сетки: приложение или автоматическая папка. */
sealed class GridItem {
    abstract val label: String

    class App(val entry: AppEntry) : GridItem() {
        override val label get() = entry.label
    }

    class Folder(override val label: String, val apps: List<AppEntry>) : GridItem() {
        /** Ключ зависит от содержимого, поэтому при смене состава значок нарисуется заново. */
        val key = "folder:" + apps.take(PREVIEW_COUNT).joinToString { it.key }.hashCode()

        companion object {
            const val PREVIEW_COUNT = 4
        }
    }
}
