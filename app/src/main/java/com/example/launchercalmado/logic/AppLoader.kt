package com.example.launchercalmado.logic

import android.content.Context
import android.content.Intent
import com.example.launchercalmado.data.AppInfo

/**
 * Clase encargada de gestionar la carga de aplicaciones instaladas en el dispositivo.
 */
class AppLoader(private val context: Context) {

    /**
     * Obtiene una lista de aplicaciones que tienen una actividad de lanzamiento (launcher).
     * La lista se devuelve ordenada alfabéticamente por el nombre de la aplicación.
     */
    fun loadInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager

        // Filtramos para obtener solo las aplicaciones que se pueden "abrir" desde el menú
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val availableActivities = packageManager.queryIntentActivities(intent, 0)

        // Mapeamos los resultados de ResolveInfo a nuestro modelo AppInfo
        val apps = availableActivities.map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }

        // Retornamos la lista ordenada alfabéticamente (case-insensitive)
        return apps.sortedBy { it.label.lowercase() }
    }
}
