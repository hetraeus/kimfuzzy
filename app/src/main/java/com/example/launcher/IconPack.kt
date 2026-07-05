package com.example.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException

object IconPack {
    private var appFilterMap: Map<String, String>? = null
    private var cachedPack: String = ""

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
        appLabel: String?
    ): Drawable? {
        if (iconPackPackage.isBlank()) return null

        if (cachedPack != iconPackPackage || appFilterMap == null) {
            appFilterMap = parseAppFilter(context, iconPackPackage)
            cachedPack = iconPackPackage
        }

        val pm = context.packageManager
        val packResources = try {
            pm.getResourcesForApplication(iconPackPackage)
        } catch (e: Exception) {
            return null
        }

        val filterMap = appFilterMap
        if (filterMap != null) {
            if (targetActivity != null) {
                val component = "$targetPackage/$targetActivity"
                filterMap[component]?.let { name ->
                    val resId = resolveResourceId(packResources, iconPackPackage, name)
                    if (resId != 0) return packResources.getDrawable(resId, context.theme)
                }
            }
            filterMap[targetPackage]?.let { name ->
                val resId = resolveResourceId(packResources, iconPackPackage, name)
                if (resId != 0) return packResources.getDrawable(resId, context.theme)
            }
        }

        val candidates = buildCandidateNames(targetPackage, targetActivity, appLabel)

        for (name in candidates) {
            val resId = resolveResourceId(packResources, iconPackPackage, name)
            if (resId != 0) {
                return packResources.getDrawable(resId, context.theme)
            }
        }

        return null
    }

    private fun buildCandidateNames(pkg: String, activity: String?, label: String?): List<String> {
        val candidates = mutableListOf<String>()
        val pkgLower = pkg.lowercase()
        val lastPart = pkg.substringAfterLast(".").lowercase()

        candidates.add(pkg)
        candidates.add(pkgLower)
        candidates.add(pkg.replace(".", "_"))
        candidates.add(pkg.replace(".", "_").lowercase())
        candidates.add(pkg.replace(".", ""))
        candidates.add(pkg.replace(".", "").lowercase())
        candidates.add(lastPart)
        candidates.add("ic_" + pkg.replace(".", "_").lowercase())

        if (activity != null) {
            val actSimple = activity.substringAfterLast(".").lowercase()
            val actFull = activity.replace(".", "_").lowercase()
            candidates.add(actSimple)
            candidates.add(actFull)
            candidates.add("${lastPart}_$actSimple")
            candidates.add("${pkg.replace(".", "_").lowercase()}_$actSimple")
            candidates.add(activity)
            candidates.add(activity.lowercase())
        }

        listOf("com_", "org_", "net_", "app_").forEach { prefix ->
            if (pkgLower.startsWith(prefix)) {
                val stripped = pkgLower.removePrefix(prefix)
                candidates.add(stripped)
                candidates.add(stripped.replace(".", "_"))
                candidates.add(stripped.substringAfterLast("."))
            }
        }

        if (label != null) {
            val clean = label.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
            if (clean.isNotBlank()) {
                candidates.add(clean)
                candidates.add(clean.removePrefix("the_"))
                candidates.add("ic_$clean")
            }
        }

        return candidates.distinct()
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
        try {
            val packContext = context.createPackageContext(
                iconPackPackage,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val assetManager = packContext.assets

            val paths = listOf("appfilter.xml", "xml/appfilter.xml", "res/xml/appfilter.xml")
            val inputStream = paths.firstNotNullOfOrNull { path ->
                try {
                    assetManager.open(path)
                } catch (e: IOException) {
                    null
                }
            } ?: return map

            inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            val clean = component
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")
                            map[clean] = drawable
                            val pkgOnly = clean.substringBefore("/")
                            if (!map.containsKey(pkgOnly)) {
                                map[pkgOnly] = drawable
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            // Appfilter missing or unreadable
        }
        return map
    }
}
