package com.example.launcherorbys.ui.home

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel

/**
 * ViewModel que gestiona el estado global de la pantalla de inicio.
 * Controla el fondo de pantalla, el tema y la visibilidad de menús.
 */
class HomeViewModel : ViewModel() {
    
    // Estados reactivos que la UI observa para actualizarse
    var mostrarMenuContextual by mutableStateOf(false)
    var posicionToque by mutableStateOf(Offset.Zero)
    var uriImagenFondo by mutableStateOf<Uri?>(null)
    var colorSolido by mutableStateOf<Color?>(null)
    var esTemaClaro by mutableStateOf(true)

    fun cerrarTodo() {
        mostrarMenuContextual = false
    }

    fun onLongPress(offset: Offset) {
        posicionToque = offset
        mostrarMenuContextual = true
    }

    fun updateTheme(isLight: Boolean) {
        esTemaClaro = isLight
    }

    /**
     * Define el fondo de la pantalla de inicio, ya sea una imagen (Uri) o un color sólido.
     */
    fun setBackground(uri: Uri?, color: Color?) {
        uriImagenFondo = uri
        colorSolido = color
    }
}
