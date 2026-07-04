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

    fun getAppPrefix(packageName: String): String? {
        val p = prefs.getString("prefix_$packageName", null)
        return if (p.isNullOrBlank()) null else p
    }

    fun setAppPrefix(packageName: String, prefix: String) =
        prefs.edit().putString("prefix_$packageName", prefix).apply()

    fun getBookmarks(): Set<String> = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()

    fun isBookmarked(packageName: String): Boolean = packageName in getBookmarks()

    fun addBookmark(packageName: String) {
        val set = getBookmarks().toMutableSet()
        set.add(packageName)
        prefs.edit().putStringSet("bookmarks", set).apply()
    }

    fun removeBookmark(packageName: String) {
        val set = getBookmarks().toMutableSet()
        set.remove(packageName)
        prefs.edit().putStringSet("bookmarks", set).apply()
    }
}
