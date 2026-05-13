package com.example.launchercalmado.data.model

import android.graphics.drawable.Drawable

/**
 * Modelo de datos que representa una aplicación instalada en el sistema.
 * Contiene la información básica necesaria para mostrarla en el cajón de apps.
 */
data class AppInfo(
    val label: String,       // Nombre legible de la aplicación
    val packageName: String, // Identificador único del paquete (ej: com.android.settings)
    val icon: Drawable?      // Icono de la aplicación obtenido del sistema
)
