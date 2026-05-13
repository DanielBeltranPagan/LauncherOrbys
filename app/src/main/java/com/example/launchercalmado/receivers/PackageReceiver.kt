package com.example.launchercalmado.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor de transmisiones (BroadcastReceiver) para detectar cambios en las apps instaladas.
 * Se encarga de notificar cuando se instala, desinstala o actualiza una aplicación.
 */
class PackageReceiver(private val onAppsChanged: () -> Unit) : BroadcastReceiver() {

    /**
     * Se activa cuando el sistema envía un evento relacionado con los paquetes de aplicaciones.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        // Ejecutamos la función de retrollamada para que el repositorio recargue la lista de apps
        onAppsChanged()
    }
}
