package com.example.launcherorbys.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extensión para acceder a la instancia única de DataStore.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/**
 * Repositorio encargado de gestionar las preferencias persistentes del Launcher utilizando Jetpack DataStore.
 *
 * Centraliza el guardado y carga de configuraciones del usuario de forma asíncrona y segura.
 * Permite persistir el estado del fondo de pantalla y la preferencia del tema visual.
 *
 * @property context El contexto necesario para acceder a DataStore.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        /** Clave para persistir el valor del fondo de pantalla. */
        private val KEY_FONDO = stringPreferencesKey("fondo")
        /** Clave para persistir si el tema es claro u oscuro. */
        private val KEY_ES_CLARO = booleanPreferencesKey("esClaro")
    }

    /**
     * Guarda la configuración del fondo de pantalla de forma persistente.
     *
     * @param fondo El valor representativo del fondo.
     */
    suspend fun saveFondo(fondo: String) {
        dataStore.edit { preferences ->
            preferences[KEY_FONDO] = fondo
        }
    }

    /**
     * Guarda la preferencia del tema visual.
     *
     * @param esClaro `true` si se debe usar el tema claro, `false` para el tema oscuro.
     */
    suspend fun saveEsClaro(esClaro: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ES_CLARO] = esClaro
        }
    }

    /**
     * Flujo que emite el fondo guardado actualmente.
     */
    val fondoFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_FONDO]
    }

    /**
     * Flujo que emite la preferencia de tema guardada.
     */
    val esClaroFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ES_CLARO] ?: true
    }

    /**
     * Elimina la configuración personalizada de fondo.
     */
    suspend fun clearFondo() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_FONDO)
        }
    }
}
