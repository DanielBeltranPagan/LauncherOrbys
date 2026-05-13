package com.example.launchercalmado.data.source.local

import android.content.Context
import android.content.Intent
import com.example.launchercalmado.data.model.AppInfo

class AppLoader(private val context: Context) {

    /**
     * Esta función recupera todas las apps instaladas que tienen una "puerta de entrada"
     * (un icono en el cajón de aplicaciones).
     */
    fun loadInstalledApps(): List<AppInfo> {
        // El PackageManager es el "bibliotecario" del sistema Android
        val pm = context.packageManager

        // Creamos un "Intent" para buscar actividades principales (MAIN)
        // que estén en la categoría LAUNCHER (las que aparecen en el menú)
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Consultamos al sistema qué actividades coinciden con ese filtro
        val activities = pm.queryIntentActivities(intent, 0)

        // Transformamos la lista de objetos 'ResolveInfo' a nuestra clase personalizada 'AppInfo'
        return activities.map {
            AppInfo(
                // Extraemos el nombre de la app (ej: "WhatsApp")
                label = it.loadLabel(pm).toString(),
                // Extraemos el ID único del paquete (ej: "com.whatsapp")
                packageName = it.activityInfo.packageName,
                // Extraemos el icono visual de la aplicación
                icon = it.loadIcon(pm)
            )
        }.sortedBy { it.label.lowercase() } // Ordenamos la lista alfabéticamente (A-Z)
    }
}