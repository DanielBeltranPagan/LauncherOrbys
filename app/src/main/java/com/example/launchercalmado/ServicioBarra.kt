package com.example.launchercalmado

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launchercalmado.ui.components.NavBar
import com.example.launchercalmado.ui.components.SystemOptionsPanel
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class ServicioBarra : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaSystemOptions: ComposeView

    // --- Estados reactivos para la UI ---
    private var systemOptionsVisible by mutableStateOf(false)
    private var currentBrightness by mutableStateOf(0.5f)
    private var isAutoBrightness by mutableStateOf(false)
    private var isAirplaneModeOn by mutableStateOf(false)
    private var currentVolume by mutableStateOf(0.5f)
    private lateinit var audioManager: AudioManager

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val density = resources.displayMetrics.density

        // Preparamos el overlay de opciones del sistema antes de mostrarlo
        setupSystemOptionsOverlay()
        
        // Configuración de la barra de navegación (tamaño, tipo de ventana y posición)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (45 * density).toInt(), // Altura de la barra
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Overlay sobre otras apps
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ServicioBarra)
            setViewTreeSavedStateRegistryOwner(this@ServicioBarra)

            // Evitamos que la barra consuma los insets del sistema (como el teclado)
            try {
                val method = javaClass.getMethod("setConsumeWindowInsets", Boolean::class.javaPrimitiveType)
                method.invoke(this, false)
            } catch (e: Exception) {}

            setPadding(0, 0, 0, 0)

            setContent {
                LauncherCalmadoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(onActionClicked = { accion ->
                            when (accion) {
                                "GOOGLE" -> {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { startActivity(intent) } catch (e: Exception) {}
                                }
                                "FILES" -> {
                                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                                        ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { startActivity(intent) } catch (e: Exception) {}
                                }
                                "SYSTEM_OPTIONS" -> toggleSystemOptions() // Abre/Cierra el panel
                            }
                            // Notifica a otros componentes del sistema sobre la acción
                            sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            sendBroadcast(Intent("COMANDO_SISTEMA").putExtra("comando", accion))
                        })
                    }
                }
            }
        }
        // Añadimos la vista de la barra a la pantalla
        windowManager.addView(vistaNav, params)
    }

    /**
     * Alterna la visibilidad del panel de opciones (Brillo, Volumen, Wi-Fi, etc.)
     */
    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            actualizarValoresSistema() // Lee el brillo/volumen real antes de mostrar el panel
            systemOptionsVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(vistaSystemOptions, params)
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) {
                windowManager.removeView(vistaSystemOptions)
            }
            systemOptionsVisible = false
        }
    }

    /**
     * Define el diseño y comportamiento del panel de opciones (Overlay de pantalla completa)
     */
    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ServicioBarra)
            setViewTreeSavedStateRegistryOwner(this@ServicioBarra)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Cierra el panel si se toca fuera del recuadro central
                            .clickable(interactionSource = null, indication = null) { toggleSystemOptions() },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        SystemOptionsPanel(
                            onSettingsClick = {
                                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onWifiClick = {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onBluetoothClick = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onAirplaneModeClick = { toggleAirplaneMode() },
                            isAirplaneModeOn = isAirplaneModeOn,
                            currentBrightness = currentBrightness,
                            onBrightnessChange = { cambiarBrillo(it) },
                            isAutoBrightness = isAutoBrightness,
                            onAutoBrightnessChange = { cambiarModoBrillo(it) },
                            currentVolume = currentVolume,
                            onVolumeChange = { cambiarVolumen(it) },
                            modifier = Modifier
                                .padding(bottom = 60.dp)
                                .clickable(interactionSource = null, indication = null) { /* Evita que el click pase al Box de abajo */ }
                        )
                    }
                }
            }
        }
    }

    /**
     * Obtiene los niveles actuales de volumen y brillo del sistema
     */
    private fun actualizarValoresSistema() {
        // Obtener Volumen Multimedia (0.0 a 1.0)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = curVol.toFloat() / maxVol
        }

        // Obtener Brillo de Pantalla (0.0 a 1.0)
        try {
            val curBright = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = curBright.toFloat() / 255f
            
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            isAutoBrightness = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            currentBrightness = 0.5f
            isAutoBrightness = false
        }

        // Obtener estado del Modo Avión
        try {
            isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            isAirplaneModeOn = false
        }
    }

    /**
     * Alterna el modo avión si tiene permisos, o abre los ajustes
     */
    private fun toggleAirplaneMode() {
        val nuevoEstado = if (isAirplaneModeOn) 0 else 1
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, nuevoEstado)
            isAirplaneModeOn = nuevoEstado != 0
        } catch (e: SecurityException) {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            toggleSystemOptions()
        }
    }

    /**
     * Alterna entre brillo automático y manual
     */
    private fun cambiarModoBrillo(auto: Boolean) {
        if (Settings.System.canWrite(this)) {
            val modo = if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            isAutoBrightness = auto
        } else {
            solicitarPermisoEscritura()
        }
    }

    private fun solicitarPermisoEscritura() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Toast.makeText(this, "Concede permiso para cambiar los ajustes", Toast.LENGTH_SHORT).show()
    }

    /**
     * Aplica el nuevo volumen al flujo de música
     */
    private fun cambiarVolumen(valor: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (valor * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
    }

    /**
     * Aplica el nuevo brillo a la pantalla. Requiere permiso de escritura de ajustes.
     */
    private fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(this)) {
            val newBright = (valor * 255).toInt()
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBright)
            currentBrightness = valor
        } else {
            // Si no tiene permiso, abre la pantalla de ajustes del sistema para concederlo
            solicitarPermisoEscritura()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpieza de vistas al detener el servicio
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
    }
}
