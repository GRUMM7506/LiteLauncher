package com.grumm.litelauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewStub
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var icons: IconLoader
    private lateinit var pins: PinStore
    private lateinit var actions: AppActions
    private lateinit var repository: AppRepository

    private lateinit var root: SwipeUpLayout
    private lateinit var home: View
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var hint: View

    private var drawer: AppDrawer? = null
    private var pinnedEntries = emptyList<AppEntry>()
    private var apps = emptyList<AppEntry>()

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        createComponents()
        bindViews()
        applyWindowInsets()
        root.onOpenRequest = ::openDrawer
        repository.start()
        loadInBackground()
        prepareDrawerWhenIdle()
    }

    override fun onStart() {
        super.onStart()
        updateClock()
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    override fun onStop() {
        unregisterReceiver(timeReceiver)
        drawer?.close()
        super.onStop()
    }

    override fun onDestroy() {
        repository.stop()
        executor.shutdown()
        super.onDestroy()
    }

    /** Нажатие «Домой», пока лаунчер уже на экране. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawer?.close()
    }

    @Deprecated("Система вызывает его, пока в манифесте enableOnBackInvokedCallback=false")
    override fun onBackPressed() {
        drawer?.close()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            icons.clear()
            drawer?.releaseViews()
        }
    }

    private fun createComponents() {
        icons = IconLoader(this, executor, mainHandler)
        pins = PinStore(this)
        actions = AppActions(this, pins, ::renderPinned)
        repository = AppRepository(this, executor, mainHandler, ::onAppsChanged)
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        pinnedRow = findViewById(R.id.pinned)
        hint = findViewById(R.id.hint)
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
        val keys = pins.load()
        val resolved = keys.mapNotNull(repository::resolve)
        mainHandler.post { onPinsLoaded(keys, resolved) }
        repository.loadAll()
    }

    private fun onPinsLoaded(keys: List<String>, resolved: List<AppEntry>) {
        pins.setLoaded(keys)
        pinnedEntries = resolved
        showPinned(resolved)
    }

    private fun onAppsChanged(updated: List<AppEntry>, stale: List<AppEntry>, uninstalled: Boolean) {
        apps = updated
        stale.forEach { icons.remove(it.key) }
        if (uninstalled) pins.removeAll(stale.map { it.key })
        renderPinned()
        drawer?.setApps(updated)
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
        view.text = entry.label
        icons.bind(entry, view, onTop = true)
        view.setOnClickListener { actions.launch(entry, it) }
        view.setOnLongClickListener {
            actions.showMenu(entry, it)
            true
        }
    }

    /** Создаём список заранее, чтобы первое открытие не тратило время на загрузку классов. */
    private fun prepareDrawerWhenIdle() {
        Looper.myQueue().addIdleHandler {
            obtainDrawer()
            false
        }
    }

    private fun obtainDrawer(): AppDrawer = drawer ?: createDrawer().also { drawer = it }

    private fun createDrawer(): AppDrawer {
        val view = findViewById<ViewStub>(R.id.drawer_stub).inflate()
        return AppDrawer(view, root, home, icons, actions::launch, actions::showMenu) {
            root.gesturesEnabled = true
        }.apply { setApps(apps) }
    }

    private fun openDrawer() {
        root.gesturesEnabled = false
        obtainDrawer().open()
    }

    private companion object {
        const val DATE_SKELETON = "EEEEdMMMM"
    }
}
