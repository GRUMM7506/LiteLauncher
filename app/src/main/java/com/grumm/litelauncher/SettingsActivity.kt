package com.grumm.litelauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import java.util.concurrent.Executors

/** Экран настроек. Отдельная Activity: её классы не грузятся, пока экран не открыт. */
class SettingsActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settings: LauncherSettings
    private lateinit var content: LinearLayout
    private lateinit var accessRow: TextView
    private lateinit var homeRow: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = LauncherSettings.load(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content)
        })
        addRows()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionRows()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun addRows() {
        addSlider(R.string.settings_columns, 3..7, settings.columns) { settings.copy(columns = it) }
        addSlider(R.string.settings_rows, 4..9, settings.rows) { settings.copy(rows = it) }
        addSlider(R.string.settings_icon_size, 36..72, settings.iconSizeDp) { settings.copy(iconSizeDp = it) }
        addSwitch(R.string.settings_labels, settings.showLabels) { settings.copy(showLabels = it) }
        addSlider(R.string.settings_scrim, 0..100, settings.scrimPercent, { "$it%" }) { settings.copy(scrimPercent = it) }
        addSlider(R.string.settings_clock_size, 40..110, settings.clockSizeSp) { settings.copy(clockSizeSp = it) }
        addSlider(R.string.settings_motion, 0..2, settings.motion, ::motionName) { settings.copy(motion = it) }
        addSwitch(R.string.settings_haptics, settings.haptics) { settings.copy(haptics = it) }
        addSwitch(R.string.settings_keyboard, settings.autoKeyboard) { settings.copy(autoKeyboard = it) }
        addSwitch(R.string.settings_shade, settings.swipeDownShade) { settings.copy(swipeDownShade = it) }
        addSwitch(R.string.settings_folders, settings.autoFolders) { settings.copy(autoFolders = it) }
        addSwitch(R.string.settings_themed_wallpaper, settings.themedWallpaper) { settings.copy(themedWallpaper = it) }
        addSlider(R.string.settings_badges, 0..2, settings.badgeStyle, ::badgeName) { settings.copy(badgeStyle = it) }
        accessRow = addAction(R.string.settings_badge_access_off, ::openNotificationAccess)
        homeRow = addAction(R.string.settings_default_off, ::openHomeSettings)
        addAction(R.string.settings_live_wallpaper, ::openLiveWallpaper)
        addAction(R.string.menu_wallpaper, ::openWallpaperPicker)
        addAction(R.string.settings_hidden, ::showHiddenApps)
    }

    private fun motionName(level: Int) = getString(
        when (level) {
            LauncherSettings.MOTION_RELAXED -> R.string.motion_relaxed
            LauncherSettings.MOTION_FAST -> R.string.motion_fast
            else -> R.string.motion_normal
        },
    )

    private fun addSlider(
        titleRes: Int,
        range: IntRange,
        value: Int,
        format: (Int) -> String = { it.toString() },
        update: (Int) -> LauncherSettings,
    ) {
        val title = newText()
        fun showValue(current: Int) {
            title.text = "${getString(titleRes)}: ${format(current)}"
        }
        showValue(value)
        content.addView(title)
        content.addView(SeekBar(this).apply {
            min = range.first
            max = range.last
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, current: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    showValue(current)
                    save(update(current))
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun addSwitch(titleRes: Int, value: Boolean, update: (Boolean) -> LauncherSettings) {
        content.addView(Switch(this).apply {
            setText(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            setPadding(0, dp(16), 0, dp(16))
            isChecked = value
            setOnCheckedChangeListener { _, checked -> save(update(checked)) }
        })
    }

    private fun addAction(titleRes: Int, action: () -> Unit): TextView = newText().apply {
        setText(titleRes)
        setOnClickListener { action() }
        content.addView(this)
    }

    private fun badgeName(style: Int) = getString(
        when (style) {
            LauncherSettings.BADGE_OFF -> R.string.badge_off
            LauncherSettings.BADGE_NUMBER -> R.string.badge_number
            else -> R.string.badge_dot
        },
    )

    /** Разрешения выдаются в системных настройках, поэтому статус обновляем при возврате сюда. */
    private fun updatePermissionRows() {
        val listening = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        accessRow.setText(if (listening) R.string.settings_badge_access_on else R.string.settings_badge_access_off)
        val isHost = getSystemService<LauncherApps>()?.hasShortcutHostPermission() == true
        homeRow.setText(if (isHost) R.string.settings_default_on else R.string.settings_default_off)
    }

    private fun openNotificationAccess() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun openHomeSettings() = startActivity(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun newText() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun save(updated: LauncherSettings) {
        settings = updated
        updated.store(this)
    }

    /** Живые обои система разрешает ставить только после подтверждения пользователем. */
    private fun openLiveWallpaper() {
        val component = ComponentName(this, LiteWallpaperService::class.java)
        startActivity(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component),
        )
    }

    private fun openWallpaperPicker() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.menu_wallpaper)))
    }

    private fun showHiddenApps() {
        val keys = settings.hidden.toList()
        if (keys.isEmpty()) {
            Toast.makeText(this, R.string.settings_hidden_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val resolver = AppRepository(this, executor, mainHandler) { _, _, _ -> }
        executor.execute {
            val entries = keys.mapNotNull(resolver::resolve)
            mainHandler.post { showHiddenDialog(entries, keys) }
        }
    }

    /** Приложения, которых уже нет на устройстве, из списка скрытых просто убираем. */
    private fun showHiddenDialog(entries: List<AppEntry>, keys: List<String>) {
        val present = entries.map { it.key }
        save(settings.copy(hidden = keys.filter { it in present }.toSet()))
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.settings_hidden_empty, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_hidden_hint)
            .setItems(entries.map { it.label }.toTypedArray()) { _, which ->
                save(settings.copy(hidden = settings.hidden - entries[which].key))
            }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TEXT_SP = 16f
    }
}
