package com.example.launcherorbys.data.repository

import android.content.Context
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.source.local.AppLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio encargado de gestionar la lista de aplicaciones instaladas en el dispositivo.
 *
 * Esta clase actúa como la única fuente de verdad para la capa de UI en lo que respecta
 * a la disponibilidad de aplicaciones. Utiliza un [AppLoader] para interactuar con el
 * [android.content.pm.PackageManager] del sistema.
 *
 * @property context El contexto de la aplicación necesario para acceder al [android.content.pm.PackageManager].
 * @constructor Crea un repositorio de aplicaciones con el contexto proporcionado.
 */
class AppRepository(private val context: Context) {
    private val loader = AppLoader(context)
    
    /**
     * Flujo de estado privado que mantiene la lista actual de aplicaciones.
     */
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Flujo de estado público que expone la lista de aplicaciones a los observadores.
     * Los suscriptores recibirán actualizaciones cada vez que se llame a [refreshApps].
     */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    init {
        refreshApps()
    }

    /**
     * Fuerza una recarga de la lista de aplicaciones desde el sistema.
     *
     * Este método consulta todas las actividades de lanzamiento (Launcher Activities)
     * instaladas y actualiza el flujo [_apps]. Debe llamarse cuando ocurran cambios
     * en el sistema (instalación/desinstalación) o al iniciar la aplicación.
     */
    fun refreshApps() {
        _apps.value = loader.loadInstalledApps()
    }
}
