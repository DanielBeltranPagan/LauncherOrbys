package com.example.launchercalmado.ui.home

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel

/**
 * ViewModel encargado de gestionar el estado de la pantalla de inicio.
 * Mantiene la información sobre el fondo de pantalla, el tema y los menús.
 */
class HomeViewModel : ViewModel() {
    
    // Controla si el menú de opciones (pulsación larga) es visible
    var mostrarMenuContextual by mutableStateOf(false)
    
    // Almacena las coordenadas exactas donde el usuario tocó la pantalla
    var posicionToque by mutableStateOf(Offset.Zero)
    
    // Referencia a la imagen de fondo personalizada (si existe)
    var uriImagenFondo by mutableStateOf<Uri?>(null)
    
    // Color de fondo sólido (si no hay imagen o se prefiere color)
    var colorSolido by mutableStateOf<Color?>(null)
    
    // Determina si se debe aplicar el tema claro u oscuro
    var esTemaClaro by mutableStateOf(true)

    /**
     * Cierra cualquier menú o panel abierto.
     */
    fun cerrarTodo() {
        mostrarMenuContextual = false
    }

    /**
     * Se llama cuando el usuario realiza una pulsación larga en la pantalla.
     */
    fun onLongPress(offset: Offset) {
        posicionToque = offset
        mostrarMenuContextual = true
    }

    /**
     * Actualiza el estado del tema de la aplicación.
     */
    fun updateTheme(isLight: Boolean) {
        esTemaClaro = isLight
    }

    /**
     * Establece un nuevo fondo de pantalla (ya sea imagen o color).
     */
    fun setBackground(uri: Uri?, color: Color?) {
        uriImagenFondo = uri
        colorSolido = color
    }
}
