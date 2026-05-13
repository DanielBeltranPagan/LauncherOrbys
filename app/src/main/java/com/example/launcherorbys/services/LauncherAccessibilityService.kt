package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import com.example.launcherorbys.ui.components.AppDrawer
import com.example.launcherorbys.ui.components.NavBar
import com.example.launcherorbys.ui.components.SideNavBar
import com.example.launcherorbys.ui.components.SystemOptionsPanel
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme

/**
 * Servicio de Accesibilidad que actúa como el núcleo del Launcher.
 * Gestiona la barra de navegación, el cajón de aplicaciones y el panel de ajustes rápidos
 * mediante superposiciones (overlays) de Jetpack Compose.
 */
class LauncherAccessibilityService : AccessibilityService(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    
    // Vistas de Compose que se añaden al WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    private lateinit var vistaSideNavLeft: ComposeView
    private lateinit var vistaSideNavRight: ComposeView

    private lateinit var paramsSideLeft: WindowManager.LayoutParams
    private lateinit var paramsSideRight: WindowManager.LayoutParams
    
    // Estados de la interfaz
    private var iconColorNav by mutableStateOf(Color.White)
    private var navBarBackground by mutableStateOf(Color.Black)
    private var drawerVisible by mutableStateOf(false)
    private var systemOptionsVisible by mutableStateOf(false)
    
    // Estados de control del sistema (Brillo, Volumen, Conectividad)
    private var currentBrightness by mutableStateOf(0.5f)
    private var isAutoBrightness by mutableStateOf(false)
    private var isAirplaneModeOn by mutableStateOf(false)
    private var isWifiOn by mutableStateOf(false)
    private var isBluetoothOn by mutableStateOf(false)
    private var isMuted by mutableStateOf(false)
    private var currentVolume by mutableStateOf(0.5f)

    // Configuración necesaria para que Compose funcione dentro de un Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    // Receptor para eventos del sistema y cambios de tema
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CAMBIO_TEMA" -> actualizarColores(intent.getBooleanExtra("esClaro", true))
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                    isBluetoothOn = state == android.bluetooth.BluetoothAdapter.STATE_ON
                }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> actualizarValoresSistema()
            }
        }
    }

    /**
     * Procesa las acciones de navegación y apertura de aplicaciones.
     */
    private fun manejarComando(comando: String?) {
        when (comando) {
            "BACK" -> if (drawerVisible) toggleDrawer() else performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> {
                if (drawerVisible) toggleDrawer()
                performGlobalAction(GLOBAL_ACTION_HOME)
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
            "GOOGLE" -> abrirUrl("https://www.google.com")
            "FILES" -> abrirAppArchivos()
            "CLOCK" -> if (!abrirRelojSistema()) toast("No se encontró el reloj")
        }
    }

    /**
     * Muestra u oculta el panel de opciones del sistema.
     */
    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            actualizarValoresSistema()
            systemOptionsVisible = true
            val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            windowManager.addView(vistaSystemOptions, params)
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
            systemOptionsVisible = false
        }
    }

    /**
     * Obtiene los valores actuales de brillo, volumen y estado de redes.
     */
    private fun actualizarValoresSistema() {
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
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            isWifiOn = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            isBluetoothOn = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) { }
    }

    private fun cambiarModoBrillo(auto: Boolean) {
        if (Settings.System.canWrite(this)) {
            val modo = if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, modo)
            isAutoBrightness = auto
        } else solicitarPermisoEscritura()
    }

    private fun solicitarPermisoEscritura() {
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        toast("Concede permiso de escritura")
    }

    private fun cambiarVolumen(valor: Float) {
        val newVol = (valor * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt()
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
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (valor * 255).toInt())
            currentBrightness = valor
        } else solicitarPermisoEscritura()
    }

    /**
     * Muestra u oculta el cajón de aplicaciones.
     */
    private fun toggleDrawer() {
        if (!drawerVisible) {
            drawerVisible = true
            windowManager.addView(vistaDrawer, createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
        } else {
            if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) windowManager.removeView(vistaDrawer)
            drawerVisible = false
        }
    }

    private fun actualizarColores(esClaro: Boolean) {
        navBarBackground = if (esClaro) Color.Black else Color.White
        iconColorNav = if (esClaro) Color.White else Color.Black
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        val esClaro = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getBoolean("esClaro", true)
        actualizarColores(esClaro)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Inicializar overlays
        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion()
        setupSideNavs()
        registrarReceptores()
    }

    /**
     * Configura las barras de navegación laterales (Izquierda y Derecha).
     */
    private fun setupSideNavs() {
        val initialY = (resources.displayMetrics.heightPixels / 3)

        paramsSideLeft = createOverlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.START or Gravity.TOP
            y = initialY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        paramsSideRight = createOverlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.TOP
            y = initialY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        vistaSideNavLeft = createSideNavComposeView(true, paramsSideLeft)
        vistaSideNavRight = createSideNavComposeView(false, paramsSideRight)

        windowManager.addView(vistaSideNavLeft, paramsSideLeft)
        windowManager.addView(vistaSideNavRight, paramsSideRight)
    }

    private fun createSideNavComposeView(isLeft: Boolean, params: WindowManager.LayoutParams): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)
            setContent {
                LauncherOrbysTheme {
                    SideNavBar(
                        isLeft = isLeft,
                        onAction = { manejarComando(it) },
                        onDrag = { deltaY ->
                            params.y += deltaY.toInt()
                            // Limitar para que no se salga de la pantalla (opcional)
                            val screenHeight = resources.displayMetrics.heightPixels
                            params.y = params.y.coerceIn(0, screenHeight - 200) 
                            windowManager.updateViewLayout(this@apply, params)
                        }
                    )
                }
            }
        }
    }

    /**
     * Configura la vista de Compose para el cajón de aplicaciones.
     */
    private fun setupDrawerOverlay() {
        vistaDrawer = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)
            setContent {
                LauncherOrbysTheme {
                    Box(modifier = Modifier.fillMaxSize().clickable(null, null) { toggleDrawer() }, contentAlignment = Alignment.Center) {
                        AppDrawer(onClose = { toggleDrawer() })
                    }
                }
            }
        }
    }

    /**
     * Configura la vista de Compose para el panel de ajustes rápidos.
     */
    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)
            setContent {
                LauncherOrbysTheme {
                    Box(modifier = Modifier.fillMaxSize().clickable(null, null) { toggleSystemOptions() }, contentAlignment = Alignment.BottomCenter) {
                        SystemOptionsPanel(
                            onSettingsClick = { launchSettings(Settings.ACTION_SETTINGS) },
                            onWifiClick = { launchSettings(Settings.ACTION_WIFI_SETTINGS) },
                            onBluetoothClick = { abrirAjustesBT() },
                            onMuteClick = { toggleMute() },
                            onPowerClick = { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); toggleSystemOptions() },
                            onScreenshotClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                                else toast("No soportado")
                                toggleSystemOptions()
                            },
                            onRecordClick = { if (!abrirGrabadorPantalla()) toast("No disponible"); toggleSystemOptions() },
                            isWifiOn = isWifiOn, isBluetoothOn = isBluetoothOn, isMuted = isMuted,
                            currentBrightness = currentBrightness, onBrightnessChange = { cambiarBrillo(it) },
                            isAutoBrightness = isAutoBrightness, onAutoBrightnessChange = { cambiarModoBrillo(it) },
                            currentVolume = currentVolume, onVolumeChange = { cambiarVolumen(it) },
                            modifier = Modifier.padding(bottom = 60.dp).clickable(null, null) { }
                        )
                    }
                }
            }
        }
    }

    /**
     * Crea y añade la barra de navegación persistente en la parte inferior.
     */
    private fun setupBarraNavegacion() {
        val h = (45 * resources.displayMetrics.density).toInt()
        val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, h).apply {
            gravity = Gravity.BOTTOM
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)
            setContent {
                LauncherOrbysTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(
                            onActionClicked = { manejarComando(it); sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", it)) }, 
                            iconColor = iconColorNav, backgroundColor = navBarBackground
                        )
                    }
                }
            }
        }
        windowManager.addView(vistaNav, params)
    }

    /**
     * Helper para crear los LayoutParams de las ventanas superpuestas.
     */
    private fun createOverlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    private fun launchSettings(action: String) {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        toggleSystemOptions()
    }

    private fun abrirUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    private fun abrirAppArchivos() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui") ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    private fun abrirAjustesBT() {
        val intent = try { Intent("android.settings.CONNECTED_DEVICE_SETTINGS") } catch (e: Exception) { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        toggleSystemOptions()
    }

    private fun abrirGrabadorPantalla(): Boolean {
        val intents = listOf(
            Intent().setClassName("com.android.systemui", "com.android.systemui.screenrecord.ScreenRecordDialog"),
            Intent("com.android.systemui.screenrecord.START"),
            packageManager.getLaunchIntentForPackage("com.samsung.android.app.screenrecorder"),
            packageManager.getLaunchIntentForPackage("com.miui.screenrecorder")
        )
        for (i in intents) {
            try { i?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it); return true } } catch (e: Exception) {}
        }
        return false
    }

    private fun abrirRelojSistema(): Boolean {
        val pkgs = listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage", "com.huawei.deskclock")
        for (p in pkgs) {
            val i = packageManager.getLaunchIntentForPackage(p)
            if (i != null) { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        }
        return try { startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun registrarReceptores() {
        val filter = IntentFilter().apply {
            addAction("CAMBIO_TEMA")
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            @Suppress("DEPRECATION") addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        }
        ContextCompat.registerReceiver(this, receptorComandos, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Eliminar todas las vistas del WindowManager para evitar fugas de memoria
        if (::vistaNav.isInitialized) try { windowManager.removeView(vistaNav) } catch (e: Exception) {}
        if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) try { windowManager.removeView(vistaDrawer) } catch (e: Exception) {}
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) try { windowManager.removeView(vistaSystemOptions) } catch (e: Exception) {}
        if (::vistaSideNavLeft.isInitialized && vistaSideNavLeft.parent != null) try { windowManager.removeView(vistaSideNavLeft) } catch (e: Exception) {}
        if (::vistaSideNavRight.isInitialized && vistaSideNavRight.parent != null) try { windowManager.removeView(vistaSideNavRight) } catch (e: Exception) {}
        try { unregisterReceiver(receptorComandos) } catch (e: Exception) {}
        super.onDestroy()
    }
}
