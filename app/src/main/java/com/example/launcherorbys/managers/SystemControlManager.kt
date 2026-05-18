package com.example.launcherorbys.managers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Gestiona el control del hardware y configuraciones del sistema: brillo, volumen y conectividad.
 * Proporciona estados observables que la interfaz de Compose consume automáticamente.
 */
class SystemControlManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val contentResolver = context.contentResolver

    // --- Estados Observables (Reflejan el estado del sistema en la UI) ---
    var currentBrightness by mutableStateOf(0.5f)
    var isAutoBrightness by mutableStateOf(false)
    var isAirplaneModeOn by mutableStateOf(false)
    var isWifiOn by mutableStateOf(false)
    var isBluetoothOn by mutableStateOf(false)
    var isMuted by mutableStateOf(false)
    var currentVolume by mutableStateOf(0.5f)

    /**
     * Sincroniza los estados internos con los valores reales del sistema.
     * Se debe llamar al abrir paneles de control o tras recibir cambios de sistema.
     */
    fun actualizarValoresSistema() {
        // Sincronizar Volumen
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
        }
        
        isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC) 
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }

        // Sincronizar Brillo
        try {
            val brightnessValue = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = brightnessValue / 255f
            
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            isAutoBrightness = (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } catch (e: Exception) {
            // Falla si no hay permisos o el valor no existe
        }

        // Sincronizar Conectividad
        isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNet = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNet)
            isWifiOn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            
            // Nota: BluetoothAdapter.getDefaultAdapter() está deprecado en favor de BluetoothManager
            // pero mantenemos compatibilidad amplia aquí.
            isBluetoothOn = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) { }
    }

    /**
     * Cambia entre brillo automático y manual.
     */
    fun cambiarModoBrillo(auto: Boolean) {
        if (Settings.System.canWrite(context)) {
            val modo = if (auto) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC 
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            isAutoBrightness = auto
        } else {
            solicitarPermisoEscritura()
        }
    }

    /**
     * Ajusta el nivel de brillo (0.0f a 1.0f).
     */
    fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(context)) {
            val intBrillo = (valor * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, intBrillo)
            currentBrightness = valor
        } else {
            solicitarPermisoEscritura()
        }
    }

    /**
     * Ajusta el volumen del flujo de música (0.0f a 1.0f).
     */
    fun cambiarVolumen(valor: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (valor * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
        if (valor > 0f) isMuted = false
    }

    /**
     * Alterna el estado de silencio.
     */
    fun toggleMute() {
        isMuted = !isMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val action = if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, action, 0)
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
        }
    }

    // --- Métodos de apertura de Ajustes con Fallbacks ---

    /**
     * Abre una pantalla de ajustes específica del sistema.
     */
    fun launchSettings(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Intentamos forzar com.android.settings para evitar selectores de aplicaciones
            if (action == Settings.ACTION_SETTINGS) {
                setPackage("com.android.settings")
            }
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                intent.setPackage(null) // Reintentar sin paquete forzado
                context.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesFallback()
            }
        }
    }

    /**
     * Abre los ajustes de Bluetooth con redundancia de intents.
     */
    fun abrirAjustesBT() {
        val intents = listOf(
            Intent("android.settings.CONNECTED_DEVICE_SETTINGS"), // Android 10+
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)           // Tradicional
        )
        
        var success = false
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                success = true
                break
            } catch (e: Exception) {}
        }
        
        if (!success) {
            toast("No se encontró el menú de Bluetooth")
            abrirAjustesFallback()
        }
    }

    private fun abrirAjustesFallback() {
        try {
            val fallbackIntent = context.packageManager.getLaunchIntentForPackage("com.android.settings")
                ?: context.packageManager.getLaunchIntentForPackage("com.google.android.settings")
            
            if (fallbackIntent != null) {
                context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                toast("No se pudo abrir el menú de ajustes")
            }
        } catch (e: Exception) {
            toast("Error al abrir ajustes")
        }
    }

    private fun solicitarPermisoEscritura() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        toast("Concede permiso de escritura para cambiar el brillo")
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
}
