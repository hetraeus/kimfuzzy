package com.example.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val prefix: String,
    val displayName: String,
    val icon: Drawable?
)
