package com.example.launcherorbys.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast

/**
 * Gestiona la apertura de aplicaciones externas, utilidades del sistema y navegación web.
 * Centraliza los intents de lanzamiento para mantener el código de los gestores de UI limpio.
 */
class AppLauncher(private val context: Context) {

    /**
     * Abre una URL en el navegador predeterminado.
     */
    fun abrirUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            toast("No hay una aplicación para abrir este enlace")
        }
    }

    /**
     * Intenta abrir el explorador de archivos del sistema.
     */
    fun abrirAppArchivos() {
        // Intentar abrir el explorador nativo de Google o el genérico de Android
        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
            ?: context.packageManager.getLaunchIntentForPackage("com.android.documentsui")
            ?: Intent(Intent.ACTION_VIEW).apply { 
                type = "vnd.android.cursor.dir/file" 
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Último recurso: Selector de contenido genérico
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { 
                    type = "*/*" 
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e: Exception) {
                toast("No se encontró un explorador de archivos")
            }
        }
    }

    /**
     * Intenta abrir el grabador de pantalla nativo buscando en paquetes comunes de fabricantes.
     * @return true si se logró lanzar alguna actividad.
     */
    fun abrirGrabadorPantalla(): Boolean {
        val intents = listOf(
            // Diálogo estándar de Android 11+
            Intent().setClassName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordDialog"),
            // Samsung
            context.packageManager.getLaunchIntentForPackage("com.samsung.android.app.screenrecorder"),
            // Xiaomi/MIUI
            context.packageManager.getLaunchIntentForPackage("com.miui.screenrecorder"),
            // Oppo/Realme
            context.packageManager.getLaunchIntentForPackage("com.coloros.screenrecorder"),
            // OneUI Samsung shortcut
            Intent("com.samsung.android.app.screenrecorder.QuickPanelScreenRecorder")
        )

        for (intent in intents) {
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    // Continuar probando el siguiente si falla
                }
            }
        }
        return false
    }

    /**
     * Intenta abrir la aplicación de reloj/alarmas buscando paquetes conocidos.
     * @return true si se encontró y abrió alguna app.
     */
    fun abrirRelojSistema(): Boolean {
        val paquetesReloj = listOf(
            "com.google.android.deskclock",       // Google / Pixel
            "com.android.deskclock",              // AOSP
            "com.sec.android.app.clockpackage",   // Samsung
            "com.huawei.deskclock",               // Huawei
            "com.coloros.alarmclock",             // Oppo
            "com.miui.calculator"                 // Xiaomi (a veces integrado)
        )

        for (pkg in paquetesReloj) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (e: Exception) { }
            }
        }

        // Fallback usando la acción estándar de alarmas
        return try {
            val intentAlarma = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intentAlarma)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
}
