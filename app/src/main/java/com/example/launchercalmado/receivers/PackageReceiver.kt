package com.example.launchercalmado.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor de transmisiones (BroadcastReceiver) para detectar cambios en las apps instaladas.
 * Se encarga de notificar cuando se instala, desinstala o actualiza una aplicación.
 */
class PackageReceiver(private val onUpdate: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = onUpdate()
}
