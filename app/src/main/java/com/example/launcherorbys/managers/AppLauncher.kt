package com.example.launcherorbys.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast

/**
 * Gestor centralizado para el lanzamiento de aplicaciones y utilidades externas.
 *
 * Esta clase centraliza la creación de [Intent] para abrir aplicaciones instaladas,
 * sitios web, el explorador de archivos y el reloj del sistema. Al usar esta clase,
 * se asegura una gestión consistente de flags como [Intent.FLAG_ACTIVITY_NEW_TASK]
 * y un manejo de errores (fallbacks) unificado.
 *
 * @property contexto El contexto de la aplicación necesario para interactuar con el PackageManager y lanzar actividades.
 */
class AppLauncher(private val contexto: Context) {

    /**
     * Lanza una aplicación instalada utilizando su nombre de paquete único.
     *
     * Si el paquete no tiene una actividad de inicio (Launcher intent) o no está instalado,
     * se muestra un mensaje informativo al usuario mediante un Toast.
     *
     * @param nombrePaquete El ID del paquete de la aplicación (ej: "com.android.settings").
     */
    fun lanzarApp(nombrePaquete: String) {
        val intent = contexto.packageManager.getLaunchIntentForPackage(nombrePaquete)
        if (intent != null) {
            try {
                // Se añade NO_ANIMATION para una transición instantánea típica de launchers ligeros
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                contexto.startActivity(intent)
            } catch (e: Exception) {
                mostrarMensaje("No se pudo abrir la aplicación")
            }
        } else {
            mostrarMensaje("Aplicación no encontrada")
        }
    }

    /**
     * Abre un sitio web o enlace profundo en el navegador predeterminado del sistema.
     *
     * @param url La dirección web completa (debe incluir el esquema, ej: "https://...").
     */
    fun abrirUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            contexto.startActivity(intent)
        } catch (e: Exception) {
            mostrarMensaje("No hay navegador instalado")
        }
    }

    /**
     * Intenta abrir el explorador de archivos del sistema.
     *
     * Realiza una búsqueda secuencial de paquetes comunes (`documentsui`) y, en caso de fallo,
     * utiliza un Intent genérico [Intent.ACTION_GET_CONTENT] como último recurso.
     */
    fun abrirAppArchivos() {
        val intent = contexto.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
            ?: contexto.packageManager.getLaunchIntentForPackage("com.android.documentsui")
            ?: Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android.cursor.dir/file"
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        try {
            contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(fallback)
            } catch (e2: Exception) {
                mostrarMensaje("No se encontró explorador de archivos")
            }
        }
    }

    /**
     * Intenta abrir la aplicación de alarma o reloj del sistema buscando entre fabricantes conocidos.
     *
     * Recorre una lista de paquetes habituales (Google, Samsung, Huawei) y si ninguno existe,
     * utiliza la acción estándar [AlarmClock.ACTION_SHOW_ALARMS].
     *
     * @return `true` si se logró lanzar alguna actividad de reloj; `false` si no fue posible.
     */
    fun abrirRelojSistema(): Boolean {
        val paquetesReloj = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.huawei.deskclock"
        )

        for (paquete in paquetesReloj) {
            val intent = contexto.packageManager.getLaunchIntentForPackage(paquete)
            if (intent != null) {
                try {
                    contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (e: Exception) {}
            }
        }

        return try {
            contexto.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mostrarMensaje(mensaje: String) = Toast.makeText(contexto, mensaje, Toast.LENGTH_SHORT).show()
}
