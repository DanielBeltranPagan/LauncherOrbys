package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.launcherorbys.managers.SystemControlManager
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
    private lateinit var systemManager: SystemControlManager
    
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
    private var navBarAtTop by mutableStateOf(false)
    private var navBarExpanded by mutableStateOf(true)
    private var sideNavHeightLeft by mutableIntStateOf(0)
    private var sideNavHeightRight by mutableIntStateOf(0)

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
                    systemManager.isBluetoothOn = state == android.bluetooth.BluetoothAdapter.STATE_ON
                }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> systemManager.actualizarValoresSistema()
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
            "TOGGLE_NAVBAR_POSITION" -> toggleNavBarPosition()
            "TOGGLE_NAVBAR_VISIBILITY" -> toggleNavBarVisibility()
        }
    }

    private fun toggleNavBarVisibility() {
        navBarExpanded = !navBarExpanded
        setupBarraNavegacion()
        ajustarPosicionSideNav(true)
        ajustarPosicionSideNav(false)
    }

    /**
     * Alterna la posición de la barra de navegación entre arriba y abajo.
     */
    private fun toggleNavBarPosition() {
        navBarAtTop = !navBarAtTop
        setupBarraNavegacion() // Actualizar posición de la barra

        // Reposicionar los SideNavs para que no choquen con la nueva posición de la NavBar
        ajustarPosicionSideNav(true)
        ajustarPosicionSideNav(false)
        
        // Informar al resto de la app sobre el cambio de posición
        sendBroadcast(Intent("NAVBAR_POSITION_CHANGED").putExtra("atTop", navBarAtTop))
    }

    private fun ajustarPosicionSideNav(isLeft: Boolean) {
        val params = if (isLeft) paramsSideLeft else paramsSideRight
        val vista = if (isLeft) vistaSideNavLeft else vistaSideNavRight
        val height = if (isLeft) sideNavHeightLeft else sideNavHeightRight
        
        if (::windowManager.isInitialized && vista.parent != null) {
            val navBarHeight = if (!navBarExpanded) 0 else (48 * resources.displayMetrics.density).toInt()
            val screenHeight = resources.displayMetrics.heightPixels
            
            val minY = if (navBarAtTop) navBarHeight else 0
            val maxY = if (navBarAtTop) screenHeight - height else screenHeight - navBarHeight - height
            
            params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
            windowManager.updateViewLayout(vista, params)
        }
    }

    /**
     * Muestra u oculta el panel de opciones del sistema.
     */
    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            systemManager.actualizarValoresSistema()
            systemOptionsVisible = true
            val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            windowManager.addView(vistaSystemOptions, params)
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
            systemOptionsVisible = false
        }
    }

    private fun launchSettings(action: String) {
        systemManager.launchSettings(action)
        toggleSystemOptions()
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

        // Configuración programática para asegurar la captura de eventos de apertura de apps
        val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        this.serviceInfo = info
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        systemManager = SystemControlManager(this)

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
                            val screenHeight = resources.displayMetrics.heightPixels
                            val navBarHeight = if (!navBarExpanded) 0 else (48 * resources.displayMetrics.density).toInt()
                            val height = if (isLeft) sideNavHeightLeft else sideNavHeightRight
                            
                            val minY = if (navBarAtTop) navBarHeight else 0
                            val maxY = if (navBarAtTop) screenHeight - height else screenHeight - navBarHeight - height
                            
                            params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
                            windowManager.updateViewLayout(this@apply, params)
                        },
                        isNavBarVisible = navBarExpanded,
                        isNavBarAtTop = navBarAtTop,
                        onHeightChanged = { newHeight ->
                            if (isLeft) sideNavHeightLeft = newHeight else sideNavHeightRight = newHeight
                            ajustarPosicionSideNav(isLeft)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(null, null) { toggleSystemOptions() },
                        contentAlignment = if (navBarAtTop) Alignment.TopCenter else Alignment.BottomCenter
                    ) {
                        SystemOptionsPanel(
                            onSettingsClick = { launchSettings(Settings.ACTION_SETTINGS) },
                            onWifiClick = { launchSettings(Settings.ACTION_WIFI_SETTINGS) },
                            onBluetoothClick = { systemManager.abrirAjustesBT(); toggleSystemOptions() },
                            onMuteClick = { systemManager.toggleMute() },
                            onPowerClick = { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); toggleSystemOptions() },
                            onScreenshotClick = {
                                // Ocultamos la barra antes de la captura
                                navBarExpanded = false
                                setupBarraNavegacion()
                                
                                // Pequeño delay para asegurar que se oculte antes de disparar la captura
                                vistaNav.postDelayed({
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                                    } else {
                                        toast("No soportado")
                                    }
                                    // La barra se queda cerrada como pediste
                                }, 200)
                                
                                toggleSystemOptions()
                            },
                            onRecordClick = { if (!abrirGrabadorPantalla()) toast("No disponible"); toggleSystemOptions() },
                            isWifiOn = systemManager.isWifiOn, isBluetoothOn = systemManager.isBluetoothOn, isMuted = systemManager.isMuted,
                            currentBrightness = systemManager.currentBrightness, onBrightnessChange = { systemManager.cambiarBrillo(it) },
                            isAutoBrightness = systemManager.isAutoBrightness, onAutoBrightnessChange = { systemManager.cambiarModoBrillo(it) },
                            currentVolume = systemManager.currentVolume, onVolumeChange = { systemManager.cambiarVolumen(it) },
                            modifier = Modifier
                                .padding(
                                    top = if (navBarAtTop) 50.dp else 0.dp,
                                    bottom = if (navBarAtTop) 0.dp else 60.dp
                                )
                                .clickable(null, null) { }
                        )
                    }
                }
            }
        }
    }

    /**
     * Crea y gestiona la barra de navegación persistente.
     */
    private fun setupBarraNavegacion() {
        val density = resources.displayMetrics.density
        // Si no está expandida, altura mínima de 1 píxel y transparente
        val h = if (!navBarExpanded) 1 else (48 * density).toInt()
        
        val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, h).apply {
            gravity = if (navBarAtTop) Gravity.TOP else Gravity.BOTTOM
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            if (!navBarExpanded) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        }

        if (!::vistaNav.isInitialized) {
            vistaNav = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@LauncherAccessibilityService)
                setViewTreeSavedStateRegistryOwner(this@LauncherAccessibilityService)
            }
            windowManager.addView(vistaNav, params)
        } else {
            // Si ya existe, simplemente actualizamos sus LayoutParams para moverla de sitio
            windowManager.updateViewLayout(vistaNav, params)
        }

        vistaNav.setContent {
            LauncherOrbysTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavBar(
                        onActionClicked = { manejarComando(it); sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", it)) },
                        iconColor = iconColorNav,
                        backgroundColor = navBarBackground,
                        isAtTop = navBarAtTop,
                        isExpanded = navBarExpanded
                    )
                }
            }
        }
    }

    /**
     * Helper para crear los LayoutParams de las ventanas superpuestas.
     */
    private fun createOverlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    private fun abrirUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    private fun abrirAppArchivos() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui") ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Solo reaccionamos a cambios de estado de ventana (abrir apps)
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Si la aplicación que pasa al frente NO es nuestro launcher
            // Ignoramos el sistema (SystemUI) para que la barra no se oculte al bajar notificaciones
            if (packageName != this.packageName && 
                packageName != "com.android.systemui" && 
                packageName != "android") {
                
                // Cerramos la barra si está abierta porque se ha abierto una APP
                if (navBarExpanded) {
                    navBarExpanded = false
                    setupBarraNavegacion()
                }
            }
            // He ELIMINADO el "else if (packageName == this.packageName)" 
            // para que NO se abra sola al volver al Launcher.
        }
    }

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
