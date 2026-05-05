package com.example.launchercalmado.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageReceiver(private val onAppsChanged: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Cuando hay un cambio en los paquetes (instalación, desinstalación o actualización)
        // ejecutamos la función para recargar las apps
        onAppsChanged()
    }
}