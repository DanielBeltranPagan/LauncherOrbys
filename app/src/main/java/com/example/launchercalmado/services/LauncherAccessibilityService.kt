package com.example.launchercalmado.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launchercalmado.ui.components.AppDrawer
import com.example.launchercalmado.ui.components.NavBar
import com.example.launchercalmado.ui.components.StatusBar
import com.example.launchercalmado.ui.components.SystemOptionsPanel
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

/**
 * Servicio de Accesibilidad que actúa como el motor visual persistente del launcher.
 * Permite superponer la barra de navegación, el cajón de apps y los ajustes rápidos
 * sobre cualquier otra aplicación.
 */
class LauncherAccessibilityService : AccessibilityService(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    
    // Estados para controlar la apariencia de las barras
    private var iconColorNav by mutableStateOf(Color.White)
    private var navBarBackground by mutableStateOf(Color.Black)

    // Estados de visibilidad de los paneles
    private var drawerVisible by mutableStateOf(false)
    private var systemOptionsVisible by mutableStateOf(false)
    
    // Estados de ajustes del sistema
    private var currentBrightness by mutableStateOf(0.5f)
    private var isAutoBrightness by mutableStateOf(false)
    private var isAirplaneModeOn by mutableStateOf(false)
    private var isWifiOn by mutableStateOf(false)
    private var isBluetoothOn by mutableStateOf(false)
    private var isMuted by mutableStateOf(false)
    private var currentVolume by mutableStateOf(0.5f)
    private lateinit var audioManager: AudioManager

    // Boilerplate necesario para usar Compose dentro de un Servicio
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    // Escucha comandos para realizar acciones globales o cambiar el tema
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CAMBIO_TEMA" -> {
                    val esClaro = intent.getBooleanExtra("esClaro", true)
                    actualizarColores(esClaro)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                    isBluetoothOn = state == android.bluetooth.BluetoothAdapter.STATE_ON
                }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> {
                    actualizarValoresSistema()
                }
                else -> manejarComando(intent?.getStringExtra("comando"))
            }
        }
    }

    /**
     * Procesa los comandos recibidos (normalmente desde la NavBar).
     */
    private fun manejarComando(comando: String?) {
        when (comando) {
            "BACK" -> {
                if (drawerVisible) toggleDrawer()
                else performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "HOME" -> {
                if (drawerVisible) toggleDrawer()
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Forzamos la vuelta al Home de nuestro launcher
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
            }
            "RECENTS" -> {
                if (drawerVisible) toggleDrawer()
                if (systemOptionsVisible) toggleSystemOptions()
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "APPS" -> toggleDrawer()
            "SYSTEM_OPTIONS" -> toggleSystemOptions()
        }
    }

    /**
     * Muestra u oculta el panel de ajustes rápidos (brillo, volumen, etc.).
     */
    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            actualizarValoresSistema() // Refresca los valores antes de mostrar
            systemOptionsVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
     * Obtiene los valores actuales del sistema para sincronizar los Sliders.
     */
    private fun actualizarValoresSistema() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = curVol.toFloat() / maxVol
        }
        
        // Verificar si está silenciado
        isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }

        try {
            val curBright = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = curBright.toFloat() / 255f
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            isAutoBrightness = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            currentBrightness = 0.5f
            isAutoBrightness = false
        }

        try {
            isAirplaneModeOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            isAirplaneModeOn = false
        }

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

    // --- Métodos de gestión del sistema (brillo, volumen, modo avión) ---

    private fun toggleAirplaneMode() {
        val nuevoEstado = if (isAirplaneModeOn) 0 else 1
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, nuevoEstado)
            isAirplaneModeOn = nuevoEstado != 0
        } catch (e: SecurityException) {
            // Si no tenemos permiso directo, abrimos los ajustes
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            toggleSystemOptions()
        }
    }

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

    private fun cambiarVolumen(valor: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (valor * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
        if (valor > 0f) isMuted = false
    }

    private fun toggleMute() {
        isMuted = !isMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, isMuted)
        }
    }

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
     * Muestra u oculta el cajón de aplicaciones.
     */
    private fun toggleDrawer() {
        if (!drawerVisible) {
            drawerVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(vistaDrawer, params)
        } else {
            if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) {
                windowManager.removeView(vistaDrawer)
            }
            drawerVisible = false
        }
    }

    /**
     * Sincroniza los colores de las barras con el tema actual.
     */
    private fun actualizarColores(esClaro: Boolean) {
        if (esClaro) {
            navBarBackground = Color.Black
            iconColorNav = Color.White
        } else {
            navBarBackground = Color.White
            iconColorNav = Color.Black
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        // Carga el tema inicial desde las preferencias
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val esClaro = prefs.getBoolean("esClaro", true)
        actualizarColores(esClaro)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val density = resources.displayMetrics.density

        // Configuración de las distintas vistas superpuestas
        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion(density)
        registrarReceptores()
    }

    // --- Métodos de configuración de las vistas de Compose ---

    private fun setupDrawerOverlay() {
        vistaDrawer = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { toggleDrawer() },
                        contentAlignment = Alignment.Center
                    ) {
                        AppDrawer(onClose = { toggleDrawer() })
                    }
                }
            }
        }
    }

    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { toggleSystemOptions() },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        SystemOptionsPanel(
                            onSettingsClick = {
                                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            onWifiClick = {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            onBluetoothClick = {
                                // En muchos dispositivos modernos, esto lleva a Connection Preferences / Connected Devices
                                val intent = Intent("android.settings.CONNECTED_DEVICE_SETTINGS").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback al estándar si el anterior falla
                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    })
                                }
                                toggleSystemOptions()
                            },
                            onMuteClick = { toggleMute() },
                            onPowerClick = {
                                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                                toggleSystemOptions()
                            },
                            onScreenshotClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                                } else {
                                    Toast.makeText(this@LauncherAccessibilityService, "Captura no soportada", Toast.LENGTH_SHORT).show()
                                }
                                toggleSystemOptions()
                            },
                            onRecordClick = {
                                if (!abrirGrabadorPantalla()) {
                                    Toast.makeText(this@LauncherAccessibilityService, "El grabador de pantalla no está disponible en este dispositivo", Toast.LENGTH_SHORT).show()
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
                                .clickable(interactionSource = null, indication = null) { /* Consume el click */ }
                        )
                    }
                }
            }
        }
    }

    private fun setupBarraNavegacion(density: Float) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (45 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)

            setContent {
                LauncherCalmadoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(
                            onActionClicked = { accion ->
                                when (accion) {
                                    "GOOGLE" -> {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        try { startActivity(intent) } catch (e: Exception) {}
                                    }
                                    "FILES" -> {
                                        val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                                            ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        try { startActivity(intent) } catch (e: Exception) {}
                                    }
                                    "CLOCK" -> {
                                        if (!abrirRelojSistema()) {
                                            Toast.makeText(this@LauncherAccessibilityService, "No se pudo encontrar la app de Reloj", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                // Notifica a la MainActivity y a otros componentes
                                sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            }, 
                            iconColor = iconColorNav,
                            backgroundColor = navBarBackground
                        )
                    }
                }
            }
        }

        windowManager.addView(vistaNav, params)
    }

    private fun abrirGrabadorPantalla(): Boolean {
        // Intentar abrir el grabador de pantalla nativo de Android 11+ (SystemUI)
        val intents = arrayOf(
            Intent().setComponent(android.content.ComponentName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordDialog")),
            Intent("com.android.systemui.screenrecord.START"),
            Intent().setComponent(android.content.ComponentName("com.samsung.android.app.screenrecorder", "com.samsung.android.app.screenrecorder.ScreenRecorderService")), // Samsung
            Intent().setComponent(android.content.ComponentName("com.miui.screenrecorder", "com.miui.screenrecorder.ActivityMain")) // Xiaomi
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                // Continuar probando
            }
        }
        return false
    }

    private fun abrirRelojSistema(): Boolean {
        val paquetesReloj = arrayOf(
            "com.google.android.deskclock",      // Google / Pixel
            "com.android.deskclock",             // AOSP
            "com.sec.android.app.clockpackage",  // Samsung
            "com.sonyericsson.organizer",        // Sony
            "com.huawei.deskclock",              // Huawei
            "com.oppo.alarmclock",               // Oppo
            "com.coloros.alarmclock",            // Realme/Oppo
            "com.htc.android.worldclock",        // HTC
            "com.motorola.blur.alarmclock",      // Motorola
            "com.lge.clock"                      // LG
        )

        for (paquete in paquetesReloj) {
            val intent = packageManager.getLaunchIntentForPackage(paquete)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
                return true
            }
        }
        
        // Fallback si no encontramos ninguno de los paquetes conocidos
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun registrarReceptores() {
        val filter = IntentFilter().apply {
            addAction("CAMBIO_TEMA")
            addAction("COMANDO_SISTEMA")
            addAction("ACCION_BARRA")
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            @Suppress("DEPRECATION")
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        }
        ContextCompat.registerReceiver(this, receptorComandos, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Limpiamos todas las vistas del WindowManager para evitar fugas y errores
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
        if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) windowManager.removeView(vistaDrawer)
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
        
        try {
            unregisterReceiver(receptorComandos)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
