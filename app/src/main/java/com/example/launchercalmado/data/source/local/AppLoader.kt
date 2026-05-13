package com.example.launchercalmado.data.source.local

import android.content.Context
import android.content.Intent
import com.example.launchercalmado.data.model.AppInfo

/**
 * Clase encargada de cargar las aplicaciones instaladas en el dispositivo.
 * Utiliza el PackageManager para consultar las apps que pueden ser lanzadas.
 */

class AppLoader(private val context: Context) {

    /**
     * Obtiene una lista de todas las aplicaciones instaladas que tienen una actividad principal (launcher).
     * @return Lista de [AppInfo] ordenada alfabéticamente por nombre.
     */
    fun loadInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        // Filtramos solo las aplicaciones que aparecen en el menú de aplicaciones del sistema
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        // Consultamos todas las actividades que coinciden con el filtro
        val availableActivities = packageManager.queryIntentActivities(intent, 0)
        
        // Mapeamos los resultados a nuestro modelo de datos AppInfo
        val apps = availableActivities.map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
        
        // Devolvemos la lista ordenada ignorando mayúsculas/minúsculas
        return apps.sortedBy { it.label.lowercase() }
    }
}
