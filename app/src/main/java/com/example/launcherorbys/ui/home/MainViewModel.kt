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
 * ViewModel central de la aplicación.
 * Gestiona el estado global (fondo, tema) y la sincronización de permisos críticos.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val permissionManager = PermissionManager(application)
    private val settingsRepository = SettingsRepository(application)

    // --- Estados de Apariencia ---
    var uriImagenFondo by mutableStateOf<Uri?>(null)
    var colorSolido by mutableStateOf<Color?>(null)
    var esTemaClaro by mutableStateOf(value = true)
    var navBarAtTop by mutableStateOf(value = false)

    // --- Estados de Permisos ---
    var isDefaultLauncher by mutableStateOf(value = false)
    var isAccessibilityEnabled by mutableStateOf(value = false)
    var canWriteSettings by mutableStateOf(value = false)
    var hasBluetoothPermission by mutableStateOf(value = false)

    init {
        updatePermissionStates()
        observeSettings()
    }

    /**
     * Observa los cambios en las preferencias guardadas en DataStore.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.fondoFlow.collect { fondo ->
                if (fondo.isNullOrEmpty()) {
                    setBackground(null, null)
                } else {
                    if (fondo.startsWith("content://")) {
                        setBackground(fondo.toUri(), null)
                    } else {
                        try {
                            setBackground(null, Color(fondo.toULong()))
                        } catch (_: Exception) {
                            setBackground(null, null)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.esClaroFlow.collect { isLight ->
                updateTheme(isLight)
            }
        }
    }

    /**
     * Guarda la preferencia de tema de forma persistente.
     */
    fun persistEsClaro(isLight: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveEsClaro(isLight)
        }
    }

    /**
     * Guarda la configuración de fondo de forma persistente.
     */
    fun persistFondo(fondo: String) {
        viewModelScope.launch {
            settingsRepository.saveFondo(fondo)
        }
    }

    /**
     * Sincroniza los estados locales con los permisos reales del sistema.
     */
    fun updatePermissionStates() {
        isDefaultLauncher = permissionManager.isDefaultLauncher()
        isAccessibilityEnabled = permissionManager.isAccessibilityEnabled()
        canWriteSettings = permissionManager.canWriteSettings()
        hasBluetoothPermission = permissionManager.hasBluetoothPermission()
    }

    /**
     * Lógica de verificación automática para el retorno desde pantallas de ajustes.
     * Detecta un cambio en el estado del permiso antes de regresar a la app.
     */
    fun startAutoCheck(check: () -> Boolean, onComplete: () -> Unit) {
        val initialValue = check()
        viewModelScope.launch {
            delay(500)
            var attempts = 0
            while (isActive && attempts < 40) {
                val currentValue = check()
                if (currentValue != initialValue) {
                    updatePermissionStates()
                    delay(200)
                    onComplete()
                    break
                }
                delay(1000)
                attempts++
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

    fun updateTheme(isLight: Boolean) {
        esTemaClaro = isLight
    }

    fun setBackground(uri: Uri?, color: Color?) {
        uriImagenFondo = uri
        colorSolido = color
    }
}
