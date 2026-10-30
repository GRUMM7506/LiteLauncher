package com.grumm.litelauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewStub
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), GestureRootLayout.Listener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val easeOut = PathInterpolator(0.2f, 0f, 0f, 1f)

    private lateinit var icons: IconLoader
    private lateinit var pins: PinStore
    private lateinit var actions: AppActions
    private lateinit var repository: AppRepository
    private lateinit var widgets: WidgetPanel
    private lateinit var contrast: WallpaperContrast

    private lateinit var root: GestureRootLayout
    private lateinit var home: View
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var hint: View
    private lateinit var menuAnchor: View

    private var settings = LauncherSettings()
    private var loaded = false
    private var drawer: AppDrawer? = null
    private var transition: DrawerTransition? = null
    private var folderPanel: FolderPanel? = null
    private var darkText = false
    private var pinnedEntries = emptyList<AppEntry>()
    private var apps = emptyList<AppEntry>()

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    override val isDrawerOpen get() = transition?.isOpen == true
    override val swipeDownEnabled get() = settings.swipeDownShade
    override val isFolderOpen get() = folderPanel?.isOpen == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        bindViews()
        createComponents()
        applyWindowInsets()
        repository.start()
        contrast.start()
        loadInBackground()
    }

    /** Смена темы приходит сюда: uiMode объявлен в configChanges манифеста. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (loaded) syncThemedWallpaper()
    }

    override fun onStart() {
        super.onStart()
        updateClock()
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        if (loaded) onReturned()
    }

    override fun onStop() {
        unregisterReceiver(timeReceiver)
        folderPanel?.closeNow()
        drawer?.onClosing()
        transition?.snapClosed()
        if (loaded) widgets.stop()
        super.onStop()
    }

    override fun onDestroy() {
        contrast.stop()
        repository.stop()
        executor.shutdown()
        super.onDestroy()
    }

    /** Нажатие «Домой», пока лаунчер уже на экране. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        folderPanel?.closeNow()
        closeDrawer()
    }

    @Deprecated("Система вызывает его, пока в манифесте enableOnBackInvokedCallback=false")
    override fun onBackPressed() = closeDrawer()

    @Deprecated("Activity без AndroidX получает результат только так")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        widgets.onActivityResult(requestCode, resultCode)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) icons.clear()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) drawer?.releaseViews()
    }

    override fun catchMotion(): Boolean {
        val running = transition?.takeIf { it.isAnimating } ?: return false
        running.beginDrag()
        return true
    }

    override fun isDragBlocked(x: Float, y: Float) = widgets.contains(x, y)

    override fun onDragStart() {
        val current = obtainDrawer()
        if (isDrawerOpen) current.onClosing()
        transition?.beginDrag()
    }

    override fun onDragBy(deltaY: Float) {
        transition?.dragBy(deltaY)
    }

    override fun onDragEnd(velocityY: Float) {
        transition?.release(velocityY)
    }

    override fun onSwipeDown() = expandNotificationShade()

    override fun onTap() = openDrawer()

    override fun onLongPress(x: Float, y: Float) = showHomeMenu(x, y)

    private fun bindViews() {
        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        pinnedRow = findViewById(R.id.pinned)
        hint = findViewById(R.id.hint)
        menuAnchor = findViewById(R.id.menu_anchor)
    }

    private fun createComponents() {
        icons = IconLoader(this, executor, mainHandler)
        pins = PinStore(this)
        actions = AppActions(this, pins, ::renderPinned, ::hideApp)
        repository = AppRepository(this, executor, mainHandler, ::onAppsChanged)
        widgets = WidgetPanel(this, findViewById(R.id.widgets), executor, mainHandler)
        contrast = WallpaperContrast(this, executor, mainHandler, ::onContrastChanged)
    }

    /** Тёмный текст и значки строки состояния нужны только на светлых обоях и без подложки списка. */
    private fun onContrastChanged(dark: Boolean) {
        darkText = dark
        val views = listOf(clock, date, hint as TextView) +
            (0 until pinnedRow.childCount).map { pinnedRow.getChildAt(it) as TextView }
        views.forEach(::styleForWallpaper)
        updateSystemBars()
    }

    private fun styleForWallpaper(view: TextView) {
        if (darkText) {
            view.setTextColor(DARK_TEXT)
            view.setShadowLayer(0f, 0f, 0f, 0)
        } else {
            view.setTextColor(Color.WHITE)
            view.setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, SHADOW_COLOR)
        }
    }

    private fun updateSystemBars() {
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        val wanted = if (darkText && !isDrawerOpen) lightBars else 0
        window.insetsController?.setSystemBarsAppearance(wanted, lightBars)
    }

    private fun syncThemedWallpaper() {
        val bounds = windowManager.currentWindowMetrics.bounds
        ThemedWallpaper.sync(this, executor, settings.themedWallpaper, bounds.width(), bounds.height())
    }

    private fun applyWindowInsets() {
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }

    private fun updateClock() {
        val now = Date()
        clock.text = DateFormat.getTimeFormat(this).format(now)
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON)
        date.text = DateFormat.format(pattern, now)
    }

    /** Закреплённые показываем сразу, не дожидаясь чтения меток всех приложений. */
    private fun loadInBackground() = executor.execute {
        val savedSettings = LauncherSettings.load(this)
        val savedWidgets = WidgetPanel.loadSaved(this)
        val keys = pins.load()
        val resolved = keys.mapNotNull(repository::resolve)
        mainHandler.post { onLoaded(savedSettings, savedWidgets, keys, resolved) }
        repository.loadAll()
    }

    private fun onLoaded(
        savedSettings: LauncherSettings,
        savedWidgets: List<Pair<Int, Int>>,
        keys: List<String>,
        resolved: List<AppEntry>,
    ) {
        loaded = true
        applySettings(savedSettings)
        syncThemedWallpaper()
        widgets.restore(savedWidgets)
        pins.setLoaded(keys)
        pinnedEntries = resolved
        showPinned(resolved)
        root.listener = this
        revealHome()
        prepareDrawerWhenIdle()
    }

    /** Возврат на экран: подхватываем настройки и мягко показываем главный экран. */
    private fun onReturned() {
        val current = LauncherSettings.load(this)
        if (current != settings) {
            applySettings(current)
            renderPinned()
            publishAppsToDrawer()
        }
        syncThemedWallpaper()
        widgets.start()
        drawer?.restoreViews()
        revealHome()
    }

    private fun revealHome() {
        home.alpha = 0f
        home.scaleX = REVEAL_SCALE
        home.scaleY = REVEAL_SCALE
        home.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(easeOut).setDuration(REVEAL_MS).start()
    }

    private fun applySettings(updated: LauncherSettings) {
        settings = updated
        icons.setIconSize(updated.iconSizeDp)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, updated.clockSizeSp.toFloat())
        transition?.applySettings(updated)
        drawer?.applySettings(updated)
    }

    private fun onAppsChanged(updated: List<AppEntry>, stale: List<AppEntry>, uninstalled: Boolean) {
        apps = updated
        stale.forEach { icons.remove(it.key) }
        if (uninstalled) pins.removeAll(stale.map { it.key })
        renderPinned()
        publishAppsToDrawer()
    }

    private fun publishAppsToDrawer() {
        folderPanel?.closeNow()
        drawer?.setApps(visibleApps())
    }

    private fun visibleApps() = apps.filter { it.key !in settings.hidden }

    private fun hideApp(entry: AppEntry) {
        applySettings(settings.copy(hidden = settings.hidden + entry.key))
        settings.store(this)
        publishAppsToDrawer()
    }

    private fun renderPinned() {
        val byKey = apps.associateBy { it.key }
        val known = pinnedEntries.associateBy { it.key }
        pinnedEntries = pins.keys.mapNotNull { byKey[it] ?: known[it] }
        showPinned(pinnedEntries)
    }

    private fun showPinned(entries: List<AppEntry>) {
        hint.isVisible = entries.isEmpty()
        while (pinnedRow.childCount > entries.size) pinnedRow.removeViewAt(pinnedRow.childCount - 1)
        while (pinnedRow.childCount < entries.size) pinnedRow.addView(newPinnedView())
        entries.forEachIndexed { index, entry -> bindPinned(pinnedRow.getChildAt(index) as TextView, entry) }
    }

    private fun newPinnedView() =
        LayoutInflater.from(this).inflate(R.layout.item_pinned, pinnedRow, false)

    private fun bindPinned(view: TextView, entry: AppEntry) {
        view.text = if (settings.showLabels) entry.label else ""
        styleForWallpaper(view)
        icons.bind(entry, view)
        view.setOnClickListener { actions.launch(entry, it) }
        view.setOnLongClickListener {
            actions.showMenu(entry, it)
            true
        }
    }

    /** Создаём сетку заранее, чтобы первый же жест шёл без подгрузки классов и разметки. */
    private fun prepareDrawerWhenIdle() {
        Looper.myQueue().addIdleHandler {
            obtainDrawer()
            false
        }
    }

    private fun obtainDrawer(): AppDrawer = drawer ?: createDrawer().also { drawer = it }

    private fun createDrawer(): AppDrawer {
        val view = findViewById<ViewStub>(R.id.drawer_stub).inflate()
        val created = AppDrawer(view, icons, actions::launch, actions::showMenu, ::openFolder, ::closeDrawer)
        created.applySettings(settings)
        created.setApps(visibleApps())
        transition = DrawerTransition(root, home, view, ::onTransitionSettled).apply { applySettings(settings) }
        return created
    }

    private fun onTransitionSettled(open: Boolean) {
        if (open) drawer?.onOpened() else drawer?.onClosed()
        updateSystemBars()
    }

    private fun openDrawer() {
        obtainDrawer()
        transition?.settle(open = true)
        updateSystemBars()
    }

    /** «Назад» закрывает слоями: сперва папку, затем список. */
    private fun closeDrawer() {
        folderPanel?.takeIf { it.isOpen }?.let {
            it.close()
            return
        }
        if (!isDrawerOpen) return
        drawer?.onClosing()
        transition?.settle(open = false)
        updateSystemBars()
    }

    private fun openFolder(folder: GridItem.Folder) {
        val panel = folderPanel ?: createFolderPanel().also { folderPanel = it }
        panel.open(folder, settings)
    }

    private fun createFolderPanel(): FolderPanel {
        val view = findViewById<ViewStub>(R.id.folder_stub).inflate()
        return FolderPanel(view, icons, actions::launch, actions::showMenu)
    }

    private fun showHomeMenu(x: Float, y: Float) {
        menuAnchor.x = x
        menuAnchor.y = y
        menuAnchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        PopupMenu(this, menuAnchor).apply {
            menu.add(0, ITEM_WIDGET, 0, R.string.menu_add_widget)
            menu.add(0, ITEM_WALLPAPER, 1, R.string.menu_wallpaper)
            menu.add(0, ITEM_SETTINGS, 2, R.string.menu_settings)
            setOnMenuItemClickListener { onHomeMenuItem(it.itemId) }
            show()
        }
    }

    private fun onHomeMenuItem(itemId: Int): Boolean {
        when (itemId) {
            ITEM_WIDGET -> widgets.showPicker()
            ITEM_WALLPAPER -> startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.menu_wallpaper)),
            )
            ITEM_SETTINGS -> startActivity(
                Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }

    /** Публичного API нет; если система откажет, жест просто ничего не делает. */
    private fun expandNotificationShade() {
        runCatching {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    private companion object {
        const val DATE_SKELETON = "EEEEdMMMM"
        const val REVEAL_SCALE = 0.96f
        const val REVEAL_MS = 260L
        const val DARK_TEXT = 0xE61A1F36.toInt()
        const val SHADOW_COLOR = 0x99000000.toInt()
        const val SHADOW_RADIUS = 4f
        const val SHADOW_DY = 1f
        const val ITEM_WIDGET = 1
        const val ITEM_WALLPAPER = 2
        const val ITEM_SETTINGS = 3
    }
}
