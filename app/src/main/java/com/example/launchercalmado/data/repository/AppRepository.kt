package com.example.launchercalmado.data.repository

import android.content.Context
import com.example.launchercalmado.data.model.AppInfo
import com.example.launchercalmado.data.source.local.AppLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio encargado de gestionar la lista de aplicaciones del sistema.
 * Utiliza un StateFlow para que la interfaz pueda reaccionar automáticamente a los cambios.
 */
class AppRepository(private val context: Context) {
    private val appLoader = AppLoader(context)
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    /**
     * Flow observable con la lista de aplicaciones instaladas.
     */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    init {
        refreshApps() // Carga inicial al crear el repositorio
    }

    /**
     * Fuerza la recarga de las aplicaciones desde el sistema.
     */
    fun refreshApps() {
        _apps.value = appLoader.loadInstalledApps()
    }
}
