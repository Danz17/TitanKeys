package com.titankeys.keyboard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Data class representing an installed app.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
)

/**
 * Helper to get list of all installed apps that can be launched.
 */
object AppListHelper {
    private const val TAG = "AppListHelper"
    
    /**
     * Gets all installed apps that can be launched.
     */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()
        
        try {
            // Get all apps that have a launcher activity
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            
            // Group by package to avoid duplicates
            val packageNames = mutableSetOf<String>()
            
            for (resolveInfo in resolveInfos) {
                val packageName = resolveInfo.activityInfo.packageName
                
                // Avoid duplicates
                if (packageNames.contains(packageName)) {
                    continue
                }
                packageNames.add(packageName)
                
                try {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(packageName)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    apps.add(InstalledApp(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystemApp
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading info for $packageName", e)
                }
            }
            
            // Sort alphabetically by name
            apps.sortBy { it.appName.lowercase() }
            
            Log.d(TAG, "Loaded ${apps.size} installed apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving installed apps", e)
        }
        
        return apps
    }
}

