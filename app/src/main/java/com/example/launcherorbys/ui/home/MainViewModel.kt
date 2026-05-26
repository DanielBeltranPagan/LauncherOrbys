package com.example.launcherorbys.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // --- Estados de Apariencia ---
    var uriImagenFondo by mutableStateOf<Uri?>(null)
    var colorSolido by mutableStateOf<Color?>(null)
    var esTemaClaro by mutableStateOf(true)
    var navBarAtTop by mutableStateOf(false)

    // --- Estados de Permisos ---
    var isDefaultLauncher by mutableStateOf(false)
    var isAccessibilityEnabled by mutableStateOf(false)
    var canWriteSettings by mutableStateOf(false)
    var hasBluetoothPermission by mutableStateOf(false)

    init {
        updatePermissionStates()
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
     */
    fun startAutoCheck(check: () -> Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            delay(500)
            var attempts = 0
            while (isActive && attempts < 30) {
                if (check()) {
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
