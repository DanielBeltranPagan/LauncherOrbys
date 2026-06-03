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
 * Controlador de hardware y configuraciones globales del sistema.
 * Expone estados observables de Compose para que la UI se actualice automáticamente.
 */
class SystemControlManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val contentResolver = context.contentResolver

    // --- Estados Observables del Sistema ---
    var currentBrightness by mutableStateOf(0.5f)
    var isAutoBrightness by mutableStateOf(false)
    var isAirplaneModeOn by mutableStateOf(false)
    var isWifiOn by mutableStateOf(false)
    var isBluetoothOn by mutableStateOf(false)
    var isMuted by mutableStateOf(false)
    var currentVolume by mutableStateOf(0.5f)

    /**
     * Sincroniza las propiedades observables con los valores reales del hardware.
     */
    fun actualizarValoresSistema() {
        actualizarVolumen()
        actualizarBrillo()
        actualizarConectividad()
    }

    private fun actualizarVolumen() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
        }
        isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC) 
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
    }

    private fun actualizarBrillo() {
        try {
            val brightnessValue = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = brightnessValue / 255f
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            isAutoBrightness = (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } catch (e: Exception) {}
    }

    private fun actualizarConectividad() {
        isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        
        // WiFi
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            isWifiOn = wm?.isWifiEnabled == true
        } catch (e: Exception) {
            isWifiOn = false
        }
        
        // Bluetooth - Manejo seguro de permisos
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bm?.adapter
            if (adapter != null) {
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                
                isBluetoothOn = if (hasPermission) {
                    adapter.isEnabled
                } else {
                    // Si no hay permiso, no podemos saberlo con certeza, devolvemos false para evitar crash
                    false
                }
            } else {
                isBluetoothOn = false
            }
        } catch (e: Exception) {
            isBluetoothOn = false
        }
    }

    /**
     * Intenta alternar el estado del Bluetooth.
     * En Android 13+ el toggle directo suele estar restringido, por lo que abrirá ajustes si falla.
     */
    fun toggleBluetooth() {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bm?.adapter ?: return

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return // OverlayManager detectará esto y pedirá permiso

        try {
            @Suppress("DEPRECATION")
            if (adapter.isEnabled) {
                adapter.disable()
            } else {
                adapter.enable()
            }
            // Actualizar estado local inmediatamente
            isBluetoothOn = adapter.isEnabled
        } catch (e: Exception) {
            // Si falla el toggle directo (restricciones de Android moderno), abrimos ajustes
            abrirAjustesBT()
        }
    }

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
     * Alterna entre brillo automático y manual.
     */
    fun cambiarModoBrillo(auto: Boolean) {
        if (Settings.System.canWrite(context)) {
            val modo = if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            isAutoBrightness = auto
        } else {
            solicitarPermisoEscritura()
        }
    }

    /**
     * Ajusta el volumen de la música.
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

    // --- Métodos de Apertura de Ajustes ---

    fun launchSettings(action: String) {
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            abrirAjustesFallback()
        }
    }

    fun abrirAjustesBT() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback 1: Pantalla de dispositivos conectados / conectividad
                val intent = Intent("android.settings.CONNECT_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesFallback()
            }
        }
    }

    fun abrirAjustesWifi() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesFallback()
            }
        }
    }

    private fun abrirAjustesFallback() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.android.settings")
        if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun solicitarPermisoEscritura() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        toast("Concede permiso para cambiar el brillo")
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
}
