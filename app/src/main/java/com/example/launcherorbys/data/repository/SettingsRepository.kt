package com.example.launcherorbys.data.repository

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color

/**
 * Repositorio encargado de gestionar las preferencias persistentes del Launcher.
 * Centraliza el guardado y carga de configuraciones como el fondo de pantalla y el tema.
 */
class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FONDO = "fondo"
        private const val KEY_ES_CLARO = "esClaro"
    }

    /**
     * Guarda la configuración del fondo de pantalla.
     * @param fondo El valor en cadena del fondo (Uri o Color en ULong).
     */
    fun saveFondo(fondo: String) {
        prefs.edit().putString(KEY_FONDO, fondo).apply()
    }

    /**
     * Guarda la preferencia del tema (Claro/Oscuro).
     */
    fun saveEsClaro(esClaro: Boolean) {
        prefs.edit().putBoolean(KEY_ES_CLARO, esClaro).apply()
    }

    /**
     * Recupera el fondo guardado.
     */
    fun getFondo(): String? = prefs.getString(KEY_FONDO, null)

    /**
     * Recupera la preferencia de tema guardada (predeterminado: true / Claro).
     */
    fun getEsClaro(): Boolean = prefs.getBoolean(KEY_ES_CLARO, true)

    /**
     * Limpia la configuración de fondo (vuelve al predeterminado).
     */
    fun clearFondo() {
        prefs.edit().remove(KEY_FONDO).apply()
    }
}
