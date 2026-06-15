package com.example.launcherorbys.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Componente base para los iconos circulares de la barra de estado.
 * Proporciona un diseño uniforme con fondo blanco e iconos negros.
 *
 * @param imageVector El icono a mostrar.
 * @param contentDescription Descripción para accesibilidad.
 * @param onClick Acción a ejecutar al pulsar el icono.
 * @param isVisible Controla si el icono debe renderizarse o no.
 */
@Composable
fun StatusIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isVisible: Boolean = true
) {
    if (isVisible) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White, shape = CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
