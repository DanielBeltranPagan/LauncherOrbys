package com.example.launcherorbys.data.repository

import android.content.Context
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.source.local.AppLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio encargado de gestionar la lista de aplicaciones instaladas.
 * Actúa como intermediario entre la fuente de datos (AppLoader) y la interfaz de usuario.
 */
class AppRepository(private val context: Context) {
    private val loader = AppLoader(context)
    
    // Flujo de estado que contiene la lista actual de aplicaciones
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    init {
        refreshApps()
    }

    /**
     * Fuerza una recarga de la lista de aplicaciones desde el sistema.
     */
    fun refreshApps() {
        _apps.value = loader.loadInstalledApps()
    }
}
