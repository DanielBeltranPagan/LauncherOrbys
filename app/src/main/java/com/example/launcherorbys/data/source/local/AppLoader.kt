package com.example.launcherorbys.data.source.local

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.launcherorbys.data.model.AppInfo

/**
 * Cargador de datos encargado de consultar el sistema Android para obtener las aplicaciones instaladas.
 * Utiliza el PackageManager para filtrar solo aquellas que pueden ser lanzadas por el usuario.
 */
class AppLoader(private val context: Context) {

    /**
     * Recupera todas las aplicaciones instaladas que tienen una actividad principal con la categoría LAUNCHER.
     * 
     * @return Una lista de objetos [AppInfo] ordenada alfabéticamente por etiqueta.
     */
    fun loadInstalledApps(): List<AppInfo> {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(intent, 0)

            return activities.mapNotNull {
                try {
                    val appInfo = it.activityInfo.applicationInfo
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    AppInfo(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        icon = it.loadIcon(pm),
                        isUninstallable = !isSystemApp || isUpdatedSystemApp
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}