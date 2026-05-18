package com.example.launcherorbys.managers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launcherorbys.ui.components.AppDrawer
import com.example.launcherorbys.ui.components.NavBar
import com.example.launcherorbys.ui.components.SideNavBar
import com.example.launcherorbys.ui.components.SystemOptionsPanel
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme

/**
 * Gestiona todas las capas de superposición (overlays) de la interfaz de usuario del Launcher.
 * Se encarga de crear, actualizar y destruir las vistas de Compose que se muestran sobre el sistema.
 * Utiliza el [WindowManager] para insertar las vistas como capas de accesibilidad.
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val systemManager: SystemControlManager,
    private val appLauncher: AppLauncher
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val context: Context get() = service

    // --- Vistas de Jetpack Compose (Overlays) ---
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    private lateinit var vistaSideNavLeft: ComposeView
    private lateinit var vistaSideNavRight: ComposeView

    // --- Parámetros de Ventana para Side Navs (Persistentes para drag) ---
    private lateinit var paramsSideLeft: WindowManager.LayoutParams
    private lateinit var paramsSideRight: WindowManager.LayoutParams

    // --- Estados Reactivos de la Interfaz (MutableState para Compose) ---
    var iconColorNav by mutableStateOf(Color.White)
    var navBarBackground by mutableStateOf(Color.Black)
    var drawerVisible by mutableStateOf(false)
    var systemOptionsVisible by mutableStateOf(false)
    var navBarAtTop by mutableStateOf(false)
    var navBarExpanded by mutableStateOf(true)
    
    // Alturas dinámicas de las barras laterales (para límites de movimiento)
    private var sideNavHeightLeft by mutableIntStateOf(0)
    private var sideNavHeightRight by mutableIntStateOf(0)

    /**
     * Inicializa todas las capas necesarias.
     */
    fun setupOverlays() {
        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion()
        setupSideNavs()
    }

    /**
     * Procesa comandos de acción provenientes de la UI.
     */
    fun manejarComando(comando: String?) {
        when (comando) {
            "BACK" -> {
                if (drawerVisible) toggleDrawer() 
                else service.performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "HOME" -> {
                if (drawerVisible) toggleDrawer()
                service.performGlobalAction(GLOBAL_ACTION_HOME)
                // Asegurar que volvemos al Home si es necesario
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                service.startActivity(intent)
            }
            "RECENTS" -> {
                if (drawerVisible) toggleDrawer()
                if (systemOptionsVisible) toggleSystemOptions()
                service.performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "APPS" -> toggleDrawer()
            "SYSTEM_OPTIONS" -> toggleSystemOptions()
            "GOOGLE" -> appLauncher.abrirUrl("https://www.google.com")
            "FILES" -> appLauncher.abrirAppArchivos()
            "CLOCK" -> if (!appLauncher.abrirRelojSistema()) toast("No se encontró el reloj")
            "TOGGLE_NAVBAR_POSITION" -> toggleNavBarPosition()
            "TOGGLE_NAVBAR_VISIBILITY" -> toggleNavBarVisibility()
        }
    }

    // --- Gestión de Visibilidad y Posicionamiento ---

    fun toggleNavBarVisibility() {
        navBarExpanded = !navBarExpanded
        setupBarraNavegacion()
        ajustarPosicionSideNav(true)
        ajustarPosicionSideNav(false)
    }

    fun toggleNavBarPosition() {
        navBarAtTop = !navBarAtTop
        setupBarraNavegacion()
        ajustarPosicionSideNav(true)
        ajustarPosicionSideNav(false)
        // Notificar a otros componentes que la posición cambió
        context.sendBroadcast(Intent("NAVBAR_POSITION_CHANGED").putExtra("atTop", navBarAtTop))
    }

    fun toggleDrawer() {
        if (!drawerVisible) {
            drawerVisible = true
            windowManager.addView(vistaDrawer, createOverlayParams(
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.MATCH_PARENT
            ))
        } else {
            if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) {
                windowManager.removeView(vistaDrawer)
            }
            drawerVisible = false
        }
    }

    fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            systemManager.actualizarValoresSistema()
            systemOptionsVisible = true
            val params = createOverlayParams(
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.MATCH_PARENT
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
     * Actualiza el esquema de colores de la UI (Dark/Light mode).
     */
    fun actualizarColores(esClaro: Boolean) {
        navBarBackground = if (esClaro) Color.Black else Color.White
        iconColorNav = if (esClaro) Color.White else Color.Black
    }

    // --- Inicialización de Vistas Específicas ---

    private fun setupBarraNavegacion() {
        val density = context.resources.displayMetrics.density
        val h = if (!navBarExpanded) 1 else (48 * density).toInt()

        val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, h).apply {
            gravity = if (navBarAtTop) Gravity.TOP else Gravity.BOTTOM
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            // Si está colapsada, no debe interceptar toques (transparente al tacto)
            if (!navBarExpanded) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        }

        if (!::vistaNav.isInitialized) {
            vistaNav = createComposeView {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavBar(
                        onActionClicked = { 
                            manejarComando(it)
                            context.sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", it)) 
                        },
                        iconColor = iconColorNav,
                        backgroundColor = navBarBackground,
                        isAtTop = navBarAtTop,
                        isExpanded = navBarExpanded
                    )
                }
            }
            windowManager.addView(vistaNav, params)
        } else {
            windowManager.updateViewLayout(vistaNav, params)
        }
    }

    private fun setupSideNavs() {
        val initialY = (context.resources.displayMetrics.heightPixels / 3)
        
        // Configuración lado izquierdo
        paramsSideLeft = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, 
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            y = initialY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        // Configuración lado derecho
        paramsSideRight = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, 
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            y = initialY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        vistaSideNavLeft = createSideNavComposeView(true, paramsSideLeft)
        vistaSideNavRight = createSideNavComposeView(false, paramsSideRight)
        
        windowManager.addView(vistaSideNavLeft, paramsSideLeft)
        windowManager.addView(vistaSideNavRight, paramsSideRight)
    }

    private fun createSideNavComposeView(isLeft: Boolean, params: WindowManager.LayoutParams): ComposeView {
        return createComposeView {
            SideNavBar(
                isLeft = isLeft,
                onAction = { manejarComando(it) },
                onDrag = { deltaY ->
                    // Gestión del movimiento vertical de las barras laterales
                    params.y += deltaY.toInt()
                    val screenHeight = context.resources.displayMetrics.heightPixels
                    val navBarHeight = if (!navBarExpanded) 0 else (48 * context.resources.displayMetrics.density).toInt()
                    val currentViewHeight = if (isLeft) sideNavHeightLeft else sideNavHeightRight
                    
                    // Calcular límites para que no se solape con la barra de navegación o salga de pantalla
                    val minY = if (navBarAtTop) navBarHeight else 0
                    val maxY = if (navBarAtTop) screenHeight - currentViewHeight else screenHeight - navBarHeight - currentViewHeight
                    
                    params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
                    windowManager.updateViewLayout(if (isLeft) vistaSideNavLeft else vistaSideNavRight, params)
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

    /**
     * Reajusta la posición de una barra lateral basándose en el estado actual de la barra de navegación.
     */
    private fun ajustarPosicionSideNav(isLeft: Boolean) {
        if (!(::paramsSideLeft.isInitialized || ::paramsSideRight.isInitialized)) return
        
        val params = if (isLeft) paramsSideLeft else paramsSideRight
        val vista = if (isLeft) vistaSideNavLeft else vistaSideNavRight
        val currentViewHeight = if (isLeft) sideNavHeightLeft else sideNavHeightRight
        
        if (vista.parent != null) {
            val navBarHeight = if (!navBarExpanded) 0 else (48 * context.resources.displayMetrics.density).toInt()
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            val minY = if (navBarAtTop) navBarHeight else 0
            val maxY = if (navBarAtTop) screenHeight - currentViewHeight else screenHeight - navBarHeight - currentViewHeight
            
            params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
            windowManager.updateViewLayout(vista, params)
        }
    }

    private fun setupDrawerOverlay() {
        vistaDrawer = createComposeView {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(null, null) { toggleDrawer() }, // Cerrar al tocar fuera
                contentAlignment = Alignment.Center
            ) {
                AppDrawer(onClose = { toggleDrawer() })
            }
        }
    }

    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = createComposeView {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(null, null) { toggleSystemOptions() }, // Cerrar al tocar fuera
                contentAlignment = if (navBarAtTop) Alignment.TopCenter else Alignment.BottomCenter
            ) {
                SystemOptionsPanel(
                    onSettingsClick = { 
                        systemManager.launchSettings(Settings.ACTION_SETTINGS)
                        closeSystemOptionsWithDelay() 
                    },
                    onWifiClick = { 
                        systemManager.launchSettings(Settings.ACTION_WIFI_SETTINGS)
                        closeSystemOptionsWithDelay() 
                    },
                    onBluetoothClick = { 
                        systemManager.abrirAjustesBT()
                        toggleSystemOptions() 
                    },
                    onMuteClick = { systemManager.toggleMute() },
                    onPowerClick = { 
                        service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                        toggleSystemOptions() 
                    },
                    onScreenshotClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                        } else {
                            toast("Función no soportada en esta versión de Android")
                        }
                        toggleSystemOptions()
                    },
                    onRecordClick = { 
                        if (!appLauncher.abrirGrabadorPantalla()) toast("Grabador no disponible")
                        toggleSystemOptions() 
                    },
                    isWifiOn = systemManager.isWifiOn,
                    isBluetoothOn = systemManager.isBluetoothOn,
                    isMuted = systemManager.isMuted,
                    currentBrightness = systemManager.currentBrightness,
                    onBrightnessChange = { systemManager.cambiarBrillo(it) },
                    isAutoBrightness = systemManager.isAutoBrightness,
                    onAutoBrightnessChange = { systemManager.cambiarModoBrillo(it) },
                    currentVolume = systemManager.currentVolume,
                    onVolumeChange = { systemManager.cambiarVolumen(it) },
                    modifier = Modifier
                        .padding(
                            top = if (navBarAtTop) 50.dp else 0.dp, 
                            bottom = if (navBarAtTop) 0.dp else 60.dp
                        )
                        .clickable(null, null) { /* Evitar que el click se propague al cerrar */ }
                )
            }
        }
    }

    private fun closeSystemOptionsWithDelay() {
        vistaSystemOptions.postDelayed({ 
            if (systemOptionsVisible) toggleSystemOptions() 
        }, 200)
    }

    // --- Utilidades ---

    /**
     * Crea una instancia de [ComposeView] vinculada al ciclo de vida del servicio.
     */
    private fun createComposeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(service as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(service as SavedStateRegistryOwner)
            setContent {
                LauncherOrbysTheme {
                    content()
                }
            }
        }
    }

    /**
     * Crea parámetros base para una ventana de tipo overlay de accesibilidad.
     */
    private fun createOverlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, 
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    /**
     * Elimina todas las vistas activas del WindowManager para evitar fugas y errores.
     */
    fun onDestroy() {
        if (::vistaNav.isInitialized && vistaNav.parent != null) {
            try { windowManager.removeView(vistaNav) } catch (e: Exception) {}
        }
        if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) {
            try { windowManager.removeView(vistaDrawer) } catch (e: Exception) {}
        }
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) {
            try { windowManager.removeView(vistaSystemOptions) } catch (e: Exception) {}
        }
        if (::vistaSideNavLeft.isInitialized && vistaSideNavLeft.parent != null) {
            try { windowManager.removeView(vistaSideNavLeft) } catch (e: Exception) {}
        }
        if (::vistaSideNavRight.isInitialized && vistaSideNavRight.parent != null) {
            try { windowManager.removeView(vistaSideNavRight) } catch (e: Exception) {}
        }
    }
}
