package com.example.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Persists a lightweight snapshot of [AppInfo] (metadata + icon bitmaps) to
 * disk so the launcher can render instantly on a cold start, instead of
 * waiting on a fresh PackageManager scan + icon resolution every time the
 * process is recreated (e.g. after Android kills the launcher in the
 * background). loadApps() still runs its full live scan afterwards to keep
 * things accurate — this cache is purely for a fast first paint.
 */
object AppCache {
    private const val TAG = "AppCache"
    private const val CACHE_FILE = "app_cache.json"
    private const val ICON_DIR = "icon_cache"

    fun loadCachedApps(context: Context): List<AppInfo> {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val arr = JSONArray(file.readText())
            val iconDir = File(context.filesDir, ICON_DIR)

            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val iconFile = File(iconDir, "${id.iconFileSafe()}.png")
                val icon: Drawable? = if (iconFile.exists()) {
                    try {
                        BitmapFactory.decodeFile(iconFile.absolutePath)
                            ?.let { BitmapDrawable(context.resources, it) }
                    } catch (e: Exception) {
                        null
                    }
                } else null

                AppInfo(
                    id = id,
                    label = o.getString("label"),
                    packageName = o.getString("packageName"),
                    activityName = o.getString("activityName"),
                    prefix = o.getString("prefix"),
                    displayName = o.getString("displayName"),
                    icon = icon,
                    iconFromPack = o.optBoolean("iconFromPack", false),
                    shortcutId = if (o.has("shortcutId") && !o.isNull("shortcutId"))
                        o.getString("shortcutId") else null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load app cache", e)
            emptyList()
        }
    }

    /** Should be called from a background thread — does disk I/O. */
    fun saveApps(context: Context, apps: List<AppInfo>) {
        try {
            val iconDir = File(context.filesDir, ICON_DIR)
            if (!iconDir.exists()) iconDir.mkdirs()

            val keepFiles = mutableSetOf<String>()
            val arr = JSONArray()

            for (app in apps) {
                val o = JSONObject()
                o.put("id", app.id)
                o.put("label", app.label)
                o.put("packageName", app.packageName)
                o.put("activityName", app.activityName)
                o.put("prefix", app.prefix)
                o.put("displayName", app.displayName)
                o.put("iconFromPack", app.iconFromPack)
                if (app.shortcutId != null) o.put("shortcutId", app.shortcutId)
                arr.put(o)

                val icon = app.icon
                if (icon != null) {
                    val fileName = "${app.id.iconFileSafe()}.png"
                    keepFiles.add(fileName)
                    val iconFile = File(iconDir, fileName)
                    if (!iconFile.exists()) {
                        saveDrawableToFile(icon, iconFile)
                    }
                }
            }

            // Drop stale icons for apps that no longer exist.
            iconDir.listFiles()?.forEach { f ->
                if (f.name !in keepFiles) f.delete()
            }

            File(context.filesDir, CACHE_FILE).writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save app cache", e)
        }
    }

    private fun String.iconFileSafe(): String =
        this.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun saveDrawableToFile(drawable: Drawable, file: File) {
        try {
            val bitmap = drawableToBitmap(drawable)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache icon to ${file.name}", e)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
