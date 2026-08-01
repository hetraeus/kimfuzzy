package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    }

    fun getTheme(): String = prefs.getString("theme", "light") ?: "light"
    fun setTheme(theme: String) = prefs.edit { putString("theme", theme) }

    fun getIconSize(): String = prefs.getString("icon_size", "default") ?: "default"
    fun setIconSize(size: String) = prefs.edit { putString("icon_size", size) }

    fun getIconPack(bucket: String): String {
        val key = "icon_pack_$bucket"
        if (prefs.contains(key)) return prefs.getString(key, "") ?: ""
        return prefs.getString("icon_pack", "") ?: ""
    }
    fun setIconPack(bucket: String, pack: String) = prefs.edit { putString("icon_pack_$bucket", pack) }

    fun getEditMode(): Boolean = prefs.getBoolean("edit_mode", false)
    fun setEditMode(enabled: Boolean) = prefs.edit { putBoolean("edit_mode", enabled) }

    fun getBlackCurtain(): Boolean = prefs.getBoolean("black_curtain", false)
    fun setBlackCurtain(enabled: Boolean) = prefs.edit { putBoolean("black_curtain", enabled) }

    fun backgroundBucketForTheme(theme: String): String = if (theme == "dark") "dark" else "light"
    fun currentBackgroundBucket(): String = backgroundBucketForTheme(getTheme())

    fun getBackgroundImage(bucket: String): String? = prefs.getString("background_image_$bucket", null)
    fun setBackgroundImage(bucket: String, uri: String?) {
        if (uri != null) {
            prefs.edit { putString("background_image_$bucket", uri) }
        } else {
            prefs.edit { remove("background_image_$bucket") }
        }
    }

    fun getAppPrefix(packageName: String): String? {
        if (!prefs.contains("prefix_$packageName")) return null
        return prefs.getString("prefix_$packageName", "") ?: ""
    }

    fun setAppPrefix(packageName: String, prefix: String) =
        prefs.edit { putString("prefix_$packageName", prefix) }

    fun getAppAnnotation(id: String): String? {
        if (!prefs.contains("annotation_$id")) return null
        return prefs.getString("annotation_$id", "") ?: ""
    }

    fun setAppAnnotation(id: String, annotation: String) {
        if (annotation.isBlank()) {
            prefs.edit { remove("annotation_$id") }
        } else {
            prefs.edit { putString("annotation_$id", annotation) }
        }
    }

    fun getPinnedApps(): Set<String> {
        val str = prefs.getString("pinned_apps", "") ?: ""
        return if (str.isEmpty()) emptySet() else str.split(",").toSet()
    }

    fun isPinned(id: String): Boolean = id in getPinnedApps()

    fun togglePin(id: String) {
        val set = getPinnedApps().toMutableSet()
        if (set.contains(id)) {
            set.remove(id)
        } else {
            set.add(id)
        }
        prefs.edit { putString("pinned_apps", set.joinToString(",")) }
    }

    fun getCustomLabel(id: String): String? {
        if (!prefs.contains("label_$id")) {
            return null
        }
        return prefs.getString("label_$id", "") ?: ""
    }

    fun setCustomLabel(id: String, label: String) =
        prefs.edit { putString("label_$id", label) }

    fun getBookmarks(): List<String> {
        val str = prefs.getString("bookmarks_ordered", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",")
    }

    fun isBookmarked(packageName: String): Boolean = packageName in getBookmarks()

    fun addBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        if (!list.contains(packageName)) {
            val firstEmpty = list.indexOf("")
            if (firstEmpty != -1) {
                list[firstEmpty] = packageName
            } else {
                list.add(packageName)
            }
            saveBookmarks(list)
        }
    }

    fun removeBookmark(packageName: String) {
        val list = getBookmarks().toMutableList()
        val index = list.indexOf(packageName)
        if (index != -1) {
            list[index] = ""
            saveBookmarks(list)
        }
    }

    fun saveBookmarks(list: List<String>) {
        prefs.edit { putString("bookmarks_ordered", list.joinToString(",")) }
    }

    fun getForgottenLinks(): Set<String> {
        val str = prefs.getString("forgotten_links", "") ?: ""
        return if (str.isEmpty()) emptySet() else str.split(",").toSet()
    }

    fun forgetLink(id: String) {
        val set = getForgottenLinks().toMutableSet()
        if (set.add(id)) {
            prefs.edit { putString("forgotten_links", set.joinToString(",")) }
        }
    }

    private const val LAST_LAUNCH_PREFIX = "last_launch_"

    fun getLastLaunchTime(appId: String): Long {
        return prefs.getLong(LAST_LAUNCH_PREFIX + appId, 0L)
    }

    fun setLastLaunchTime(appId: String, time: Long) {
        prefs.edit { putLong(LAST_LAUNCH_PREFIX + appId, time) }
    }

    fun export(): String {
        val root = JSONObject()
        root.put("theme", getTheme())
        root.put("icon_size", getIconSize())
        root.put("icon_pack_dark", getIconPack("dark"))
        root.put("icon_pack_light", getIconPack("light"))
        root.put("background_image_dark", getBackgroundImage("dark") ?: "")
        root.put("background_image_light", getBackgroundImage("light") ?: "")
        root.put("black_curtain", getBlackCurtain())

        val prefixes = JSONObject()
        val labels = JSONObject()
        val annotations = JSONObject()
        for (key in prefs.all.keys) {
            when {
                key.startsWith("prefix_") -> {
                    val pkg = key.removePrefix("prefix_")
                    prefixes.put(pkg, prefs.getString(key, ""))
                }
                key.startsWith("label_") -> {
                    val id = key.removePrefix("label_")
                    labels.put(id, prefs.getString(key, ""))
                }
                key.startsWith("annotation_") -> {
                    val id = key.removePrefix("annotation_")
                    annotations.put(id, prefs.getString(key, ""))
                }
                key == "pinned_apps" -> {
                    root.put("pinned_apps", prefs.getString(key, ""))
                }
            }
        }
        root.put("prefixes", prefixes)
        root.put("labels", labels)
        root.put("annotations", annotations)
        return root.toString(2)
    }

    fun import(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            prefs.edit {
                putString("theme", root.optString("theme", "light"))
                putString("icon_size", root.optString("icon_size", "default"))
                if (root.has("icon_pack_dark") || root.has("icon_pack_light")) {
                    putString("icon_pack_dark", root.optString("icon_pack_dark", ""))
                    putString("icon_pack_light", root.optString("icon_pack_light", ""))
                } else {
                    val legacyPack = root.optString("icon_pack", "")
                    putString("icon_pack_dark", legacyPack)
                    putString("icon_pack_light", legacyPack)
                }
                putBoolean("black_curtain", root.optBoolean("black_curtain", false))

                val bgDark = root.optString("background_image_dark", "")
                if (bgDark.isNotEmpty()) {
                    putString("background_image_dark", bgDark)
                } else {
                    remove("background_image_dark")
                }

                val bgLight = root.optString("background_image_light", "")
                if (bgLight.isNotEmpty()) {
                    putString("background_image_light", bgLight)
                } else {
                    remove("background_image_light")
                }

                for (key in prefs.all.keys) {
                    if (key.startsWith("prefix_") || key.startsWith("label_") || key.startsWith("annotation_") || key == "pinned_apps") {
                        remove(key)
                    }
                }

                val prefixes = root.optJSONObject("prefixes")
                if (prefixes != null) {
                    val keys = prefixes.keys()
                    while (keys.hasNext()) {
                        val pkg = keys.next()
                        putString("prefix_$pkg", prefixes.getString(pkg))
                    }
                }

                val labels = root.optJSONObject("labels")
                if (labels != null) {
                    val keys = labels.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        putString("label_$id", labels.getString(id))
                    }
                }

                val annotations = root.optJSONObject("annotations")
                if (annotations != null) {
                    val keys = annotations.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        putString("annotation_$id", annotations.getString(id))
                    }
                }

                val pinned = root.optString("pinned_apps", "")
                if (pinned.isNotEmpty()) {
                    putString("pinned_apps", pinned)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
