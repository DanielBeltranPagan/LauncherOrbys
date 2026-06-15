package com.example.launcherorbys.data.repository

import android.content.Context
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.source.local.AppLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio encargado de gestionar la lista de aplicaciones instaladas en el dispositivo.
 */
class AppRepository(private val contexto: Context) {
    private val cargador = AppLoader(contexto)
    
    /**
     * Flujo de estado privado que mantiene la lista actual de aplicaciones.
     */
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Flujo de estado público que expone la lista de aplicaciones a los observadores.
     */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    init {
        refrescarApps()
    }

    /**
     * Fuerza una recarga de la lista de aplicaciones desde el sistema.
     */
    fun refrescarApps() {
        _apps.value = cargador.cargarAppsInstaladas()
    }
}
