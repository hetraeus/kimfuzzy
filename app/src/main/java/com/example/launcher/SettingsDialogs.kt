package com.example.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Full-screen settings view reachable from the settings (𑁍) button. */

internal fun MainActivity.setupSettings() {
    binding.settingsBtn.setOnClickListener {
        if (binding.settingsView.visibility == View.VISIBLE) {
            resetToBookmarks()
        } else {
            showSettingsView()
        }
    }

    binding.closeSettingsBtn.setOnClickListener {
        resetToBookmarks()
    }
}

internal fun MainActivity.showSettingsView() {
    binding.blackCurtain.visibility = View.GONE
    binding.bookmarksGrid.visibility = View.GONE
    binding.filterContainer.visibility = View.GONE
    binding.appList.visibility = View.GONE
    binding.settingsView.visibility = View.VISIBLE

    buildSettingsOptions()
    applySettingsThemeColors()
}

private fun MainActivity.applySettingsThemeColors() {
    val bg = ThemeUtils.getBackgroundColor()
    val text = ThemeUtils.getTextColor()

    binding.settingsView.setBackgroundColor(bg)
    binding.settingsTitle.setTextColor(text)
    binding.closeSettingsBtn.setTextColor(text)
}

private fun MainActivity.buildSettingsOptions() {
    val container = binding.settingsOptionsContainer
    container.removeAllViews()

    val editMode = Prefs.getEditMode()
    val curtainOn = Prefs.getBlackCurtain()

    val options = listOf(
        "Overview" to { showOverview() },
        "Theme" to { showThemePicker() },
        "Icon Size" to { showIconSizePicker() },
        "Icon Pack" to { showIconPackPicker() },
        "Background Image" to { showBackgroundImagePicker() },
        (if (editMode) "💮 Lock bookmarks" else "✏️ Edit bookmarks") to { toggleEditMode() },
        (if (curtainOn) "⚫ Disable Black Curtain" else "⚫ Enable Black Curtain") to { toggleBlackCurtain() },
        "Export settings" to { exportSettings() },
        "Import settings" to { importSettings() },
        "Set as Default Launcher" to { promptSetDefaultLauncher() }
    )

    val textColor = ThemeUtils.getTextColor()
    val accent = ThemeUtils.getAccentColor(this)

    for ((label, action) in options) {
        val item = TextView(this).apply {
            text = label
            setTextColor(if (label.startsWith("Set as")) accent else textColor)
            textSize = 16f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                action()
                // Rebuild options in case state changed (e.g., edit mode toggled)
                if (label.startsWith("💮") || label.startsWith("✏️") ||
                    label.startsWith("⚫")) {
                    buildSettingsOptions()
                }
            }
        }
        container.addView(item)
    }
}

private fun MainActivity.toggleEditMode() {
    val editMode = Prefs.getEditMode()
    Prefs.setEditMode(!editMode)
    updateEditModeIcon()
    binding.dropZone.visibility = if (Prefs.getEditMode()) View.VISIBLE else View.GONE
    bookmarkAdapter = BookmarkAdapter(
        onRename = { app -> renameBookmark(app) },
        dragListener = if (Prefs.getEditMode()) createBookmarkDragListener() else null
    )
    binding.bookmarksGrid.adapter = bookmarkAdapter
    loadBookmarks()
    Toast.makeText(
        this,
        if (!editMode) "✏️ Edit mode enabled" else "💮 Locked mode enabled",
        Toast.LENGTH_SHORT
    ).show()
}

private fun MainActivity.toggleBlackCurtain() {
    val curtainOn = Prefs.getBlackCurtain()
    Prefs.setBlackCurtain(!curtainOn)
    applyBlackCurtainState()
    Toast.makeText(
        this,
        if (!curtainOn) "⚫ Black Curtain enabled" else "☀️ Black Curtain disabled",
        Toast.LENGTH_SHORT
    ).show()
}

