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
 * Gestor centralizado para la comprobación de permisos críticos del sistema.
 * Proporciona métodos para verificar el estado de los permisos necesarios para el funcionamiento del Launcher.
 */
class PermissionManager(private val context: Context) {

    /**
     * Comprueba si esta aplicación es el Launcher predeterminado del sistema.
     */
    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Comprueba si el servicio de accesibilidad está activado de forma robusta.
     */
    fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == LauncherAccessibilityService::class.java.name
        }
    }

    /**
     * Comprueba si la aplicación tiene permiso para escribir ajustes del sistema (Brillo, etc).
     */
    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Comprueba si se tiene el permiso de Bluetooth (necesario en Android 12+).
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
     * Comprueba si se tiene permiso para grabar audio.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
