package com.grumm.litelauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.getSystemService

/** Запуск приложения и контекстное меню. */
class AppActions(
    private val context: Context,
    private val pins: PinStore,
    private val onPinsChanged: () -> Unit,
) {
    private val launcherApps = context.getSystemService<LauncherApps>()!!

    fun launch(entry: AppEntry, source: View) {
        val bounds = Rect().also(source::getGlobalVisibleRect)
        guarded { launcherApps.startMainActivity(entry.info.componentName, entry.info.user, bounds, null) }
    }

    fun showMenu(entry: AppEntry, anchor: View) {
        val pinned = pins.isPinned(entry.key)
        PopupMenu(context, anchor).apply {
            menu.add(0, ITEM_PIN, 0, if (pinned) R.string.menu_unpin else R.string.menu_pin)
            menu.add(0, ITEM_DETAILS, 1, R.string.menu_details)
            menu.add(0, ITEM_UNINSTALL, 2, R.string.menu_uninstall)
            setOnMenuItemClickListener { onMenuItem(it.itemId, entry) }
            show()
        }
    }

    private fun onMenuItem(itemId: Int, entry: AppEntry): Boolean {
        when (itemId) {
            ITEM_PIN -> togglePin(entry)
            ITEM_DETAILS -> showDetails(entry)
            ITEM_UNINSTALL -> uninstall(entry)
        }
        return true
    }

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
        }
    }

    private fun showLaunchError() =
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()

    private companion object {
        const val ITEM_PIN = 1
        const val ITEM_DETAILS = 2
        const val ITEM_UNINSTALL = 3
    }
}
