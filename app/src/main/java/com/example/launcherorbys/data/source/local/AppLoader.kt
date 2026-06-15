package com.example.launcherorbys.data.source.local

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.launcherorbys.data.model.AppInfo

/**
 * Proveedor de datos encargado de interactuar con el sistema Android para extraer información
 * sobre las aplicaciones instaladas.
 *
 * Esta clase encapsula el uso del [android.content.pm.PackageManager] para filtrar y obtener
 * solo las aplicaciones que son relevantes para el Launcher (aquellas con una actividad principal).
 *
 * @property context El contexto necesario para acceder al [android.content.pm.PackageManager].
 */
class AppLoader(private val context: Context) {

    /**
     * Consulta el sistema para obtener la lista de aplicaciones que pueden ser ejecutadas por el usuario.
     *
     * Utiliza una intención con acción [Intent.ACTION_MAIN] y categoría [Intent.CATEGORY_LAUNCHER]
     * para identificar las "puertas de entrada" a las aplicaciones instaladas.
     *
     * @return Una lista de objetos [AppInfo] conteniendo el nombre, paquete, icono y estado de
     *         desinstalación, ordenada alfabéticamente ignorando mayúsculas.
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