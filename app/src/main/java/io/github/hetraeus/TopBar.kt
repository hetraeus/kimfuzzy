package io.github.hetraeus.kimfuzzy

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.widget.Toast
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Date

@Suppress("DEPRECATION")
internal fun MainActivity.setupWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val controller = WindowInsetsControllerCompat(window, window.decorView)
    val isLight = isBackgroundLight()
    controller.isAppearanceLightStatusBars = isLight
    controller.isAppearanceLightNavigationBars = isLight

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
}

internal fun MainActivity.applyThemeColors() {
    val bg = ThemeUtils.getBackgroundColor()
    val text = ThemeUtils.getTextColor()
    val textSecondary = ThemeUtils.getSecondaryTextColor()
    val accent = ThemeUtils.getAccentColor(this)

    binding.root.setBackgroundColor(bg)
    binding.topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)

    binding.dateText.setTextColor(text)
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
    val bgUri = Prefs.getBackgroundImage(Prefs.currentBackgroundBucket())
    if (bgUri != null) {
        try {
            val uri = bgUri.toUri()
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                binding.backgroundImage.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            binding.backgroundImage.setImageDrawable(null)
            Prefs.setBackgroundImage(Prefs.currentBackgroundBucket(), null)
        }
    } else {
        binding.backgroundImage.setImageDrawable(null)
    }
}

private fun MainActivity.isBackgroundLight(): Boolean {
    val bgUri = Prefs.getBackgroundImage(Prefs.currentBackgroundBucket())
    return if (bgUri != null) {
        try {
            val uri = bgUri.toUri()
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                bitmap?.let { bmp ->
                    val pixel = bmp[bmp.width / 2, bmp.height / 2]
                    android.graphics.Color.luminance(pixel) > 0.5
                } ?: false
            } ?: false
        } catch (e: Exception) {
            false
        }
    } else {
        val bg = ThemeUtils.getBackgroundColor()
        android.graphics.Color.luminance(bg) > 0.5
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

internal fun MainActivity.setSystemUiVisibility(hide: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (hide) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
