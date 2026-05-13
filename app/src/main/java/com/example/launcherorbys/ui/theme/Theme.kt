package com.example.launcherorbys.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Esquema de colores para el modo oscuro
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8)
)

// Esquema de colores para el modo claro
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260)
)

/**
 * Tema principal de la aplicación.
 * Configura los colores de Material Design 3 y ajusta las barras del sistema.
 */
@Composable
fun LauncherOrbysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Los colores dinámicos (Material You) están disponibles en Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Selección del esquema de colores basado en la versión de Android y preferencia del usuario
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Efecto secundario para configurar las barras de estado y navegación del sistema
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                // Ponemos las barras transparentes para que el launcher ocupe toda la pantalla
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                
                // Ajustamos el color de los iconos de la barra de estado (negro o blanco) según el tema
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
