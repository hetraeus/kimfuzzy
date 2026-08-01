package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
    binding.emptyState.visibility = View.GONE
    binding.appStoresLookup.visibility = View.GONE
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

    val textColor = ThemeUtils.getTextColor()
    val accent = ThemeUtils.getAccentColor(this)

    fun sectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            setTextColor(accent)
            textSize = 12f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(4))
            setAllCaps(true)
        }
        container.addView(header)
    }

    fun optionItem(label: String, onClick: () -> Unit, rebuild: Boolean = false) {
        val item = TextView(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 16f
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
                if (rebuild) buildSettingsOptions()
            }
        }
        container.addView(item)
    }

    val editMode = Prefs.getEditMode()
    val curtainOn = Prefs.getBlackCurtain()

    sectionHeader("Appearance")
    optionItem(if (curtainOn) "⚫ Disable Black Curtain" else "⚫ Enable Black Curtain", { toggleBlackCurtain() }, true)
    optionItem("Theme", { showThemePicker() })
    optionItem("Icon Size", { showIconSizePicker() })
    optionItem("Icon Pack", { showIconPackPicker() })
    optionItem("Background Image", { showBackgroundImagePicker() })
    optionItem(if (editMode) "💮 Lock bookmarks" else "✏️ Edit bookmarks", { toggleEditMode() }, true)

    sectionHeader("Setup")
    optionItem("Export settings", { exportSettings() })
    optionItem("Import settings", { importSettings() })
    optionItem("Set as Default Launcher", { promptSetDefaultLauncher() })
    optionItem("About", { showAboutDialog() })
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
    val bucket = Prefs.currentBackgroundBucket()
    val bucketLabel = if (bucket == "dark") "Dark" else "Light"
    val isSet = Prefs.getBackgroundImage(bucket) != null

    val items = mutableListOf<Pair<String, () -> Unit>>()

    items.add((if (isSet) "Change wallpaper" else "Set wallpaper") to {
        pendingBackgroundBucket = bucket
        pickImageLauncher.launch(arrayOf("image/*"))
    })
    if (isSet) {
        items.add("Remove wallpaper" to {
            Prefs.setBackgroundImage(bucket, null)
            applyBackgroundImage()
            Toast.makeText(this, "$bucketLabel theme wallpaper removed", Toast.LENGTH_SHORT).show()
        })
    }

    val labels = items.map { it.first }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle("Background Image ($bucketLabel theme)")
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
    val themes = arrayOf("Light", "Dark")
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
    val bucket = Prefs.currentBackgroundBucket()
    val bucketLabel = if (bucket == "dark") "Dark" else "Light"
    val packs = IconPack.discover(this)
    val current = Prefs.getIconPack(bucket)
    val currentIndex = packs.indexOfFirst { it.first == current }.coerceAtLeast(0)

    val labels = packs.map { it.second }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle("Icon Pack ($bucketLabel theme)")
        .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
            val selected = packs[which].first
            if (selected != current) {
                IconPack.clearCache()
                Prefs.setIconPack(bucket, selected)
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

private fun MainActivity.showAboutDialog() {
    val textColor = ThemeUtils.getTextColor()
    val accent = ThemeUtils.getAccentColor(this)
    val secondaryText = ThemeUtils.getSecondaryTextColor()

    val contentView = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8))
    }

    val titleView = TextView(this).apply {
        text = getString(R.string.about_title)
        setTextColor(textColor)
        textSize = 22f
        setPadding(0, 0, 0, dpToPx(4))
    }
    contentView.addView(titleView)

    val devView = TextView(this).apply {
        text = getString(R.string.about_developer_format, "hetraeus")
        setTextColor(secondaryText)
        textSize = 14f
        setPadding(0, 0, 0, dpToPx(16))
    }
    contentView.addView(devView)

    val divider = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        ).apply { setMargins(0, 0, 0, dpToPx(8)) }
        setBackgroundColor(secondaryText)
    }
    contentView.addView(divider)

    val githubLink = TextView(this).apply {
        text = getString(R.string.about_github)
        setTextColor(accent)
        textSize = 16f
        setPadding(0, dpToPx(12), 0, dpToPx(12))
        setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hetraeus/kimfuzzy"))
            startActivity(intent)
        }
    }
    contentView.addView(githubLink)

    val licenseLink = TextView(this).apply {
        text = getString(R.string.about_license_format, "LGPL v3")
        setTextColor(accent)
        textSize = 16f
        setPadding(0, dpToPx(12), 0, dpToPx(12))
        setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/lgpl-3.0.html"))
            startActivity(intent)
        }
    }
    contentView.addView(licenseLink)

    MaterialAlertDialogBuilder(this)
        .setView(contentView as android.view.View)
        .setPositiveButton("Close", null)
        .show()
}
