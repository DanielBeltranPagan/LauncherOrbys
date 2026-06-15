package com.example.launcherorbys.managers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.launcherorbys.services.LauncherAccessibilityService

/**
 * Gestor centralizado para la comprobación y validación de permisos críticos del sistema.
 *
 * Esta clase encapsula la lógica necesaria para verificar si el Launcher tiene las
 * autorizaciones requeridas para realizar tareas sensibles, como ser el lanzador
 * predeterminado, escribir configuraciones del sistema o capturar audio.
 *
 * @property context El contexto de la aplicación utilizado para consultar servicios del sistema y el PackageManager.
 */
class PermissionManager(private val context: Context) {

    /**
     * Determina si esta aplicación está configurada actualmente como el Launcher predeterminado del sistema.
     *
     * @return `true` si el paquete de la actividad que resuelve [Intent.ACTION_MAIN] con
     *         [Intent.CATEGORY_HOME] coincide con el de esta aplicación; `false` en caso contrario.
     */
    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Verifica si el [LauncherAccessibilityService] de la aplicación está habilitado y activo.
     *
     * La comprobación se realiza consultando tanto el AccessibilityManager como los ajustes
     * seguros del sistema para mayor robustez, especialmente tras actualizaciones de la app.
     *
     * @return `true` si el servicio está activado en los ajustes; `false` en caso contrario.
     */
    fun isAccessibilityEnabled(): Boolean {
        // 1. Intento rápido con el AccessibilityManager
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        val isEnabledByManager = enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == LauncherAccessibilityService::class.java.name
        }
        
        if (isEnabledByManager) return true

        // 2. Fallback: Comprobación directa en Settings.Secure
        // Útil cuando el servicio está habilitado pero el sistema aún no lo ha reiniciado tras una actualización.
        try {
            val expectedService = ComponentName(context, LauncherAccessibilityService::class.java).flattenToString()
            val enabledSettings = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (enabledSettings != null) {
                val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledSettings)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(expectedService, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Si falla la lectura de ajustes, nos fiamos solo del manager
        }

        return false
    }

    /**
     * Comprueba si la aplicación tiene permiso para modificar los ajustes del sistema.
     *
     * Este permiso es especial y permite cambiar el brillo de la pantalla, el tiempo de espera, etc.
     *
     * @return `true` si el permiso ha sido concedido mediante la pantalla de configuración del sistema.
     */
    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Verifica si se dispone de los permisos necesarios para interactuar con dispositivos Bluetooth.
     *
     * En versiones inferiores a Android 12 (API 31), este método siempre devuelve `true`
     * si el permiso está declarado en el manifiesto. En Android 12+, verifica el permiso
     * dinámico `BLUETOOTH_CONNECT`.
     *
     * @return `true` si se tiene permiso para conectar con dispositivos Bluetooth.
     */
    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Verifica si el usuario ha concedido permiso para grabar audio (micrófono).
     *
     * Requerido principalmente para la funcionalidad de grabación de pantalla con audio.
     *
     * @return `true` si el permiso `RECORD_AUDIO` está concedido.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
