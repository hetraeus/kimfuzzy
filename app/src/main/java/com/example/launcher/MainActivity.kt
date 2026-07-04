package com.example.launcher

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appAdapter: AppAdapter
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm - d MMMM", Locale.getDefault())
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

        if (!isDefaultLauncher()) {
            promptSetDefaultLauncher()
        }

        loadApps()
    }

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun applyThemeColors() {
        val bg = ThemeUtils.getBackgroundColor(this)
        val text = ThemeUtils.getTextColor(this)
        val textSecondary = ThemeUtils.getSecondaryTextColor(this)
        val accent = Prefs.getAccentColor()

        binding.root.setBackgroundColor(bg)
        binding.topBar.setBackgroundColor(bg)

        binding.dateTime.setTextColor(text)
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
    }

    private fun updateDateTime() {
        binding.dateTime.text = dateFormat.format(Date())
    }

    private fun updateAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock
        binding.nextAlarm.text = if (nextAlarm != null) {
            val time = DateFormat.getTimeFormat(this).format(Date(nextAlarm.triggerTime))
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
            layoutManager = LinearLayoutManager(this@MainActivity)
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

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolves = packageManager.queryIntentActivities(intent, 0)

            val apps = resolves.map { resolve ->
                val label = resolve.loadLabel(packageManager).toString()
                val pkg = resolve.activityInfo.packageName
                val activity = resolve.activityInfo.name
                val prefix = Prefs.getAppPrefix(pkg) ?: ""
                val display = if (prefix.isNotEmpty()) "$prefix - $label" else label

                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = activity,
                    prefix = prefix,
                    displayName = display,
                    icon = resolve.loadIcon(packageManager)
                )
            }.sortedBy { it.displayName.lowercase() }

            allApps = apps

            withContext(Dispatchers.Main) {
                loadBookmarks()
                if (isKeyboardVisible) {
                    filterApps(binding.filter.text?.toString() ?: "")
                }
            }
        }
    }

    private fun loadBookmarks() {
        val bookmarked = Prefs.getBookmarks()
        val bookmarks = allApps
            .filter { it.packageName in bookmarked }
            .take(calculateSpanCount() * 2)
        bookmarkAdapter.submitList(bookmarks)
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
            .setPositiveButton("Accent Color") { _, _ ->
                showAccentPicker()
            }
            .setNegativeButton("Close", null)
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

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, 0)
        return info?.activityInfo?.packageName == packageName
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
