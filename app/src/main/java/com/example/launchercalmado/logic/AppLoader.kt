package com.example.launchercalmado.logic

import android.content.Context
import android.content.Intent
import com.example.launchercalmado.data.AppInfo

class AppLoader(private val context: Context) {

    fun loadInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val manager = context.packageManager

        // Buscamos solo las apps que se pueden "abrir" (tienen icono en el menú)
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val availableActivities = manager.queryIntentActivities(intent, 0)

        for (ri in availableActivities) {
            val app = AppInfo(
                label = ri.loadLabel(manager).toString(),
                packageName = ri.activityInfo.packageName,
                icon = ri.activityInfo.loadIcon(manager)
            )
            apps.add(app)
        }

        // Las ordenamos de la A a la Z para que no sea un caos
        return apps.sortedBy { it.label.lowercase() }
    }
}