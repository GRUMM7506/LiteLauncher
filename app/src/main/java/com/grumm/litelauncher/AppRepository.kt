package com.grumm.litelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.getSystemService
import java.text.Collator
import java.util.concurrent.Executor

/**
 * Список приложений. Полная копия [all] живёт в фоновом потоке,
 * в UI-поток публикуются неизменяемые снимки.
 */
class AppRepository(
    context: Context,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val onChanged: (apps: List<AppEntry>, stale: List<AppEntry>, uninstalled: Boolean) -> Unit,
) {
    private val launcherApps = context.getSystemService<LauncherApps>()!!
    private val userManager = context.getSystemService<UserManager>()!!
    private val collator = Collator.getInstance()
    private val byLabel = compareBy<AppEntry, String>(collator) { it.label }
    private var all = emptyList<AppEntry>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) =
            refresh(listOf(packageName), user, uninstalled = true)

        override fun onPackageAdded(packageName: String, user: UserHandle) =
            refresh(listOf(packageName), user)

        override fun onPackageChanged(packageName: String, user: UserHandle) =
            refresh(listOf(packageName), user)

        override fun onPackagesAvailable(names: Array<String>, user: UserHandle, replacing: Boolean) =
            refresh(names.asList(), user)

        override fun onPackagesUnavailable(names: Array<String>, user: UserHandle, replacing: Boolean) =
            refresh(names.asList(), user)
    }

    fun start() = launcherApps.registerCallback(callback, mainHandler)

    fun stop() = launcherApps.unregisterCallback(callback)

    /** Быстро находит одно приложение по ключу, без загрузки всего списка. Только фоновый поток. */
    fun resolve(key: String): AppEntry? {
        val separator = key.lastIndexOf(KEY_SEPARATOR)
        if (separator < 0) return null
        val component = ComponentName.unflattenFromString(key.substring(0, separator)) ?: return null
        val serial = key.substring(separator + 1).toLongOrNull() ?: return null
        val user = userManager.getUserForSerialNumber(serial) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        return launcherApps.resolveActivity(intent, user)?.let(::toEntry)
    }

    /** Только фоновый поток. */
    fun loadAll() {
        val loaded = launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) }
        publish(loaded.map(::toEntry).sortedWith(byLabel), emptyList(), uninstalled = false)
    }

    /** Перечитывает только изменившиеся пакеты, остальной список не трогает. */
    private fun refresh(packages: List<String>, user: UserHandle, uninstalled: Boolean = false) = executor.execute {
        val (stale, kept) = all.partition { it.info.user == user && it.info.componentName.packageName in packages }
        val fresh = packages.flatMap { launcherApps.getActivityList(it, user) }.map(::toEntry)
        publish((kept + fresh).sortedWith(byLabel), stale, uninstalled)
    }

    private fun publish(apps: List<AppEntry>, stale: List<AppEntry>, uninstalled: Boolean) {
        all = apps
        mainHandler.post { onChanged(apps, stale, uninstalled) }
    }

    private fun toEntry(info: LauncherActivityInfo): AppEntry {
        val serial = userManager.getSerialNumberForUser(info.user)
        val key = info.componentName.flattenToString() + KEY_SEPARATOR + serial
        return AppEntry(info, info.label.toString(), key)
    }

    private companion object {
        const val KEY_SEPARATOR = '#'
    }
}
