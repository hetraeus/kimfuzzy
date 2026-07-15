package com.example.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    }

    fun getTheme(): String = prefs.getString("theme", "light") ?: "light"
    fun setTheme(theme: String) = prefs.edit().putString("theme", theme).apply()

    fun getIconSize(): String = prefs.getString("icon_size", "default") ?: "default"
    fun setIconSize(size: String) = prefs.edit().putString("icon_size", size).apply()

    fun getIconPack(): String = prefs.getString("icon_pack", "") ?: ""
    fun setIconPack(pack: String) = prefs.edit().putString("icon_pack", pack).apply()

    fun getEditMode(): Boolean = prefs.getBoolean("edit_mode", false)
    fun setEditMode(enabled: Boolean) = prefs.edit().putBoolean("edit_mode", enabled).apply()

    // ── Background Image ───────────────────────────────────────────────
    fun getBackgroundImage(): String? = prefs.getString("background_image", null)
    fun setBackgroundImage(uri: String?) {
        if (uri != null) {
            prefs.edit().putString("background_image", uri).apply()
        } else {
            prefs.edit().remove("background_image").apply()
        }
    }

    fun getAppPrefix(packageName: String): String? {
        if (!prefs.contains("prefix_$packageName")) return null
        return prefs.getString("prefix_$packageName", "") ?: ""
    }

    fun setAppPrefix(packageName: String, prefix: String) =
        prefs.edit().putString("prefix_$packageName", prefix).apply()

    fun getCustomLabel(id: String): String? {
        if (!prefs.contains("label_$id")) {
            return null
        }
        return prefs.getString("label_$id", "") ?: ""
    }

    fun setCustomLabel(id: String, label: String) =
        prefs.edit().putString("label_$id", label).apply()

    fun getBookmarks(): List<String> {
        val str = prefs.getString("bookmarks_ordered", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",")
    }

    fun isBookmarked(packageName: String): Boolean = packageName in getBookmarks()

    fun addBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        if (!list.contains(packageName)) {
            val firstEmpty = list.indexOf("")
            if (firstEmpty != -1) {
                list[firstEmpty] = packageName
            } else {
                list.add(packageName)
            }
            saveBookmarks(list)
        }
    }

    fun removeBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        val index = list.indexOf(packageName)
        if (index != -1) {
            list[index] = ""
            saveBookmarks(list)
        }
    }

    fun saveBookmarks(list: List<String>) {
        prefs.edit().putString("bookmarks_ordered", list.joinToString(",")).apply()
    }

    fun getForgottenLinks(): Set<String> {
        val str = prefs.getString("forgotten_links", "") ?: ""
        return if (str.isEmpty()) emptySet() else str.split(",").toSet()
    }

    fun forgetLink(id: String) {
        val set = getForgottenLinks().toMutableSet()
        if (set.add(id)) {
            prefs.edit().putString("forgotten_links", set.joinToString(",")).apply()
        }
    }

    private const val LAST_LAUNCH_PREFIX = "last_launch_"

    fun getLastLaunchTime(appId: String): Long {
        return prefs.getLong(LAST_LAUNCH_PREFIX + appId, 0L)
    }

    fun setLastLaunchTime(appId: String, time: Long) {
        prefs.edit().putLong(LAST_LAUNCH_PREFIX + appId, time).apply()
    }

    // ── Export / Import ───────────────────────────────────────────────

    fun export(): String {
        val root = JSONObject()
        root.put("theme", getTheme())
        root.put("icon_size", getIconSize())
        root.put("icon_pack", getIconPack())
        root.put("background_image", getBackgroundImage() ?: "")

        val prefixes = JSONObject()
        val labels = JSONObject()
        for (key in prefs.all.keys) {
            when {
                key.startsWith("prefix_") -> {
                    val pkg = key.removePrefix("prefix_")
                    prefixes.put(pkg, prefs.getString(key, ""))
                }
                key.startsWith("label_") -> {
                    val id = key.removePrefix("label_")
                    labels.put(id, prefs.getString(key, ""))
                }
            }
        }
        root.put("prefixes", prefixes)
        root.put("labels", labels)
        return root.toString(2)
    }

    fun import(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val editor = prefs.edit()

            editor.putString("theme", root.optString("theme", "light"))
            editor.putString("icon_size", root.optString("icon_size", "default"))
            editor.putString("icon_pack", root.optString("icon_pack", ""))

            val bgImage = root.optString("background_image", "")
            if (bgImage.isNotEmpty()) {
                editor.putString("background_image", bgImage)
            } else {
                editor.remove("background_image")
            }

            for (key in prefs.all.keys) {
                if (key.startsWith("prefix_") || key.startsWith("label_")) {
                    editor.remove(key)
                }
            }

            val prefixes = root.optJSONObject("prefixes")
            if (prefixes != null) {
                val keys = prefixes.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    editor.putString("prefix_$pkg", prefixes.getString(pkg))
                }
            }

            val labels = root.optJSONObject("labels")
            if (labels != null) {
                val keys = labels.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    editor.putString("label_$id", labels.getString(id))
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
