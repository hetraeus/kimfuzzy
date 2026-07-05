package com.example.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    }

    fun getTheme(): String = prefs.getString("theme", "light") ?: "light"
    fun setTheme(theme: String) = prefs.edit().putString("theme", theme).apply()

    fun getAccentColor(): Int = prefs.getInt("accent", Color.parseColor("#FF4081"))
    fun setAccentColor(color: Int) = prefs.edit().putInt("accent", color).apply()

    fun getIconSize(): String = prefs.getString("icon_size", "default") ?: "default"
    fun setIconSize(size: String) = prefs.edit().putString("icon_size", size).apply()

    fun getIconPack(): String = prefs.getString("icon_pack", "") ?: ""
    fun setIconPack(pack: String) = prefs.edit().putString("icon_pack", pack).apply()

    fun getAppPrefix(packageName: String): String? {
        val p = prefs.getString("prefix_$packageName", null)
        return if (p.isNullOrBlank()) null else p
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
        return if (str.isEmpty()) emptyList() else str.split(",").filter { it.isNotEmpty() }
    }

    fun isBookmarked(packageName: String): Boolean = packageName in getBookmarks()

    fun addBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        if (!list.contains(packageName)) {
            list.add(packageName)
            saveBookmarks(list)
        }
    }

    fun removeBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        list.remove(packageName)
        saveBookmarks(list)
    }

    fun saveBookmarks(list: List<String>) {
        prefs.edit().putString("bookmarks_ordered", list.joinToString(",")).apply()
    }
}
