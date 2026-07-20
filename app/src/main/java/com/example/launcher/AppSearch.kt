package com.example.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The search/filter app list: the RecyclerView itself, the search box, launching apps, and per-app options. */

internal fun MainActivity.setupAppList() {
    appAdapter = AppAdapter(
        onClick = { app -> launchApp(app) },
        onLongClick = { app -> showAppOptions(app) }
    )
    val ctx = this
    binding.appList.apply {
        layoutManager = LinearLayoutManager(ctx, RecyclerView.VERTICAL, true)
        adapter = appAdapter
    }
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

internal fun MainActivity.setupFilter() {
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
            binding.clearBtn.visibility = if (hasText) View.VISIBLE else View.GONE
            binding.playBtn.visibility = if (hasText) View.VISIBLE else View.GONE
            filterApps(query)
        }
    })

    binding.clearBtn.setOnClickListener {
        binding.filter.text?.clear()
    }

    binding.playBtn.setOnClickListener {
        val query = binding.filter.text?.toString() ?: ""
        if (query.isNotEmpty()) {
            val filteredApps = appAdapter.currentList
            if (filteredApps.isNotEmpty()) {
                launchApp(filteredApps[0])
            }
        }
    }

    // App stores lookup click handler
    binding.appStoresLookup.setOnClickListener {
        val query = binding.filter.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) {
            searchAppStores(query)
        }
    }
}

internal fun MainActivity.showFilter() {
    if (binding.filterContainer.visibility == View.VISIBLE) return
    binding.bookmarksGrid.visibility = View.GONE
    binding.appList.visibility = View.VISIBLE
    binding.filterContainer.visibility = View.VISIBLE
    binding.filter.requestFocus()
    showKeyboard()
    filterApps(binding.filter.text?.toString() ?: "")
    scrollToBottom()
}

private fun MainActivity.scrollToBottom() {
    binding.appList.post {
        if (appAdapter.itemCount > 0) {
            binding.appList.scrollToPosition(0)
        }
    }
}

internal fun MainActivity.filterApps(query: String) {
    val isFilteringByZzz = query.contains("zzz", ignoreCase = true)
    val visibleApps = if (isFilteringByZzz) {
        allApps
    } else {
        allApps.filterNot { it.prefix.equals("zzz", ignoreCase = true) }
    }

    if (query.isEmpty()) {
        appAdapter.submitList(visibleApps)
        binding.emptyState.visibility = View.GONE
        binding.appStoresLookup.visibility = View.GONE
        return
    }

    val scored = visibleApps.map { app ->
        val displayScore = FzfScorer.score(query, app.displayName)
        val labelScore = FzfScorer.score(query, app.label)
        val base = maxOf(displayScore, labelScore)
        val lastLaunch = Prefs.getLastLaunchTime(app.id)
        Triple(app, base, lastLaunch)
    }.filter { it.second > 0 }
    .sortedWith(
        compareByDescending<Triple<AppInfo, Int, Long>> { it.second }
            .thenByDescending { it.third }
    )
    .map { it.first }

    appAdapter.submitList(scored)

    val hasNoMatches = scored.isEmpty()
    binding.emptyState.visibility = if (hasNoMatches) View.VISIBLE else View.GONE
    binding.appStoresLookup.visibility = if (hasNoMatches && query.isNotEmpty()) View.VISIBLE else View.GONE
}

internal fun MainActivity.launchApp(app: AppInfo) {
    Prefs.setLastLaunchTime(app.id, System.currentTimeMillis())
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

private fun MainActivity.showAppOptions(app: AppInfo) {
    val isBookmarked = Prefs.isBookmarked(app.id)
    val isShortcut = app.shortcutId != null
    val options = buildList {
        add(if (isBookmarked) "Hide bookmark" else "Add bookmark")
        add("Edit suffix")
        if (isShortcut) {
            add("Forget link")
        } else {
            add("App info")
        }
    }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle(app.label)
        .setItems(options) { _, which ->
            when (options[which]) {
                "Hide bookmark", "Add bookmark" -> toggleBookmark(app)
                "Edit suffix" -> editPrefix(app)
                "App info" -> showAppInfo(app)
                "Forget link" -> forgetLink(app)
            }
        }
        .show()
}

private fun MainActivity.forgetLink(app: AppInfo) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Forget this link?")
        .setMessage("\"${app.label}\" will be removed from the search list. Any bookmark pointing to it will stop working, but you can remove that separately.")
        .setPositiveButton("Forget") { _, _ ->
            Prefs.forgetLink(app.id)
            Toast.makeText(this, "Forgot: ${app.label}", Toast.LENGTH_SHORT).show()
            loadApps()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.editPrefix(app: AppInfo) {
    val input = EditText(this).apply {
        setText(app.prefix)
        hint = "e.g., LLM AI"
        setTextColor(ThemeUtils.getTextColor())
        setHintTextColor(ThemeUtils.getSecondaryTextColor())
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Edit suffix for ${app.label}")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val suffix = input.text?.toString()?.trim() ?: ""
            Prefs.setAppPrefix(app.id, suffix)
            loadApps()
        }
        .setNegativeButton("Cancel", null)
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    dialog.show()
}

private fun MainActivity.showAppInfo(app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${app.packageName}")
    }
    startActivity(intent)
}

/** Opens both Google Play Store and F-Droid (if available) searching for the given query. */
private fun MainActivity.searchAppStores(query: String) {
    // Google Play Store
    val playIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://search?q=${Uri.encode(query)}")
    }
    val playWebIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}")
    }

    // F-Droid
    val fdroidIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://f-droid.org/packages/search?q=${Uri.encode(query)}")
    }

    var opened = false

    // Try Google Play app first
    if (playIntent.resolveActivity(packageManager) != null) {
        startActivity(playIntent)
        opened = true
    } else if (playWebIntent.resolveActivity(packageManager) != null) {
        startActivity(playWebIntent)
        opened = true
    }

    // Try F-Droid
    if (fdroidIntent.resolveActivity(packageManager) != null) {
        startActivity(fdroidIntent)
        opened = true
    }

    if (!opened) {
        Toast.makeText(this, "No app store found", Toast.LENGTH_SHORT).show()
    }
}
