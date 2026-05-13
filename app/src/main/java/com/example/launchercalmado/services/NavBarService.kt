package com.example.launchercalmado.services

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
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

/**
 * NavBarService: Servicio encargado de gestionar y mostrar la barra de navegación personalizada
 * y el panel de ajustes rápidos (SystemOptionsPanel) mediante overlays de WindowManager.
 * Funciona de manera persistente sobre otras aplicaciones.
 */
class NavBarService : LifecycleService(), SavedStateRegistryOwner {

    // Gestores de sistema para manejar ventanas y audio
    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaSystemOptions: ComposeView

    // Estados reactivos para la UI del panel de opciones
    private var systemOptionsVisible by mutableStateOf(false)
    private var currentBrightness by mutableStateOf(0.5f)
    private var isAutoBrightness by mutableStateOf(false)
    private var isAirplaneModeOn by mutableStateOf(false)
    private var isWifiOn by mutableStateOf(false)
    private var isBluetoothOn by mutableStateOf(false)
    private var isMuted by mutableStateOf(false)
    private var currentVolume by mutableStateOf(0.5f)
    private lateinit var audioManager: AudioManager

    // Implementación necesaria para usar Compose dentro de un Service
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        // Restaurar estado si es necesario
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        // Inicialización de servicios del sistema
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val density = resources.displayMetrics.density

        // Configurar la vista del panel de opciones (se añade/quita dinámicamente)
        setupSystemOptionsOverlay()
        
        // Configuración de parámetros para la barra de navegación (overlay inferior)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (45 * density).toInt(), // Altura de 45dp
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        // Creación de la vista Compose para la barra de navegación
        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NavBarService)
            setViewTreeSavedStateRegistryOwner(this@NavBarService)

            // Evitar que la barra consuma insets para que se dibuje correctamente
            try {
                val method = javaClass.getMethod("setConsumeWindowInsets", Boolean::class.javaPrimitiveType)
                method.invoke(this, false)
            } catch (e: Exception) {}

            setPadding(0, 0, 0, 0)

            setContent {
                LauncherCalmadoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(onActionClicked = { accion ->
                            // Lógica para cada botón de la barra
                            when (accion) {
                                "GOOGLE" -> {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { startActivity(intent) } catch (e: Exception) {}
                                }
                                "FILES" -> {
                                    // Abrir el explorador de archivos predeterminado
                                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                                        ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { startActivity(intent) } catch (e: Exception) {}
                                }
                                "SYSTEM_OPTIONS" -> toggleSystemOptions() // Mostrar/Ocultar panel
                            }
                            // Notificar acciones mediante broadcast si otros componentes lo necesitan
                            sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            sendBroadcast(Intent("COMANDO_SISTEMA").putExtra("comando", accion))
                        })
                    }
                }
            }
        }
        // Añadir la barra a la pantalla
        windowManager.addView(vistaNav, params)
    }

    /**
     * Muestra u oculta el panel de opciones del sistema.
     * Actualiza los valores actuales (brillo, volumen) antes de mostrarlo.
     */
    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            actualizarValoresSistema()
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
     * Prepara la vista del panel de opciones con Compose.
     */
    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NavBarService)
            setViewTreeSavedStateRegistryOwner(this@NavBarService)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { toggleSystemOptions() }, // Cerrar al tocar fuera
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
                                val intent = Intent("android.settings.CONNECTED_DEVICE_SETTINGS").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                toggleSystemOptions()
                            },
                            onMuteClick = { toggleMute() },
                            onPowerClick = {
                                Toast.makeText(this@NavBarService, "Usa el botón físico para apagar", Toast.LENGTH_SHORT).show()
                                toggleSystemOptions()
                            },
                            onScreenshotClick = {
                                Toast.makeText(this@NavBarService, "Función solo disponible en modo persistente", Toast.LENGTH_SHORT).show()
                                toggleSystemOptions()
                            },
                            onRecordClick = {
                                if (!abrirGrabadorPantalla()) {
                                    Toast.makeText(this@NavBarService, "Grabador no disponible", Toast.LENGTH_SHORT).show()
                                }
                                toggleSystemOptions()
                            },
                            isWifiOn = isWifiOn,
                            isBluetoothOn = isBluetoothOn,
                            isMuted = isMuted,
                            currentBrightness = currentBrightness,
                            onBrightnessChange = { cambiarBrillo(it) },
                            isAutoBrightness = isAutoBrightness,
                            onAutoBrightnessChange = { cambiarModoBrillo(it) },
                            currentVolume = currentVolume,
                            onVolumeChange = { cambiarVolumen(it) },
                            modifier = Modifier
                                .padding(bottom = 60.dp)
                                .clickable(interactionSource = null, indication = null) { /* Evitar que el clic cierre el panel */ }
                        )
                    }
                }
            }
        }
    }

    private fun abrirGrabadorPantalla(): Boolean {
        val intents = arrayOf(
            Intent().setComponent(android.content.ComponentName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordDialog")),
            Intent("com.android.systemui.screenrecord.START"),
            Intent().setComponent(android.content.ComponentName("com.samsung.android.app.screenrecorder", "com.samsung.android.app.screenrecorder.ScreenRecorderService")),
            Intent().setComponent(android.content.ComponentName("com.miui.screenrecorder", "com.miui.screenrecorder.ActivityMain"))
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    /**
     * Lee el estado actual del sistema (volumen, brillo, modo avión) para sincronizar la UI.
     */
    private fun actualizarValoresSistema() {
        // Obtener volumen actual
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = curVol.toFloat() / maxVol
        }

        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)

        // Obtener brillo y modo automático
        try {
            val curBright = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = curBright.toFloat() / 255f
            
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            isAutoBrightness = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            currentBrightness = 0.5f
            isAutoBrightness = false
        }

        // Obtener estado del modo avión
        try {
            isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            isAirplaneModeOn = false
        }

        // Obtener estado de Wi-Fi y Bluetooth
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            isWifiOn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            isWifiOn = false
        }

        try {
            val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            isBluetoothOn = btAdapter?.isEnabled == true
        } catch (e: Exception) {
            isBluetoothOn = false
        }
    }

    /**
     * Intenta cambiar el modo avión. Debido a restricciones de Android moderno,
     * si falla (SecurityException) redirige a los ajustes del sistema.
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
     * Cambia entre brillo automático y manual. Requiere permiso de escritura de ajustes.
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

    /**
     * Muestra la pantalla de configuración para otorgar permisos de escritura de ajustes de sistema.
     */
    private fun solicitarPermisoEscritura() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Toast.makeText(this, "Concede permiso para cambiar los ajustes", Toast.LENGTH_SHORT).show()
    }

    /**
     * Ajusta el volumen del flujo de música.
     */
    private fun cambiarVolumen(valor: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (valor * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
        if (valor > 0f) isMuted = false
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    /**
     * Ajusta el nivel de brillo de la pantalla. Requiere permiso de escritura.
     */
    private fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(this)) {
            val newBright = (valor * 255).toInt()
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBright)
            currentBrightness = valor
        } else {
            solicitarPermisoEscritura()
        }
    }

    /**
     * Limpieza de vistas al destruir el servicio para evitar fugas de memoria o overlays huérfanos.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
    }
}
