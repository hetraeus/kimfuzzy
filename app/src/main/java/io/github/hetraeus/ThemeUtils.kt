package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.graphics.Color

object ThemeUtils {
    fun applyTheme(context: Context) {
        val theme = Prefs.getTheme()
        val themeId = when (theme) {
            "dark" -> R.style.Theme_Launcher_Dark
            // "light" now uses what used to be the separate sepia palette.
            else -> R.style.Theme_Launcher_Light
        }
        context.setTheme(themeId)
    }

    fun getBackgroundColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> Color.BLACK
            else -> Color.parseColor("#F4ECD8")
        }
    }

    fun getTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> Color.parseColor("#EEEEEE")
            else -> Color.parseColor("#3E2B1F")
        }
    }

    fun getSecondaryTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> Color.parseColor("#BDBDBD")
            else -> Color.parseColor("#5B4636")
        }
    }

    fun getDimmedTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> Color.parseColor("#616161")
            else -> Color.parseColor("#8B7355")
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
