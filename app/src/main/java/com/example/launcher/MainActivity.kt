package com.example.launcher

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
        bookmarkAdapter = BookmarkAdapter { app -> launchApp(app) }
        binding.bookmarksGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, calculateSpanCount())
            adapter = bookmarkAdapter
        }
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
                binding.clearBtn.visibility = if (query.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                filterApps(query)
                scrollToBottom()
            }
        })

        binding.clearBtn.setOnClickListener {
            binding.filter.text?.clear()
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

            // Phase 1: metadata only — labels appear instantly
            val appsNoIcons = resolves.map { resolve ->
                val label = resolve.loadLabel(packageManager).toString()
                val pkg = resolve.activityInfo.packageName
                val activity = resolve.activityInfo.name

                val appInfo = try {
                    packageManager.getApplicationInfo(pkg, 0)
                } catch (e: Exception) { null }

                val autoPrefix = getCategoryPrefix(appInfo)
                val userPrefix = Prefs.getAppPrefix(pkg)
                val prefix = if (!userPrefix.isNullOrBlank()) userPrefix else autoPrefix

                val display = if (prefix.isNotEmpty()) "$prefix - $label" else label

                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = activity,
                    prefix = prefix,
                    displayName = display,
                    icon = null
                )
            }.sortedBy { it.displayName.lowercase() }

            allApps = appsNoIcons

            withContext(Dispatchers.Main) {
                loadBookmarks()
                if (isKeyboardVisible) {
                    filterApps(binding.filter.text?.toString() ?: "")
                }
            }

            // Phase 2: load icons in background
            val iconPackPkg = Prefs.getIconPack()
            val ctx = applicationContext
            val appsWithIcons = appsNoIcons.map { app ->
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
            .filter { it.packageName in bookmarked }
            .sortedBy { bookmarked.indexOf(it.packageName) }
            .take(calculateSpanCount() * 2)

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
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppOptions(app: AppInfo) {
        val isBookmarked = Prefs.isBookmarked(app.packageName)
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
        if (Prefs.isBookmarked(app.packageName)) {
            Prefs.removeBookmark(app.packageName)
        } else {
            Prefs.addBookmark(app.packageName)
        }
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
                Prefs.setAppPrefix(app.packageName, prefix)
                loadApps()
            }
            .setNegativeButton("Remove") { _, _ ->
                Prefs.setAppPrefix(app.packageName, "")
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
        loadApps()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isKeyboardVisible) {
            binding.filter.clearFocus()
            hideKeyboard()
        }
    }
}
