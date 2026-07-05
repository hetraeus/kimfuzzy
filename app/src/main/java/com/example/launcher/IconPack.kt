package com.example.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

object IconPack {
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

    fun loadIcon(context: Context, iconPackPackage: String, targetPackage: String): Drawable? {
        if (iconPackPackage.isBlank()) return null

        return try {
            val pm = context.packageManager
            val packResources = pm.getResourcesForApplication(iconPackPackage)

            val candidates = listOf(
                targetPackage,
                targetPackage.lowercase(),
                targetPackage.replace(".", "_"),
                targetPackage.replace(".", "_").lowercase(),
                targetPackage.replace(".", ""),
                targetPackage.replace(".", "").lowercase(),
                targetPackage.substringAfterLast("."),
                targetPackage.substringAfterLast(".").lowercase(),
                "ic_" + targetPackage.replace(".", "_").lowercase()
            )

            for (name in candidates) {
                var resId = packResources.getIdentifier(name, "drawable", iconPackPackage)
                if (resId == 0) {
                    resId = packResources.getIdentifier(name, "mipmap", iconPackPackage)
                }
                if (resId != 0) {
                    return packResources.getDrawable(resId, context.theme)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
