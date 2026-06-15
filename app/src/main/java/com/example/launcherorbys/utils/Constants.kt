package com.example.launcherorbys.utils

/**
 * Constantes globales para el proyecto Launcher Orbys.
 * Centraliza las acciones de Intent y claves de preferencias para evitar errores por cadenas de texto.
 */
object Constants {
    // --- Acciones de Broadcast ---
    const val ACTION_THEME_CHANGED = "com.example.launcherorbys.THEME_CHANGED"
    const val ACTION_NAVBAR_POSITION_CHANGED = "com.example.launcherorbys.NAVBAR_POSITION_CHANGED"
    const val ACTION_NAVBAR_COMMAND = "com.example.launcherorbys.NAVBAR_COMMAND"
    const val ACTION_REQUEST_BLUETOOTH = "com.example.launcherorbys.REQUEST_BLUETOOTH"
    const val ACTION_SETTINGS_SEARCH = "com.example.launcherorbys.SETTINGS_SEARCH"

    // --- Claves de Preferencias ---
    const val KEY_WALLPAPER_URI = "fondo"
    const val KEY_IS_LIGHT_THEME = "esClaro"
    
    // --- Paquetes del Sistema ---
    const val PACKAGE_SETTINGS = "com.android.settings"
    const val PACKAGE_GOOGLE_SETTINGS = "com.google.android.settings"
}
