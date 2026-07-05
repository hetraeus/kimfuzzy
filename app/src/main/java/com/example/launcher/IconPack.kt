package com.example.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException

object IconPack {
    private const val TAG = "IconPack"
    private var appFilterMap: Map<String, String>? = null
    private var cachedPack: String = ""
    private val iconCache = mutableMapOf<String, Drawable?>()

    fun clearCache() {
        iconCache.clear()
        appFilterMap = null
        cachedPack = ""
    }

    fun getAppFilterSize(): Int = appFilterMap?.size ?: 0

    fun discover(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val packs = mutableListOf("" to "Default")

        val categories = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.fede.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.theme",
            "com.google.android.apps.nexuslauncher.THEME"
        )

        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolves = pm.queryIntentActivities(intent, 0)
            for (resolve in resolves) {
                val label = resolve.loadLabel(pm).toString()
                val pkg = resolve.activityInfo.packageName
                if (packs.none { it.first == pkg }) {
                    packs.add(pkg to label)
                }
            }
        }

        return packs
    }

    fun loadIcon(
        context: Context,
        iconPackPackage: String,
        targetPackage: String,
        targetActivity: String?,
        defaultIcon: Drawable?
    ): Pair<Drawable?, Boolean> {
        if (iconPackPackage.isBlank()) return defaultIcon to false

        val cacheKey = "$iconPackPackage:$targetPackage/$targetActivity"
        if (cacheKey in iconCache) {
            val cached = iconCache[cacheKey]
            return (cached ?: defaultIcon) to (cached != null)
        }

        if (cachedPack != iconPackPackage || appFilterMap == null) {
            appFilterMap = parseAppFilter(context, iconPackPackage)
            cachedPack = iconPackPackage
            Log.i(TAG, "Loaded appfilter for $iconPackPackage: ${appFilterMap?.size ?: 0} entries")
        }

        val pm = context.packageManager
        val packResources = try {
            pm.getResourcesForApplication(iconPackPackage)
        } catch (e: Exception) {
            iconCache[cacheKey] = null
            return defaultIcon to false
        }

        val filterMap = appFilterMap
        if (filterMap != null) {
            if (targetActivity != null) {
                val component = "$targetPackage/$targetActivity"
                filterMap[component]?.let { name ->
                    val resId = resolveResourceId(packResources, iconPackPackage, name)
                    if (resId != 0) {
                        try {
                            val drawable = packResources.getDrawable(resId, context.theme)
                            iconCache[cacheKey] = drawable
                            return drawable to true
                        } catch (e: Exception) {
                            Log.i(TAG, "Failed to load $name for $component")
                        }
                    }
                }
            }

            filterMap[targetPackage]?.let { name ->
                val resId = resolveResourceId(packResources, iconPackPackage, name)
                if (resId != 0) {
                    try {
                        val drawable = packResources.getDrawable(resId, context.theme)
                        iconCache[cacheKey] = drawable
                        return drawable to true
                    } catch (e: Exception) {
                        Log.i(TAG, "Failed to load $name for $targetPackage")
                    }
                }
            }
        }

        iconCache[cacheKey] = null
        return defaultIcon to false
    }

    private fun resolveResourceId(resources: android.content.res.Resources, pkg: String, name: String): Int {
        var resId = resources.getIdentifier(name, "drawable", pkg)
        if (resId == 0) resId = resources.getIdentifier(name, "mipmap", pkg)
        if (resId == 0) resId = resources.getIdentifier(name.lowercase(), "drawable", pkg)
        if (resId == 0) resId = resources.getIdentifier(name.lowercase(), "mipmap", pkg)
        return resId
    }

    private fun parseAppFilter(context: Context, iconPackPackage: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val packageFallback = mutableMapOf<String, String>()

        try {
            val packContext = context.createPackageContext(
                iconPackPackage,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val packResources = packContext.resources

            var found = false

            // 1. Try assets/
            val assetPaths = listOf(
                "appfilter.xml",
                "xml/appfilter.xml",
                "appmap.xml",
                "xml/appmap.xml"
            )
            for (path in assetPaths) {
                try {
                    packContext.assets.open(path).use { stream ->
                        val parser = Xml.newPullParser()
                        parser.setInput(stream, "UTF-8")
                        parseAppFilterXml(parser, map, packageFallback)
                    }
                    found = true
                    Log.i(TAG, "Parsed appfilter from assets/$path")
                    break
                } catch (e: IOException) {
                    Log.d(TAG, "Not found in assets: $path")
                }
            }

            // 2. Try res/xml/ and res/raw/
            if (!found) {
                val resConfigs = listOf(
                    "appfilter" to "xml",
                    "appmap" to "xml",
                    "appfilter" to "raw",
                    "appmap" to "raw"
                )
                for ((name, type) in resConfigs) {
                    val resId = packResources.getIdentifier(name, type, iconPackPackage)
                    if (resId != 0) {
                        val parser = packResources.getXml(resId)
                        parseAppFilterXml(parser, map, packageFallback)
                        found = true
                        Log.i(TAG, "Parsed appfilter from res/$type/$name")
                        break
                    }
                }
            }

            if (!found) {
                Log.w(TAG, "No appfilter found in $iconPackPackage (tried assets and resources)")
            }

            Log.i(TAG, "Total parsed: ${map.size} component entries, ${packageFallback.size} package fallbacks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appfilter for $iconPackPackage", e)
        }
        return map + packageFallback
    }

    private fun parseAppFilterXml(
        parser: XmlPullParser,
        map: MutableMap<String, String>,
        packageFallback: MutableMap<String, String>
    ) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")?.trim()
                val drawable = parser.getAttributeValue(null, "drawable")?.trim()
                if (component != null && drawable != null) {
                    val clean = component
                        .removePrefix("ComponentInfo{")
                        .removeSuffix("}")
                    map[clean] = drawable

                    val pkgOnly = clean.substringBefore("/")
                    if (!packageFallback.containsKey(pkgOnly)) {
                        packageFallback[pkgOnly] = drawable
                    }
                }
            }
            eventType = parser.next()
        }
    }
}
