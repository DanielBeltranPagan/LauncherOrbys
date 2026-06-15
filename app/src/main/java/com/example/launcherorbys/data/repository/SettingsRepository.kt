package com.example.launcherorbys.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.launcherorbys.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extensión para acceder a la instancia única de DataStore.
 */
private val Context.almacenDatos: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/**
 * Repositorio encargado de gestionar las preferencias persistentes del Launcher mediante Jetpack DataStore.
 *
 * Centraliza el almacenamiento y recuperación de configuraciones del usuario como el fondo de pantalla
 * (ya sea una URI de imagen o un valor de color) y la preferencia del tema visual.
 */
class SettingsRepository(contexto: Context) {

    private val almacenDatos = contexto.applicationContext.almacenDatos

    companion object {
        private val CLAVE_FONDO = stringPreferencesKey(Constants.KEY_WALLPAPER_URI)
        private val CLAVE_ES_CLARO = booleanPreferencesKey(Constants.KEY_IS_LIGHT_THEME)
    }

    /**
     * Guarda la referencia del fondo de pantalla.
     * @param fondo Cadena que representa la URI de la imagen o el valor hexadecimal del color.
     */
    suspend fun guardarFondo(fondo: String) {
        almacenDatos.edit { preferencias ->
            preferencias[CLAVE_FONDO] = fondo
        }
    }

    /**
     * Guarda la preferencia del tema visual.
     * @param esClaro `true` para tema claro, `false` para tema oscuro.
     */
    suspend fun guardarEsClaro(esClaro: Boolean) {
        almacenDatos.edit { preferencias ->
            preferencias[CLAVE_ES_CLARO] = esClaro
        }
    }

    /**
     * Flujo reactivo que emite el valor actual del fondo de pantalla.
     */
    val flujoFondo: Flow<String?> = almacenDatos.data.map { preferencias ->
        preferencias[CLAVE_FONDO]
    }

    /**
     * Flujo reactivo que emite la preferencia de tema claro/oscuro.
     * Por defecto devuelve `true` (claro) si no hay un valor guardado.
     */
    val flujoEsClaro: Flow<Boolean> = almacenDatos.data.map { preferencias ->
        preferencias[CLAVE_ES_CLARO] ?: true
    }

    /**
     * Elimina la configuración del fondo de pantalla de las preferencias.
     */
    suspend fun limpiarFondo() {
        almacenDatos.edit { preferencias ->
            preferencias.remove(CLAVE_FONDO)
        }
    }
}
