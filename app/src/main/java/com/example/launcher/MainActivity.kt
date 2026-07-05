package com.example.launcher

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())
    private var allApps = listOf<AppInfo>()
    private var isKeyboardVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindow()
        applyThemeColors()
        setupTopBar()
        setupBookmarks()
        setupAppList()
        setupFilter()
        setupKeyboardListener()
        setupSettings()

        loadApps()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "android.content.pm.action.CONFIRM_PIN_SHORTCUT") {
            handleConfirmPinShortcut(intent)
        }
    }

    private fun handleConfirmPinShortcut(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val request = launcherApps.getPinItemRequest(intent)
            if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                val shortcutInfo = request.shortcutInfo
                if (shortcutInfo != null) {
                    if (request.isValid) {
                        request.accept()
                        val pkg = shortcutInfo.`package`
                        val shortcutId = shortcutInfo.id
                        val id = "shortcut:$pkg:$shortcutId"
                        Prefs.addBookmark(id)
                        Toast.makeText(this, "Shortcut pinned: ${shortcutInfo.shortLabel}", Toast.LENGTH_SHORT).show()
                        loadApps()
                        handler.postDelayed({ loadApps() }, 500)
                    }
                }
            }
        }
    }

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun applyThemeColors() {
        val bg = ThemeUtils.getBackgroundColor()
        val text = ThemeUtils.getTextColor()
        val textSecondary = ThemeUtils.getSecondaryTextColor()
        val accent = Prefs.getAccentColor()

        binding.root.setBackgroundColor(bg)
        binding.topBar.setBackgroundColor(bg)

        binding.timeText.setTextColor(text)
        binding.dateText.setTextColor(text)
        binding.nextAlarm.setTextColor(textSecondary)
        binding.emptyState.setTextColor(textSecondary)

        binding.filter.setTextColor(text)
        binding.filter.setHintTextColor(textSecondary)

        binding.settingsBtn.setColorFilter(accent)
        binding.clearBtn.setColorFilter(accent)
        binding.playBtn.setTextColor(accent)

        ViewCompat.setBackgroundTintList(binding.filter, android.content.res.ColorStateList.valueOf(accent))
    }

    private fun setupTopBar() {
        updateDateTime()
        updateAlarm()

        handler.postDelayed(object : Runnable {
            override fun run() {
                updateDateTime()
                updateAlarm()
                handler.postDelayed(this, 60000)
            }
        }, 60000)

        binding.timeText.setOnClickListener {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        }

        binding.dateText.setOnClickListener {
            openCalendar()
        }

        binding.nextAlarm.setOnClickListener {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        }
    }

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }

        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(System.currentTimeMillis().toString())
                .build()
        }

        if (fallback.resolveActivity(packageManager) != null) {
            startActivity(fallback)
            return
        }

        Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
    }

    private fun updateDateTime() {
        val now = Date()
        binding.timeText.text = timeFormat.format(now)
        binding.dateText.text = dateFormat.format(now)
    }

    private fun updateAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock
        binding.nextAlarm.text = if (nextAlarm != null) {
            val time = android.text.format.DateFormat.getTimeFormat(this).format(Date(nextAlarm.triggerTime))
            "Alarm: $time"
        } else {
            ""
        }
    }

    private fun setupBookmarks() {
        bookmarkAdapter = BookmarkAdapter(
            onClick = { app -> launchApp(app) },
            onLongClick = { app -> showBookmarkOptions(app) }
        )
        binding.bookmarksGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, calculateSpanCount())
            adapter = bookmarkAdapter
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                val item = bookmarkAdapter.getItemAt(position)
                if (item == null) {
                    return makeMovementFlags(0, 0)
                }
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                bookmarkAdapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val newOrder = bookmarkAdapter.getItems()
                    .filterNotNull()
                    .map { it.packageName }
                    .reversed()
                Prefs.saveBookmarks(newOrder)
                loadBookmarks()
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.bookmarksGrid)
    }

    private fun calculateSpanCount(): Int {
        val dm = resources.displayMetrics
        val dpWidth = dm.widthPixels / dm.density
        return (dpWidth / 80).toInt().coerceIn(4, 6)
    }

    private fun setupAppList() {
        appAdapter = AppAdapter(
            onClick = { app -> launchApp(app) },
            onLongClick = { app -> showAppOptions(app) }
        )
        binding.appList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.VERTICAL, true)
            adapter = appAdapter
        }
        // Scroll to bottom when adapter data changes
        appAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                scrollToBottom()
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                scrollToBottom()
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                scrollToBottom()
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                scrollToBottom()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                scrollToBottom()
            }
            override fun onChanged() {
                scrollToBottom()
            }
        })
    }

    private fun setupFilter() {
        binding.filter.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isKeyboardVisible) {
                showKeyboard()
            }
        }

        binding.filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val hasText = query.isNotEmpty()
                binding.clearBtn.visibility = if (hasText) android.view.View.VISIBLE else android.view.View.GONE
                binding.playBtn.visibility = if (hasText) android.view.View.VISIBLE else android.view.View.GONE
                filterApps(query)
            }
        })

        binding.clearBtn.setOnClickListener {
            binding.filter.text?.clear()
        }

        binding.playBtn.setOnClickListener {
            val query = binding.filter.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                // Launch the bottom-most app (best match at position 0 due to reverse layout)
                val filteredApps = appAdapter.currentList
                if (filteredApps.isNotEmpty()) {
                    launchApp(filteredApps[0])
                }
            }
        }
    }

    private fun setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                if (ime.bottom > 0) ime.bottom else systemBars.bottom
            )

            val wasVisible = isKeyboardVisible
            isKeyboardVisible = ime.bottom > systemBars.bottom

            if (wasVisible != isKeyboardVisible) {
                onKeyboardVisibilityChanged()
            }

            insets
        }
    }

    private fun onKeyboardVisibilityChanged() {
        if (isKeyboardVisible) {
            binding.bookmarksGrid.visibility = android.view.View.GONE
            binding.appList.visibility = android.view.View.VISIBLE
            filterApps(binding.filter.text?.toString() ?: "")
            scrollToBottom()
        } else {
            binding.bookmarksGrid.visibility = android.view.View.VISIBLE
            binding.appList.visibility = android.view.View.GONE
            binding.emptyState.visibility = android.view.View.GONE
            binding.filter.clearFocus()
            hideKeyboard()
            loadBookmarks()
        }
    }

    private fun setupSettings() {
        binding.settingsBtn.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun scrollToBottom() {
        binding.appList.post {
            if (appAdapter.itemCount > 0) {
                binding.appList.scrollToPosition(0)
            }
        }
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolves = packageManager.queryIntentActivities(intent, 0)
            val resolveMap = resolves.associateBy { it.activityInfo.packageName }

            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val shortcuts = if (launcherApps.hasShortcutHostPermission()) {
                try {
                    val query = LauncherApps.ShortcutQuery().apply {
                        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    }
                    launcherApps.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            val shortcutMap = shortcuts.associateBy { "shortcut:${it.`package`}:${it.id}" }

            // Phase 1: metadata only — labels appear instantly
            val appsNoIcons = resolves.map { resolve ->
                val pkg = resolve.activityInfo.packageName
                val label = resolve.loadLabel(packageManager).toString()
                val activity = resolve.activityInfo.name

                val appInfo = try {
                    packageManager.getApplicationInfo(pkg, 0)
                } catch (e: Exception) { null }

                val autoPrefix = getCategoryPrefix(appInfo)
                val userPrefix = Prefs.getAppPrefix(pkg)
                val prefix = if (!userPrefix.isNullOrBlank()) userPrefix else autoPrefix

                val display = if (prefix.isNotEmpty()) "$prefix - $label" else label

                AppInfo(
                    id = pkg,
                    label = label,
                    packageName = pkg,
                    activityName = activity,
                    prefix = prefix,
                    displayName = display,
                    icon = null
                )
            }

            val shortcutsNoIcons = shortcuts.map { shortcut ->
                val pkg = shortcut.`package`
                val shortcutId = shortcut.id
                val id = "shortcut:$pkg:$shortcutId"
                val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "Shortcut").toString()

                val autoPrefix = "Shortcut"
                val userPrefix = Prefs.getAppPrefix(id)
                val prefix = if (!userPrefix.isNullOrBlank()) userPrefix else autoPrefix

                val display = if (prefix.isNotEmpty()) "$prefix - $label" else label

                AppInfo(
                    id = id,
                    label = label,
                    packageName = pkg,
                    activityName = "",
                    prefix = prefix,
                    displayName = display,
                    icon = null,
                    shortcutId = shortcutId
                )
            }

            val allAppsNoIcons = (appsNoIcons + shortcutsNoIcons).sortedBy { it.displayName.lowercase() }
            allApps = allAppsNoIcons

            withContext(Dispatchers.Main) {
                loadBookmarks()
                if (isKeyboardVisible) {
                    filterApps(binding.filter.text?.toString() ?: "")
                }
            }

            // Phase 2: load icons in background
            val iconPackPkg = Prefs.getIconPack()
            val ctx = applicationContext
            val density = ctx.resources.displayMetrics.densityDpi
            val appsWithIcons = allAppsNoIcons.map { app ->
                if (app.shortcutId != null) {
                    val shortcut = shortcutMap[app.id]
                    val icon = if (shortcut != null && launcherApps.hasShortcutHostPermission()) {
                        try {
                            launcherApps.getShortcutIconDrawable(shortcut, density)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    app.copy(icon = icon)
                } else {
                    val resolve = resolveMap[app.packageName]
                    val defaultIcon = resolve?.loadIcon(packageManager)
                    val (icon, fromPack) = if (iconPackPkg.isNotBlank()) {
                        IconPack.loadIcon(
                            ctx,
                            iconPackPkg,
                            app.packageName,
                            app.activityName,
                            defaultIcon
                        )
                    } else {
                        defaultIcon to false
                    }
                    app.copy(icon = icon, iconFromPack = fromPack)
                }
            }

            allApps = appsWithIcons

            withContext(Dispatchers.Main) {
                loadBookmarks()
                if (isKeyboardVisible) {
                    filterApps(binding.filter.text?.toString() ?: "")
                }
            }
        }
    }

    private fun getCategoryPrefix(appInfo: ApplicationInfo?): String {
        if (appInfo == null) return ""
        return when (appInfo.category) {
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Image"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
            else -> {
                val desc = appInfo.loadDescription(packageManager)?.toString()
                if (!desc.isNullOrBlank()) desc else ""
            }
        }
    }

    private fun loadBookmarks() {
        val bookmarked = Prefs.getBookmarks()
        val bookmarks = allApps
            .filter { it.id in bookmarked }
            .sortedBy { bookmarked.indexOf(it.id) }
            .take(calculateSpanCount() * 2)
            .map { app ->
                val customLabel = Prefs.getCustomLabel(app.id)
                if (customLabel != null) app.copy(label = customLabel) else app
            }

        val spanCount = calculateSpanCount()
        val maxSlots = spanCount * 2
        val reversed = bookmarks.reversed()
        val padding = (maxSlots - reversed.size).coerceAtLeast(0)
        val padded = List(padding) { null } + reversed

        bookmarkAdapter.submitList(padded)
    }

    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            appAdapter.submitList(allApps)
            binding.emptyState.visibility = android.view.View.GONE
            return
        }

        val scored = allApps.map { app ->
            val score = FzfScorer.score(query, app.displayName)
            app to score
        }.filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenBy { it.first.displayName.lowercase() }
            )
            .map { it.first }

        appAdapter.submitList(scored)
        binding.emptyState.visibility = if (scored.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun launchApp(app: AppInfo) {
        if (app.shortcutId != null) {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            try {
                launcherApps.startShortcut(app.packageName, app.shortcutId, null, null, android.os.Process.myUserHandle())
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot launch shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppOptions(app: AppInfo) {
        val isBookmarked = Prefs.isBookmarked(app.id)
        val options = arrayOf(
            if (isBookmarked) "Remove bookmark" else "Add bookmark",
            "Edit prefix",
            "App info"
        )

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleBookmark(app)
                    1 -> editPrefix(app)
                    2 -> showAppInfo(app)
                }
            }
            .show()
    }

    private fun toggleBookmark(app: AppInfo) {
        if (Prefs.isBookmarked(app.id)) {
            Prefs.removeBookmark(app.id)
            loadBookmarks()
        } else {
            showAddBookmarkDialog(app)
        }
    }

    private fun showAddBookmarkDialog(app: AppInfo) {
        val input = EditText(this).apply {
            setText(app.label)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Set bookmark label")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val customLabel = input.text?.toString()?.trim() ?: ""
                Prefs.setCustomLabel(app.id, customLabel)
                Prefs.addBookmark(app.id)
                loadApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBookmarkOptions(app: AppInfo) {
        val options = arrayOf("Rename bookmark", "Remove bookmark")
        AlertDialog.Builder(this)
            .setTitle(app.label.ifEmpty { "Bookmark" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> renameBookmark(app)
                    1 -> removeBookmark(app)
                }
            }
            .show()
    }

    private fun renameBookmark(app: AppInfo) {
        val input = EditText(this).apply {
            setText(app.label)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Rename bookmark")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text?.toString()?.trim() ?: ""
                Prefs.setCustomLabel(app.id, newLabel)
                loadApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeBookmark(app: AppInfo) {
        Prefs.removeBookmark(app.id)
        loadBookmarks()
    }

    private fun editPrefix(app: AppInfo) {
        val input = EditText(this).apply {
            setText(app.prefix)
            hint = "e.g., LLM AI"
        }

        AlertDialog.Builder(this)
            .setTitle("Edit prefix for ${app.label}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val prefix = input.text?.toString()?.trim() ?: ""
                Prefs.setAppPrefix(app.id, prefix)
                loadApps()
            }
            .setNegativeButton("Remove") { _, _ ->
                Prefs.setAppPrefix(app.id, "")
                loadApps()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Theme", "Accent Color", "Icon Size", "Icon Pack", "Set as Default Launcher")

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemePicker()
                    1 -> showAccentPicker()
                    2 -> showIconSizePicker()
                    3 -> showIconPackPicker()
                    4 -> promptSetDefaultLauncher()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showThemePicker() {
        val themes = arrayOf("Light", "Dark", "OLED", "Sepia")
        val current = Prefs.getTheme()
        val currentIndex = themes.indexOfFirst { it.lowercase() == current }

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selected = themes[which].lowercase()
                if (selected != current) {
                    Prefs.setTheme(selected)
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAccentPicker() {
        val colors = intArrayOf(
            Color.parseColor("#FF4081"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#F44336"),
            Color.parseColor("#00BCD4"),
            Color.parseColor("#FFEB3B"),
        )
        val names = arrayOf("Pink", "Blue", "Green", "Orange", "Purple", "Red", "Cyan", "Yellow")

        AlertDialog.Builder(this)
            .setTitle("Accent Color")
            .setItems(names) { _, which ->
                Prefs.setAccentColor(colors[which])
                recreate()
            }
            .show()
    }

    private fun showIconSizePicker() {
        val sizes = arrayOf("Default", "Small", "Rounded")
        val current = Prefs.getIconSize()
        val currentIndex = sizes.indexOfFirst { it.lowercase() == current }

        AlertDialog.Builder(this)
            .setTitle("Icon Size")
            .setSingleChoiceItems(sizes, currentIndex) { dialog, which ->
                val selected = sizes[which].lowercase()
                if (selected != current) {
                    Prefs.setIconSize(selected)
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showIconPackPicker() {
        val packs = IconPack.discover(this)
        val current = Prefs.getIconPack()
        val currentIndex = packs.indexOfFirst { it.first == current }.coerceAtLeast(0)

        val labels = packs.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Icon Pack")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selected = packs[which].first
                if (selected != current) {
                    IconPack.clearCache()
                    Prefs.setIconPack(selected)
                    loadApps()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val count = IconPack.getAppFilterSize()
                        Toast.makeText(this, "Icon pack: $count mappings loaded", Toast.LENGTH_SHORT).show()
                    }, 1500)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSetDefaultLauncher() {
        AlertDialog.Builder(this)
            .setTitle("Set as default launcher?")
            .setMessage("This app needs to be your default launcher.")
            .setPositiveButton("Set") { _, _ ->
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.filter, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.filter.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isKeyboardVisible) {
            binding.filter.clearFocus()
            hideKeyboard()
        }
    }
}
