package com.example.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Everything reachable from the settings (⚙) button. */

internal fun MainActivity.setupSettings() {
    binding.settingsBtn.setOnClickListener {
        showSettingsDialog()
    }
}

internal fun MainActivity.showSettingsDialog() {
    val editMode = Prefs.getEditMode()
    val editLabel = if (editMode) "💮 Lock bookmarks" else "✏️ Edit bookmarks"
    val curtainOn = Prefs.getBlackCurtain()
    val curtainLabel = if (curtainOn) "⚫ Disable Black Curtain" else "⚫ Enable Black Curtain"
    val options = arrayOf("Theme", "Icon Size", "Icon Pack", "Background Image", editLabel, curtainLabel, "Export settings", "Import settings", "Set as Default Launcher")

    MaterialAlertDialogBuilder(this)
        .setTitle("Settings")
        .setItems(options) { _, which ->
            when (which) {
                0 -> showThemePicker()
                1 -> showIconSizePicker()
                2 -> showIconPackPicker()
                3 -> showBackgroundImagePicker()
                4 -> {
                    Prefs.setEditMode(!editMode)
                    updateEditModeIcon()
                    binding.dropZone.visibility = if (Prefs.getEditMode()) View.VISIBLE else View.GONE
                    bookmarkAdapter = BookmarkAdapter(
                        onRename = { app -> renameBookmark(app) },
                        dragListener = if (Prefs.getEditMode()) bookmarkDragListener else null
                    )
                    binding.bookmarksGrid.adapter = bookmarkAdapter
                    loadBookmarks()
                    Toast.makeText(
                        this,
                        if (!editMode) "✏️ Edit mode enabled" else "💮 Locked mode enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                5 -> {
                    Prefs.setBlackCurtain(!curtainOn)
                    applyBlackCurtainState()
                    Toast.makeText(
                        this,
                        if (!curtainOn) "⚫ Black Curtain enabled" else "☀️ Black Curtain disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                6 -> exportSettings()
                7 -> importSettings()
                8 -> promptSetDefaultLauncher()
            }
        }
        .setNegativeButton("Close", null)
        .show()
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
