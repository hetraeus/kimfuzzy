package io.github.hetraeus.kimfuzzy

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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
            binding.clearBtn.isVisible = hasText
            binding.playBtn.isVisible = hasText
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

    binding.appStoresLookup.setOnClickListener {
        val query = binding.filter.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) {
            searchAppStores(query)
        }
    }
}

internal fun MainActivity.showFilter() {
    if (binding.filterContainer.isVisible) return
    binding.bookmarksGrid.isVisible = false
    binding.appList.isVisible = true
    binding.filterContainer.isVisible = true
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

    val scored = if (query.isEmpty()) {
        visibleApps.map { app ->
            val score = if (Prefs.isPinned(app.id)) 1 else 0
            val lastLaunch = Prefs.getLastLaunchTime(app.id)
            Triple(app, score, lastLaunch)
        }.sortedWith(
            compareByDescending<Triple<AppInfo, Int, Long>> { it.second }
                .thenByDescending { it.third }
        )
    } else {
        visibleApps.map { app ->
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
    }.map { it.first }

    appAdapter.submitList(scored)

    val inFilterView = binding.filterContainer.isVisible
    val hasNoMatches = scored.isEmpty()
    binding.emptyState.isVisible = inFilterView && hasNoMatches
    binding.appStoresLookup.isVisible = inFilterView && hasNoMatches && query.isNotEmpty()
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

    val textColor = ThemeUtils.getTextColor()
    val accent = ThemeUtils.getAccentColor(this)
    val secondaryText = ThemeUtils.getSecondaryTextColor()

    val contentView = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8))
    }

    val nameView = TextView(this).apply {
        text = app.label
        setTextColor(textColor)
        textSize = 20f
        setPadding(0, 0, 0, dpToPx(12))
    }
    contentView.addView(nameView)

    val annotationContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    contentView.addView(annotationContainer)

    lateinit var dialogRef: androidx.appcompat.app.AlertDialog
    fun buildAnnotationSection() {
        annotationContainer.removeAllViews()
        val currentAnnotation = Prefs.getAppAnnotation(app.id)

        if (currentAnnotation != null) {
            val annotationView = TextView(this).apply {
                text = currentAnnotation
                setTextColor(secondaryText)
                textSize = 14f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dpToPx(12))
                setOnClickListener {
                    dialogRef.dismiss()
                    showAnnotationEditDialog(app)
                }
            }
            annotationContainer.addView(annotationView)
        } else {
            val annotatePrompt = TextView(this).apply {
                text = getString(R.string.annotate_app)
                setTextColor(accent)
                textSize = 14f
                setPadding(0, 0, 0, dpToPx(12))
                setOnClickListener {
                    dialogRef.dismiss()
                    showAnnotationEditDialog(app)
                }
            }
            annotationContainer.addView(annotatePrompt)
        }
    }

    buildAnnotationSection()

    val divider = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        ).apply { setMargins(0, 0, 0, dpToPx(8)) }
        setBackgroundColor(secondaryText)
    }
    contentView.addView(divider)

    val actionsContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    contentView.addView(actionsContainer)

    val bookmarkAction = if (isBookmarked) "Hide bookmark" else "Add bookmark"
    val pinAction = if (Prefs.isPinned(app.id)) "Unpin from top" else "Pin to top"
    val actions = listOf(
        pinAction to { Prefs.togglePin(app.id); Toast.makeText(this, if (Prefs.isPinned(app.id)) "Pinned" else "Unpinned", Toast.LENGTH_SHORT).show() },
        bookmarkAction to { toggleBookmark(app) },
        "Edit suffix" to { editPrefix(app) },
        if (isShortcut) "Forget link" to { forgetLink(app) } else "App info" to { showAppInfo(app) }
    )

    for ((label, action) in actions) {
        val btn = TextView(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 16f
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            setOnClickListener {
                dialogRef.dismiss()
                action()
            }
        }
        actionsContainer.addView(btn)
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setView(contentView as android.view.View)
        .create()
    dialogRef = dialog

    dialog.show()
}

private fun MainActivity.showAnnotationEditDialog(app: AppInfo) {
    val input = EditText(this).apply {
        setText(Prefs.getAppAnnotation(app.id) ?: "")
        setTextColor(ThemeUtils.getTextColor())
        setHintTextColor(ThemeUtils.getSecondaryTextColor())
        hint = "Why did you install this app?"
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        minLines = 1
        maxLines = 3
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(app.label)
        .setView(input)
        .setPositiveButton(getString(R.string.action_save)) { _, _ ->
            val text = input.text?.toString()?.trim() ?: ""
            Prefs.setAppAnnotation(app.id, text)
        }
        .setNegativeButton(getString(R.string.action_cancel), null)
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    dialog.show()
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
        .setNegativeButton(getString(R.string.action_cancel), null)
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
        .setPositiveButton(getString(R.string.action_save)) { _, _ ->
            val suffix = input.text?.toString()?.trim() ?: ""
            Prefs.setAppPrefix(app.id, suffix)
            loadApps()
        }
        .setNegativeButton(getString(R.string.action_cancel), null)
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    dialog.show()
}

private fun MainActivity.showAppInfo(app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${app.packageName}".toUri()
    }
    startActivity(intent)
}

private fun MainActivity.searchAppStores(query: String) {
    val playIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "market://search?q=${Uri.encode(query)}".toUri()
    }
    val playWebIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "https://play.google.com/store/search?q=${Uri.encode(query)}".toUri()
    }

    val fdroidIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "https://f-droid.org/packages/search?q=${Uri.encode(query)}".toUri()
    }

    var opened = false

    if (playIntent.resolveActivity(packageManager) != null) {
        startActivity(playIntent)
        opened = true
    } else if (playWebIntent.resolveActivity(packageManager) != null) {
        startActivity(playWebIntent)
        opened = true
    }

    if (fdroidIntent.resolveActivity(packageManager) != null) {
        startActivity(fdroidIntent)
        opened = true
    }

    if (!opened) {
        Toast.makeText(this, "No app store found", Toast.LENGTH_SHORT).show()
    }
}
