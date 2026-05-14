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
 * Gestiona el control de hardware del sistema: brillo, volumen y conectividad.
 */
class SystemControlManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val contentResolver = context.contentResolver

    // Estados observables para la UI de Compose
    var currentBrightness by mutableStateOf(0.5f)
    var isAutoBrightness by mutableStateOf(false)
    var isAirplaneModeOn by mutableStateOf(false)
    var isWifiOn by mutableStateOf(false)
    var isBluetoothOn by mutableStateOf(false)
    var isMuted by mutableStateOf(false)
    var currentVolume by mutableStateOf(0.5f)

    /**
     * Sincroniza los estados internos con los valores reales del sistema.
     */
    fun actualizarValoresSistema() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
        }
        isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioManager.isStreamMute(AudioManager.STREAM_MUSIC) 
                  else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

        try {
            currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            isAutoBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) { }

        isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            isWifiOn = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isBluetoothOn = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) { }
    }

    fun cambiarModoBrillo(auto: Boolean) {
        if (Settings.System.canWrite(context)) {
            val modo = if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            isAutoBrightness = auto
        } else solicitarPermisoEscritura()
    }

    private fun solicitarPermisoEscritura() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(context, "Concede permiso de escritura", Toast.LENGTH_SHORT).show()
    }

    fun cambiarVolumen(valor: Float) {
        val newVol = (valor * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
        if (valor > 0f) isMuted = false
    }

    fun toggleMute() {
        isMuted = !isMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
        }
    }

    fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(context)) {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (valor * 255).toInt())
            currentBrightness = valor
        } else solicitarPermisoEscritura()
    }

    // --- Métodos de apertura de Ajustes ---

    fun launchSettings(action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (e: Exception) {}
    }

    fun abrirAjustesBT() {
        val intents = listOf(
            Intent("android.settings.CONNECTED_DEVICE_SETTINGS"),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        var success = false
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                success = true
                break
            } catch (e: Exception) {}
        }
        if (!success) Toast.makeText(context, "No se encontró el menú de Bluetooth", Toast.LENGTH_SHORT).show()
    }
}
