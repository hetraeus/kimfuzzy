package io.github.hetraeus.kimfuzzy

import android.graphics.drawable.Drawable

data class AppInfo(
    val id: String,
    val label: String,
    val packageName: String,
    val activityName: String,
    val prefix: String,
    val displayName: String,
    val icon: Drawable?,
    val iconFromPack: Boolean = false,
    val shortcutId: String? = null
)
