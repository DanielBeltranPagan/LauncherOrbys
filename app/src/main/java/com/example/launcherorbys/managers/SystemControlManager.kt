package com.example.launcherorbys.managers

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
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
        // Forzamos siempre la apertura de los ajustes para evitar el aviso de "120 segundos"
        // y porque en Android moderno el toggle directo falla casi siempre.
        abrirSwitchBarBT()
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
        val action = if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, action, 0)
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
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$BluetoothSettingsActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            abrirAjustesBT() // Llama a la opción 1 si rompe por temas de permisos
        }
    }

    /**
     * Abre el panel de control de Bluetooth (disponible desde Android 10+).
     * Si falla, intenta el panel de conectividad general antes de ir a ajustes completos.
     */
    fun abrirSwitchBarBT() {
        try {
            // Intentamos el panel flotante específico de Bluetooth (Android 10+)
            val intent = Intent("android.settings.panel.action.BLUETOOTH").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Si el panel de BT no existe, intentamos el de conectividad (Android 12+)
                val intent = Intent("android.settings.panel.action.INTERNET_CONNECTIVITY").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Si nada funciona, vamos a la pantalla de ajustes con los extras de fragmento
                abrirAjustesBT()
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
        // Mostramos un mensaje genérico o usamos el contexto para obtener el string
        try {
            val msg = context.getString(com.example.launcherorbys.R.string.permission_system_settings_desc)
            toast(msg)
        } catch (_: Exception) {
            toast("Concede permiso para cambiar el brillo")
        }
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
}
