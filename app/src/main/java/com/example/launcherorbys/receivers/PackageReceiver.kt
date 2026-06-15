package com.example.launcherorbys.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor de transmisiones ([BroadcastReceiver]) para detectar cambios dinámicos en las aplicaciones del sistema.
 *
 * Este receptor escucha eventos como la instalación de nuevos paquetes, la desinstalación de existentes
 * o la actualización de los mismos. Cuando se detecta cualquiera de estos eventos, ejecuta el callback [onUpdate]
 * para permitir que la aplicación refresque su lista interna de aplicaciones.
 *
 * @property onUpdate Función que se ejecutará cada vez que se reciba una transmisión de cambio de paquete.
 */
class PackageReceiver(private val onUpdate: () -> Unit) : BroadcastReceiver() {
    
    /**
     * Se invoca cuando el sistema envía una transmisión que coincide con los filtros configurados.
     * 
     * @param context El contexto en el que se está ejecutando el receptor.
     * @param intent El [Intent] que contiene los detalles de la transmisión (acción, datos del paquete, etc.).
     */
    override fun onReceive(context: Context?, intent: Intent?) = onUpdate()
}
