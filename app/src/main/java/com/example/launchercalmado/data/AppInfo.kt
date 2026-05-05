package com.example.launchercalmado.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,       // Nombre de la app
    val packageName: String, // El ID interno
    val icon: Drawable?      // El dibujo del icono
)