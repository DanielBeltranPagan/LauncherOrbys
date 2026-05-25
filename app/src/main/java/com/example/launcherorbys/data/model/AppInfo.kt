package com.example.launcherorbys.data.model

import android.graphics.drawable.Drawable

/**
 * Representa la información básica de una aplicación instalada.
 */

data class AppInfo(
    val label: String,      // Nombre visible de la app
    val packageName: String, // ID único del paquete
    val icon: Drawable?,    // Icono de la aplicación
    val isUninstallable: Boolean = true // Indica si la app puede ser desinstalada
)
