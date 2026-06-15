package com.example.launcherorbys.data.source.local

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.launcherorbys.data.model.AppInfo

/**
 * Proveedor de datos encargado de interactuar con el sistema Android para extraer información
 * sobre las aplicaciones instaladas.
 */
class AppLoader(private val contexto: Context) {

    /**
     * Consulta el sistema para obtener la lista de aplicaciones que pueden ser ejecutadas por el usuario.
     */
    fun cargarAppsInstaladas(): List<AppInfo> {
        try {
            val pm = contexto.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val actividades = pm.queryIntentActivities(intent, 0)

            return actividades.mapNotNull {
                try {
                    val infoApp = it.activityInfo.applicationInfo
                    val esAppSistema = (infoApp.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val esAppSistemaActualizada = (infoApp.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    AppInfo(
                        nombre = it.loadLabel(pm).toString(),
                        nombrePaquete = it.activityInfo.packageName,
                        esDesinstalable = !esAppSistema || esAppSistemaActualizada
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.nombrePaquete }
             .sortedBy { it.nombre.lowercase() }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
