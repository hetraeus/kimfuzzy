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
    hideKeyboard()
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
        (if (curtainOn) "⚫ Disable Black Curtain" else "⚫ Enable Black Curtain") to { toggleBlackCurtain() },
        "Theme" to { showThemePicker() },
        "Icon Size" to { showIconSizePicker() },
        "Icon Pack" to { showIconPackPicker() },
        "Background Image" to { showBackgroundImagePicker() },
        (if (editMode) "💮 Lock bookmarks" else "✏️ Edit bookmarks") to { toggleEditMode() },
        "Export settings" to { exportSettings() },
        "Import settings" to { importSettings() },
        "Set as Default Launcher" to { promptSetDefaultLauncher() }
    )

    val textColor = ThemeUtils.getTextColor()

    for ((label, action) in options) {
        val item = TextView(this).apply {
            text = label
            setTextColor(textColor)
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
    val darkSet = Prefs.getBackgroundImage("dark") != null
    val lightSet = Prefs.getBackgroundImage("light") != null

    val items = mutableListOf<Pair<String, () -> Unit>>()

    items.add((if (darkSet) "Change dark theme wallpaper" else "Set dark theme wallpaper") to {
        pendingBackgroundBucket = "dark"
        pickImageLauncher.launch(arrayOf("image/*"))
    })
    if (darkSet) {
        items.add("Remove dark theme wallpaper" to {
            Prefs.setBackgroundImage("dark", null)
            applyBackgroundImage()
            Toast.makeText(this, "Dark theme wallpaper removed", Toast.LENGTH_SHORT).show()
        })
    }

    items.add((if (lightSet) "Change light/sepia theme wallpaper" else "Set light/sepia theme wallpaper") to {
        pendingBackgroundBucket = "light"
        pickImageLauncher.launch(arrayOf("image/*"))
    })
    if (lightSet) {
        items.add("Remove light/sepia theme wallpaper" to {
            Prefs.setBackgroundImage("light", null)
            applyBackgroundImage()
            Toast.makeText(this, "Light/Sepia theme wallpaper removed", Toast.LENGTH_SHORT).show()
        })
    }

    val labels = items.map { it.first }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle("Background Image")
        .setItems(labels) { _, which -> items[which].second() }
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
