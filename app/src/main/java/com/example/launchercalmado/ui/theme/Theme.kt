package com.example.launchercalmado.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

@Composable
fun LauncherCalmadoTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            // FIX: Solo hacemos el cast a Activity si realmente lo es
            if (context is Activity) {
                val window = context.window
                window.setWindowAnimations(0) // Cero animaciones
                window.statusBarColor = Color(0xFF1C1C1C).toArgb()
                window.navigationBarColor = Color(0xFF1C1C1C).toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = Color.Cyan),
        content = content
    )
}