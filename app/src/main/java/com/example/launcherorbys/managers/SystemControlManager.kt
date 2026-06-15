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
 * 
 * Gestiona el volumen, brillo, conectividad (WiFi/Bluetooth) y acceso a paneles de ajustes.
 * 
 * @param contexto El contexto de la aplicación, utilizado para acceder a servicios del sistema y lanzar Intents.
 */
class SystemControlManager(private val contexto: Context) {
    
    private val gestorAudio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val resolvedorContenido = contexto.contentResolver

    // --- Estados Observables del Sistema ---
    /** Nivel de brillo actual normalizado (0.0f a 1.0f). */
    var brilloActual by mutableStateOf(0.5f)
    /** Indica si el brillo automático está habilitado en el sistema. */
    var esBrilloAutomatico by mutableStateOf(false)
    /** Indica si el WiFi está habilitado actualmente. */
    var estaWifiActivado by mutableStateOf(false)
    /** Indica si el Bluetooth está habilitado actualmente. */
    var estaBluetoothActivado by mutableStateOf(false)
    /** Indica si la salida de audio multimedia está silenciada. */
    var estaSilenciado by mutableStateOf(false)
    /** Nivel de volumen actual normalizado (0.0f a 1.0f). */
    var volumenActual by mutableStateOf(0.5f)

    /**
     * Sincroniza las propiedades observables con los valores reales del hardware.
     * Lee el estado actual de volumen, brillo y conectividad.
     */
    fun actualizarValoresSistema() {
        actualizarVolumen()
        actualizarBrillo()
        actualizarConectividad()
    }

    private fun actualizarVolumen() {
        val volMax = gestorAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volMax > 0) {
            volumenActual = gestorAudio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / volMax
        }
        estaSilenciado = gestorAudio.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    private fun actualizarBrillo() {
        try {
            val valorBrillo = Settings.System.getInt(resolvedorContenido, Settings.System.SCREEN_BRIGHTNESS)
            brilloActual = valorBrillo / 255f
            val modo = Settings.System.getInt(resolvedorContenido, Settings.System.SCREEN_BRIGHTNESS_MODE)
            esBrilloAutomatico = (modo == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } catch (e: Exception) {}
    }

    private fun actualizarConectividad() {
        // WiFi
        try {
            val wm = contexto.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            estaWifiActivado = wm?.isWifiEnabled == true
        } catch (e: Exception) {
            estaWifiActivado = false
        }
        
        // Bluetooth
        try {
            val bm = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adaptador = bm?.adapter
            if (adaptador != null) {
                val tienePermiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    contexto.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                
                estaBluetoothActivado = if (tienePermiso) {
                    adaptador.isEnabled
                } else {
                    false
                }
            } else {
                estaBluetoothActivado = false
            }
        } catch (e: Exception) {
            estaBluetoothActivado = false
        }
    }

    fun alternarBluetooth() {
        abrirPanelBluetooth()
    }

    fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(contexto)) {
            val intBrillo = (valor * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(resolvedorContenido, Settings.System.SCREEN_BRIGHTNESS, intBrillo)
            brilloActual = valor
        } else {
            solicitarPermisoEscritura()
        }
    }

    fun cambiarModoBrillo(automatico: Boolean) {
        if (Settings.System.canWrite(contexto)) {
            val modo = if (automatico) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(resolvedorContenido, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            esBrilloAutomatico = automatico
        } else {
            solicitarPermisoEscritura()
        }
    }

    fun cambiarVolumen(valor: Float) {
        val volMax = gestorAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val nuevoVol = (valor * volMax).toInt().coerceIn(0, volMax)
        gestorAudio.setStreamVolume(AudioManager.STREAM_MUSIC, nuevoVol, 0)
        volumenActual = valor
        if (valor > 0f) estaSilenciado = false
    }

    fun alternarSilencio() {
        estaSilenciado = !estaSilenciado
        val accion = if (estaSilenciado) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        gestorAudio.adjustStreamVolume(AudioManager.STREAM_MUSIC, accion, 0)
    }

    fun lanzarAjustes(accion: String) {
        try {
            val intent = Intent(accion).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            contexto.startActivity(intent)
        } catch (e: Exception) {
            abrirAjustesFallback()
        }
    }

    fun abrirAjustesBluetooth() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$BluetoothSettingsActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            contexto.startActivity(intent)
        } catch (e: Exception) {
            // Intentar abrir ajustes generales de BT
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                contexto.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesFallback()
            }
        }
    }

    fun abrirPanelBluetooth() {
        try {
            val intent = Intent("android.settings.panel.action.BLUETOOTH").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            contexto.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("android.settings.panel.action.INTERNET_CONNECTIVITY").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesBluetooth()
            }
        }
    }

    fun abrirAjustesWifi() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            contexto.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
            } catch (e2: Exception) {
                abrirAjustesFallback()
            }
        }
    }

    private fun abrirAjustesFallback() {
        val intent = contexto.packageManager.getLaunchIntentForPackage("com.android.settings")
        if (intent != null) contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun solicitarPermisoEscritura() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${contexto.packageName}"))
        contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val mensaje = contexto.getString(com.example.launcherorbys.R.string.permission_system_settings_desc)
            mostrarMensaje(mensaje)
        } catch (_: Exception) {
            mostrarMensaje("Concede permiso para cambiar el brillo")
        }
    }

    private fun mostrarMensaje(m: String) = Toast.makeText(contexto, m, Toast.LENGTH_SHORT).show()
}
