package com.example.launcherorbys.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcherorbys.data.repository.SettingsRepository
import com.example.launcherorbys.managers.PermissionManager
import com.example.launcherorbys.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel central de la aplicación Launcher Orbys.
 *
 * Actúa como la "fuente de verdad" para el estado de la UI global, incluyendo la apariencia
 * (temas, fondos) y el estado de los permisos críticos del sistema.
 *
 * Utiliza [SettingsRepository] para la persistencia de datos y [PermissionManager] para
 * validar el acceso a funciones restringidas de Android.
 *
 * @param application Referencia a la aplicación para el acceso a recursos y servicios del sistema.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val gestorPermisos = PermissionManager(application)
    private val repositorioAjustes = SettingsRepository(application)

    // --- Estados de Apariencia ---
    /** URI de la imagen seleccionada como fondo de pantalla. Si es nula, se usa [colorSolido]. */
    var uriImagenFondo by mutableStateOf<Uri?>(null)
    /** Color de fondo sólido si no se ha definido una imagen. */
    var colorSolido by mutableStateOf<Color?>(null)
    /** Indica si el tema visual actual es claro (`true`) u oscuro (`false`). */
    var esTemaClaro by mutableStateOf(value = true)
    /** Indica si la barra de navegación del sistema está configurada en la parte superior. */
    var navBarEnLaParteSuperior by mutableStateOf(value = false)

    // --- Estados de Permisos ---
    /** `true` si esta app es el launcher por defecto actual. */
    var esLauncherPorDefecto by mutableStateOf(value = false)
    /** `true` si el servicio de accesibilidad de la app está activo. */
    var estaAccesibilidadHabilitada by mutableStateOf(value = false)
    /** `true` si la app puede modificar ajustes del sistema (brillo, etc). */
    var puedeEscribirAjustes by mutableStateOf(value = false)
    /** `true` si se tienen permisos dinámicos de Bluetooth concedidos. */
    var tienePermisoBluetooth by mutableStateOf(value = false)

    init {
        actualizarEstadosPermisos()
        observarAjustes()
    }

    /**
     * Inicia la observación de los flujos de datos desde el repositorio de ajustes.
     * Actualiza automáticamente el fondo y el tema cuando cambian en el DataStore.
     */
    private fun observarAjustes() {
        viewModelScope.launch {
            repositorioAjustes.flujoFondo.collect { fondo ->
                if (fondo.isNullOrEmpty()) {
                    establecerFondo(null, null)
                } else {
                    if (fondo.startsWith("content://")) {
                        establecerFondo(fondo.toUri(), null)
                    } else {
                        try {
                            establecerFondo(null, Color(fondo.toULong()))
                        } catch (_: Exception) {
                            establecerFondo(null, null)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repositorioAjustes.flujoEsClaro.collect { esClaro ->
                actualizarTema(esClaro)
            }
        }
    }

    fun persistEsClaro(esClaro: Boolean) {
        viewModelScope.launch {
            repositorioAjustes.guardarEsClaro(esClaro)
        }
    }

    fun persistFondo(fondo: String) {
        viewModelScope.launch {
            repositorioAjustes.guardarFondo(fondo)
        }
    }

    fun actualizarEstadosPermisos() {
        esLauncherPorDefecto = gestorPermisos.esLauncherPorDefecto()
        estaAccesibilidadHabilitada = gestorPermisos.estaAccesibilidadHabilitada()
        puedeEscribirAjustes = gestorPermisos.puedeEscribirAjustes()
        tienePermisoBluetooth = gestorPermisos.tienePermisoBluetooth()
    }

    fun iniciarComprobacionAutomatica(verificar: () -> Boolean, alCompletar: () -> Unit) {
        val valorInicial = verificar()
        viewModelScope.launch {
            delay(500)
            var intentos = 0
            while (isActive && intentos < 40) {
                val valorActual = verificar()
                if (valorActual != valorInicial) {
                    actualizarEstadosPermisos()
                    delay(200)
                    alCompletar()
                    break
                }
                delay(1000)
                intentos++
            }
        }
    }

    fun cerrarTodo() {
        val intent = Intent(Constants.ACTION_NAVBAR_COMMAND).apply {
            putExtra("comando", "CLOSE_ALL")
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    fun actualizarTema(esClaro: Boolean) {
        esTemaClaro = esClaro
    }

    fun establecerFondo(uri: Uri?, color: Color?) {
        uriImagenFondo = uri
        colorSolido = color
    }
}
