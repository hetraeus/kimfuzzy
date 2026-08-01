package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.toColorInt

object ThemeUtils {
    fun applyTheme(context: Context) {
        val theme = Prefs.getTheme()
        val themeId = when (theme) {
            "dark" -> R.style.Theme_Launcher_Dark
            else -> R.style.Theme_Launcher_Light
        }
        context.setTheme(themeId)
    }

    fun getBackgroundColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> Color.BLACK
            else -> "#F4ECD8".toColorInt()
        }
    }

    fun getTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> "#EEEEEE".toColorInt()
            else -> "#3E2B1F".toColorInt()
        }
    }

    fun getSecondaryTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> "#BDBDBD".toColorInt()
            else -> "#5B4636".toColorInt()
        }
    }

    fun getDimmedTextColor(): Int {
        return when (Prefs.getTheme()) {
            "dark" -> "#616161".toColorInt()
            else -> "#8B7355".toColorInt()
        }
    }

    fun getAccentColor(context: Context): Int {
        val attrs = intArrayOf(android.R.attr.colorPrimary)
        val ta = context.obtainStyledAttributes(attrs)
        val color = ta.getColor(0, "#FF4081".toColorInt())
        ta.recycle()
        return color
    }
}
