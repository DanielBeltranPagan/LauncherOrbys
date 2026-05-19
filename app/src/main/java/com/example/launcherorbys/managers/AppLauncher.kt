package com.example.launcherorbys.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast

/**
 * Gestor centralizado para el lanzamiento de aplicaciones y utilidades externas.
 * Evita la dispersión de Intents de apertura por toda la interfaz de usuario.
 */
class AppLauncher(private val context: Context) {

    /**
     * Abre un sitio web en el navegador del sistema.
     */
    fun abrirUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            toast("No hay navegador instalado")
        }
    }

    /**
     * Abre el explorador de archivos del sistema buscando los paquetes más comunes.
     */
    fun abrirAppArchivos() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
            ?: context.packageManager.getLaunchIntentForPackage("com.android.documentsui")
            ?: Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android.cursor.dir/file"
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                toast("No se encontró explorador de archivos")
            }
        }
    }

    /**
     * Envía una señal interna para que MainActivity inicie el flujo de grabación de pantalla.
     */
    fun iniciarGrabacionEstandar() {
        val intent = Intent("com.example.launcherorbys.START_SCREEN_RECORD").apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Intenta abrir el grabador de pantalla nativo del sistema.
     */
    fun abrirGrabadorPantalla(): Boolean {
        val intents = listOf(
            Intent().setClassName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordDialog"),
            Intent().setClassName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordActivity"),
            Intent("com.android.systemui.screenrecord.START")
        )

        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    /**
     * Intenta abrir la aplicación de alarma/reloj del sistema.
     */
    fun abrirRelojSistema(): Boolean {
        val paquetes = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.huawei.deskclock"
        )

        for (pkg in paquetes) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (e: Exception) {}
            }
        }

        return try {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
}
