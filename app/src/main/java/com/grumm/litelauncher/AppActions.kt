package com.grumm.litelauncher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.core.content.getSystemService
import java.util.concurrent.Executor

/** Запуск приложения и меню по долгому нажатию. */
class AppActions(
    private val context: Context,
    private val pins: PinStore,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val onPinsChanged: () -> Unit,
    private val onHide: (AppEntry) -> Unit,
) {
    private class ShortcutRow(val info: ShortcutInfo, val icon: Drawable?)

    private val launcherApps = context.getSystemService<LauncherApps>()!!

    /** Окно приложения вырастает из его иконки. */
    fun launch(entry: AppEntry, source: View) {
        guarded { launcherApps.startMainActivity(entry.info.componentName, entry.info.user, boundsOf(source), optionsFor(source)) }
    }

    /** Ярлыки читаются через binder и с диска, поэтому меню открывается после их загрузки. */
    fun showMenu(entry: AppEntry, anchor: View) = executor.execute {
        val shortcuts = loadShortcuts(entry)
        mainHandler.post {
            if (anchor.isAttachedToWindow) ActionPopup.show(anchor, shortcutRows(shortcuts, anchor) + actionRows(entry, shortcuts.isNotEmpty()))
        }
    }

    /** Система отдаёт ярлыки только лаунчеру, назначенному по умолчанию. */
    private fun loadShortcuts(entry: AppEntry): List<ShortcutRow> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.info.componentName.packageName)
            .setActivity(entry.info.componentName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val density = context.resources.displayMetrics.densityDpi
        launcherApps.getShortcuts(query, entry.info.user).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(MAX_SHORTCUTS)
            .map { ShortcutRow(it, launcherApps.getShortcutIconDrawable(it, density)) }
    }.getOrDefault(emptyList())

    private fun shortcutRows(shortcuts: List<ShortcutRow>, anchor: View) = shortcuts.map { shortcut ->
        ActionPopup.Row(shortcut.info.shortLabel ?: "", shortcut.icon) {
            guarded { launcherApps.startShortcut(shortcut.info, boundsOf(anchor), optionsFor(anchor)) }
        }
    }

    private fun actionRows(entry: AppEntry, afterShortcuts: Boolean): List<ActionPopup.Row> {
        val pinLabel = if (pins.isPinned(entry.key)) R.string.menu_unpin else R.string.menu_pin
        return listOf(
            ActionPopup.Row(context.getString(pinLabel), startsGroup = afterShortcuts) { togglePin(entry) },
            ActionPopup.Row(context.getString(R.string.menu_details)) { showDetails(entry) },
            ActionPopup.Row(context.getString(R.string.menu_hide)) { onHide(entry) },
            ActionPopup.Row(context.getString(R.string.menu_uninstall)) { uninstall(entry) },
        )
    }

    private fun boundsOf(source: View) = Rect().also(source::getGlobalVisibleRect)

    private fun optionsFor(source: View) =
        ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.width, source.height).toBundle()

    private fun togglePin(entry: AppEntry) {
        if (!pins.isPinned(entry.key) && pins.isFull) {
            val message = context.getString(R.string.pin_limit, PinStore.MAX_PINS)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        pins.toggle(entry.key)
        onPinsChanged()
    }

    // Открывает ACTION_APPLICATION_DETAILS_SETTINGS и, в отличие от прямого Intent, учитывает профиль.
    private fun showDetails(entry: AppEntry) = guarded {
        launcherApps.startAppDetailsActivity(entry.info.componentName, entry.info.user, null, null)
    }

    private fun uninstall(entry: AppEntry) = guarded {
        val uri = Uri.fromParts("package", entry.info.componentName.packageName, null)
        val intent = Intent(Intent.ACTION_DELETE, uri)
            .putExtra(Intent.EXTRA_USER, entry.info.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private inline fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            showLaunchError()
        } catch (_: SecurityException) {
            showLaunchError()
        } catch (_: IllegalStateException) {
            showLaunchError()
        }
    }

    private fun showLaunchError() =
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MAX_SHORTCUTS = 4
    }
}
