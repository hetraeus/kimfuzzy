package com.example.launcher

import android.content.Context
import android.graphics.Color

object ThemeUtils {
    fun applyTheme(context: Context) {
        val theme = Prefs.getTheme()
        val themeId = when (theme) {
            "light" -> R.style.Theme_Launcher_Light
            "dark" -> R.style.Theme_Launcher_Dark
            "sepia" -> R.style.Theme_Launcher_Sepia
            else -> R.style.Theme_Launcher_Light
        }
        context.setTheme(themeId)
    }

    fun getBackgroundColor(): Int {
        return when (Prefs.getTheme()) {
            "light" -> Color.parseColor("#FAFAFA")
            "dark" -> Color.BLACK
            "sepia" -> Color.parseColor("#F4ECD8")
            else -> Color.parseColor("#FAFAFA")
        }
    }

    fun getTextColor(): Int {
        return when (Prefs.getTheme()) {
            "light" -> Color.parseColor("#212121")
            "dark" -> Color.parseColor("#EEEEEE")
            "sepia" -> Color.parseColor("#3E2B1F")
            else -> Color.parseColor("#212121")
        }
    }

    fun getSecondaryTextColor(): Int {
        return when (Prefs.getTheme()) {
            "light" -> Color.parseColor("#757575")
            "dark" -> Color.parseColor("#BDBDBD")
            "sepia" -> Color.parseColor("#5B4636")
            else -> Color.parseColor("#757575")
        }
    }
    fun getAccentColor(context: Context): Int {
        val attrs = intArrayOf(android.R.attr.colorPrimary)
        val ta = context.obtainStyledAttributes(attrs)
        val color = ta.getColor(0, Color.parseColor("#FF4081"))
        ta.recycle()
        return color
    }
}