private fun MainActivity.showBackgroundImagePicker() {
    val currentUri = Prefs.getBackgroundImage()
    val options = if (currentUri != null) {
        arrayOf("Choose image", "Remove background")
    } else {
        arrayOf("Choose image")
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("Background Image")
        .setItems(options) { _, which ->
            when (options[which]) {
                "Choose image" -> pickImageLauncher.launch(arrayOf("image/*"))
                "Remove background" -> {
                    Prefs.setBackgroundImage(null)
                    binding.backgroundImage.setImageDrawable(null)
                    Toast.makeText(this, "Background removed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.exportSettings() {
    val json = Prefs.export()
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("launcher_settings", json)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Settings copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun MainActivity.importSettings() {
    val input = EditText(this).apply {
        hint = "Paste settings JSON here..."
        setTextColor(ThemeUtils.getTextColor())
        setHintTextColor(ThemeUtils.getSecondaryTextColor())
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        minLines = 4
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("Import settings")
        .setView(input)
        .setPositiveButton("Import") { _, _ ->
            val json = input.text?.toString()?.trim() ?: ""
            if (json.isNotEmpty()) {
                if (Prefs.import(json)) {
                    Toast.makeText(this, "Settings imported. Restarting...", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, "Invalid settings JSON", Toast.LENGTH_SHORT).show()
                }
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.showOverview() {
    val textColor = ThemeUtils.getTextColor()
    val secondaryText = ThemeUtils.getSecondaryTextColor()

    val scrollView = android.widget.ScrollView(this).apply {
        setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
    }

    val content = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
    }
    scrollView.addView(content)

    val sections = listOf(
        "Bookmarks" to "Swipe up on the bottom grid to open search. Long-press any bookmark to rename, annotate, or hide it. Tap ✏️ Edit bookmarks to drag and reorder.",
        "Search" to "Type to filter apps. The best match is shown first, boosted by how recently you opened it. Tap ▶ to launch the top result. Tap the empty-state link to search app stores.",
        "Hide apps" to "Type 'zzz' in the search box to reveal every app, including hidden ones. To hide an app, long-press it in search and pick Edit suffix, then set it to 'zzz'.",
        "Annotations" to "Long-press any app (bookmark or search result) and tap Annotate to leave a note for yourself — why you installed it, what it does, etc.",
        "Black Curtain" to "Enable it in settings for distraction-free viewing. Swipe up to exit. A dim settings button stays reachable.",
        "Wallpaper" to "Pick any image as your background. It shows edge-to-edge, behind the status bar, top bar, and navigation bar.",
        "Icon packs" to "Install any standard icon pack from the Play Store or F-Droid and apply it here. The launcher auto-detects supported packs.",
        "Backup" to "Export copies your layout, labels, annotations, and preferences as JSON to the clipboard. Import pastes it back. No cloud needed."
    )

    for ((title, body) in sections) {
        val titleView = android.widget.TextView(this).apply {
            text = title
            setTextColor(textColor)
            textSize = 16f
            setPadding(0, dpToPx(12), 0, dpToPx(4))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        content.addView(titleView)

        val bodyView = android.widget.TextView(this).apply {
            text = body
            setTextColor(secondaryText)
            textSize = 14f
            setPadding(0, 0, 0, dpToPx(8))
            setTextIsSelectable(true)
        }
        content.addView(bodyView)
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("Overview")
        .setView(scrollView)
        .setPositiveButton("Got it", null)
        .show()
}

private fun MainActivity.showThemePicker() {
    val themes = arrayOf("Light", "Dark", "Sepia")
    val current = Prefs.getTheme()
    val currentIndex = themes.indexOfFirst { it.lowercase() == current }

    MaterialAlertDialogBuilder(this)
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

private fun MainActivity.showIconSizePicker() {
    val sizes = arrayOf("Default", "Small", "Rounded")
    val current = Prefs.getIconSize()
    val currentIndex = sizes.indexOfFirst { it.lowercase() == current }

    MaterialAlertDialogBuilder(this)
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

private fun MainActivity.showIconPackPicker() {
    val packs = IconPack.discover(this)
    val current = Prefs.getIconPack()
    val currentIndex = packs.indexOfFirst { it.first == current }.coerceAtLeast(0)

    val labels = packs.map { it.second }.toTypedArray()

    MaterialAlertDialogBuilder(this)
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

private fun MainActivity.promptSetDefaultLauncher() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Set as default launcher?")
        .setMessage("This app needs to be your default launcher.")
        .setPositiveButton("Set") { _, _ ->
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        .setNegativeButton("Later", null)
        .show()
}
