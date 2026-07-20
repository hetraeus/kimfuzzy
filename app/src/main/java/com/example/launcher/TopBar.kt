package com.example.launcher

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Date

/** Window flags, theme colors, background image, and the date/alarm top bar. */

internal fun MainActivity.setupWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
}

internal fun MainActivity.applyThemeColors() {
    val bg = ThemeUtils.getBackgroundColor()
    val text = ThemeUtils.getTextColor()
    val textSecondary = ThemeUtils.getSecondaryTextColor()
    val accent = ThemeUtils.getAccentColor(this)

    binding.root.setBackgroundColor(bg)
    // Top bar is transparent so the wallpaper shows through behind it
    binding.topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)

    binding.dateText.setTextColor(text)
    // Alarm text made darker using a more muted/darker secondary color
    binding.nextAlarm.setTextColor(ThemeUtils.getDimmedTextColor())
    binding.emptyState.setTextColor(textSecondary)

    binding.filter.setTextColor(text)
    binding.filter.setHintTextColor(textSecondary)

    binding.settingsBtn.setTextColor(accent)
    binding.calendarBtn.setTextColor(accent)
    binding.clearBtn.setColorFilter(accent)
    binding.playBtn.setTextColor(accent)

    binding.dropZone.setTextColor(textSecondary)
    binding.dropZone.setBackgroundColor(bg)
    binding.dropZone.visibility = if (Prefs.getEditMode()) View.VISIBLE else View.GONE

    updateEditModeIcon()
}

internal fun MainActivity.applyBackgroundImage() {
    val bgUri = Prefs.getBackgroundImage()
    if (bgUri != null) {
        try {
            val uri = Uri.parse(bgUri)
            contentResolver.openInputStream(uri)?.use { stream ->
                // Decode as a bitmap and hand it to the full-screen
                // backgroundImage ImageView (scaleType="centerCrop") so the
                // wallpaper fills the screen edge-to-edge, behind the top
                // bar and bottom bookmarks area, without ever stretching.
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                binding.backgroundImage.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            binding.backgroundImage.setImageDrawable(null)
            Prefs.setBackgroundImage(null)
        }
    } else {
        binding.backgroundImage.setImageDrawable(null)
    }
}

internal fun MainActivity.updateEditModeIcon() {
    binding.settingsBtn.text = if (Prefs.getEditMode()) "✏️" else "𑁍"
}

internal fun MainActivity.setupTopBar() {
    updateDateTime()
    updateAlarm()

    handler.postDelayed(object : Runnable {
        override fun run() {
            updateDateTime()
            updateAlarm()
            handler.postDelayed(this, 60000)
        }
    }, 60000)

    binding.dateText.setOnClickListener {
        openCalendarView()
    }

    binding.calendarBtn.setOnClickListener {
        openCalendarEvent()
    }

    binding.nextAlarm.setOnClickListener {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }
}

private fun MainActivity.openCalendarEvent() {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
    }

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        return
    }

    val fallback = Intent(Intent.ACTION_VIEW).apply {
        data = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
    }

    if (fallback.resolveActivity(packageManager) != null) {
        startActivity(fallback)
        return
    }

    Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
}

private fun MainActivity.openCalendarView() {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
    }

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        return
    }

    Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
}

private fun MainActivity.updateDateTime() {
    val now = Date()
    binding.dateText.text = dateFormat.format(now)
}

private fun MainActivity.updateAlarm() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nextAlarm = alarmManager.nextAlarmClock
    binding.nextAlarm.text = if (nextAlarm != null) {
        val time = android.text.format.DateFormat.getTimeFormat(this).format(Date(nextAlarm.triggerTime))
        "⏰ $time"
    } else {
        ""
    }
}

/** Hides or shows system status bar icons (battery, network, etc.) using legacy flags. */
internal fun MainActivity.setSystemUiVisibility(hide: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (hide) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}