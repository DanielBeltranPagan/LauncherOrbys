package com.example.launcherorbys.ui.setup

/**
 * Modelo de datos que representa un permiso requerido por la aplicación.
 */
data class PermissionItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onGrantClick: () -> Unit
)
