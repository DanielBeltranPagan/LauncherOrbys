package com.example.launcherorbys.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Genera la configuración de tipografía para el Launcher.
 * Permite inyectar un peso base para soportar manualmente el modo 'Texto en negrita' del sistema
 * si el escalado automático del framework no fuera suficiente.
 */
fun getAppTypography(baseWeight: FontWeight = FontWeight.Normal): Typography {
    return Typography(
        // Estilo principal para los nombres de las aplicaciones (rejilla)
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = baseWeight,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        ),
        // Estilo para etiquetas más pequeñas o secundarias (cabeceras de sección)
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = if (baseWeight == FontWeight.Bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = baseWeight,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = baseWeight,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        )
    )
}
