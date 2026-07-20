package com.example.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Scans installed apps + pinned shortcuts via PackageManager/LauncherApps, resolves icons, and caches the result. */

internal fun MainActivity.loadApps() {
    lifecycleScope.launch(Dispatchers.IO) {
        // Fast path: on a cold start (e.g. the launcher's process was
        // killed in the background and this Activity is being recreated
        // from scratch), paint immediately from the last cached snapshot
        // instead of leaving the screen blank/stale until a full
        // PackageManager scan + icon resolution finishes below.
        if (allApps.isEmpty()) {
            val cached = AppCache.loadCachedApps(applicationContext)
            if (cached.isNotEmpty()) {
                allApps = cached
                withContext(Dispatchers.Main) {
                    loadBookmarks()
                    if (isKeyboardVisible) {
                        filterApps(binding.filter.text?.toString() ?: "")
                    }
                }
            }
        }

        val oldAppsMap = allApps.associateBy { it.id }

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = packageManager.queryIntentActivities(intent, 0)
        val resolveMap = resolves.associateBy { it.activityInfo.packageName }

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val forgottenLinks = Prefs.getForgottenLinks()
        val shortcuts = if (launcherApps.hasShortcutHostPermission()) {
            try {
                val query = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                }
                (launcherApps.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList())
                    .filterNot { "shortcut:${it.`package`}:${it.id}" in forgottenLinks }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val shortcutMap = shortcuts.associateBy { "shortcut:${it.`package`}:${it.id}" }

        val appsNoIcons = resolves.map { resolve ->
            val pkg = resolve.activityInfo.packageName
            val label = resolve.loadLabel(packageManager).toString()
            val activity = resolve.activityInfo.name

            val appInfo = try {
                packageManager.getApplicationInfo(pkg, 0)
            } catch (e: Exception) { null }

            val autoSuffix = getCategorySuffix(appInfo)
            val userSuffix = Prefs.getAppPrefix(pkg)  // Keep using same pref key for backward compat
            val suffix = userSuffix ?: autoSuffix

            // Suffix format: "Label - Suffix" instead of "Prefix - Label"
            val display = if (suffix.isNotEmpty()) "$label - $suffix" else label

            AppInfo(
                id = pkg,
                label = label,
                packageName = pkg,
                activityName = activity,
                prefix = suffix,  // Keep field name for backward compatibility
                displayName = display,
                icon = null
            )
        }

        val shortcutsNoIcons = shortcuts.map { shortcut ->
            val pkg = shortcut.`package`
            val shortcutId = shortcut.id
            val id = "shortcut:$pkg:$shortcutId"
            val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "Shortcut").toString()

            val autoSuffix = "Shortcut"
            val userSuffix = Prefs.getAppPrefix(id)
            val suffix = userSuffix ?: autoSuffix

            // Suffix format: "Label - Suffix" instead of "Prefix - Label"
            val display = if (suffix.isNotEmpty()) "$label - $suffix" else label

            AppInfo(
                id = id,
                label = label,
                packageName = pkg,
                activityName = "",
                prefix = suffix,
                displayName = display,
                icon = null,
                shortcutId = shortcutId
            )
        }

        val allAppsNoIcons = (appsNoIcons + shortcutsNoIcons)
            .sortedBy { it.displayName.lowercase() }
            .map { app ->
                oldAppsMap[app.id]?.let { old ->
                    if (old.icon != null) app.copy(icon = old.icon, iconFromPack = old.iconFromPack) else app
                } ?: app
            }

        allApps = allAppsNoIcons

        withContext(Dispatchers.Main) {
            loadBookmarks()
            if (isKeyboardVisible) {
                filterApps(binding.filter.text?.toString() ?: "")
            }
        }

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
        AppCache.saveApps(applicationContext, appsWithIcons)

        withContext(Dispatchers.Main) {
            loadBookmarks()
            if (isKeyboardVisible) {
                filterApps(binding.filter.text?.toString() ?: "")
            }
        }
    }
}

private fun MainActivity.getCategorySuffix(appInfo: ApplicationInfo?): String {
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
