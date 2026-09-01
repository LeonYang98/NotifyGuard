package com.example.notifyguard

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache

object AppInfoCache {

    private val names = LruCache<String, String>(128)
    private val icons = LruCache<String, Drawable>(64)

    fun appName(pm: PackageManager, pkg: String): String =
        names.get(pkg) ?: try {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            names.put(pkg, label)
            label
        } catch (e: Exception) {
            pkg
        }

    fun icon(pm: PackageManager, pkg: String): Drawable? =
        icons.get(pkg) ?: try {
            val d = pm.getApplicationIcon(pkg)
            icons.put(pkg, d)
            d
        } catch (e: Exception) {
            null
        }
}
