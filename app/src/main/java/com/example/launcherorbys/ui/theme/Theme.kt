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
 * Tema principal de la aplicación LauncherOrbys.
 *
 * Configura los esquemas de color de Material Design 3, la tipografía global y los estilos
 * de las barras del sistema (StatusBar y NavigationBar).
 *
 * Esta función composable centraliza la apariencia visual de toda la aplicación, manejando
 * automáticamente el cambio entre temas claro y oscuro, así como el soporte para colores
 * dinámicos (Material You) en dispositivos compatibles.
 *
 * @param darkTheme Indica si se debe aplicar el esquema de colores oscuro. Por defecto usa [isSystemInDarkTheme].
 * @param dynamicColor Indica si se deben habilitar los colores dinámicos basados en el fondo de pantalla
 *                     del sistema (disponible en Android 12+).
 * @param baseWeight El peso de fuente base que se aplicará a la tipografía del sistema.
 * @param content El contenido Composable que se renderizará bajo este tema.
 */
@Composable
fun LauncherOrbysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Los colores dinámicos (Material You) están disponibles en Android 12+
    dynamicColor: Boolean = true,
    baseWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
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
        typography = getAppTypography(baseWeight),
        content = content
    )
}
