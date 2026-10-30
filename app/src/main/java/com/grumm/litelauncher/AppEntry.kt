package com.grumm.litelauncher

import android.content.pm.LauncherActivityInfo

/** Приложение в списке. [key] уникален среди профилей и сохраняется в закреплённых. */
class AppEntry(val info: LauncherActivityInfo, val label: String, val key: String) {
    val id: Long = key.hashCode().toLong()
}
