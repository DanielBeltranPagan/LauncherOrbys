package com.example.launcherorbys.data.model

/**
 * Representa la información básica de una aplicación instalada.
 */
data class AppInfo(
    val nombre: String,           // Nombre visible de la app
    val nombrePaquete: String,    // ID único del paquete
    val esDesinstalable: Boolean = true // Indica si la app puede ser desinstalada
)
