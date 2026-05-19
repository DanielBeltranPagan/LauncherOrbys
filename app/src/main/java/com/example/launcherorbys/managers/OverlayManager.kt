package com.example.launcherorbys.managers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launcherorbys.ui.components.*
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orquestador de las capas de superposición (Overlays) del Launcher.
 * Gestiona la creación, actualización y eliminación de las vistas de Compose sobre el sistema.
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val systemManager: SystemControlManager,
    private val appLauncher: AppLauncher
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val context: Context get() = service

    // --- Referencias a Vistas (ComposeView) ---
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    private lateinit var vistaSideNavLeft: ComposeView
    private lateinit var vistaSideNavRight: ComposeView
    private lateinit var vistaTimer: ComposeView

    // --- Parámetros de Ventana (LayoutParams) ---
    private lateinit var paramsSideLeft: WindowManager.LayoutParams
    private lateinit var paramsSideRight: WindowManager.LayoutParams
    private lateinit var paramsTimer: WindowManager.LayoutParams

    // --- Estado Global de la Interfaz ---
    var iconColorNav by mutableStateOf(Color.White)
    var navBarBackground by mutableStateOf(Color.Black)
    var drawerVisible by mutableStateOf(false)
    var systemOptionsVisible by mutableStateOf(false)
    var navBarAtTop by mutableStateOf(false)
    var navBarExpanded by mutableStateOf(true)
    var clockAtLeft by mutableStateOf(true)

    // --- Estado de la Grabación de Pantalla ---
    var isRecording by mutableStateOf(false)
    var recordingSeconds by mutableIntStateOf(0)
    var showStopConfirmation by mutableStateOf(false)
    private var timerJob: Job? = null
    
    // Alturas dinámicas para evitar colisiones
    private var sideNavHeightLeft by mutableIntStateOf(0)
    private var sideNavHeightRight by mutableIntStateOf(0)

    /**
     * Inicializa y despliega todas las capas base.
     */
    fun setupOverlays() {
        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion()
        setupSideNavs()
        setupTimerOverlay()
    }

    /**
     * Procesa y ejecuta acciones provenientes de la UI.
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
            "TOGGLE_CLOCK_SIDE" -> {
                clockAtLeft = !clockAtLeft
                setupBarraNavegacion()
            }
        }
    }

    // --- Métodos de Control de Capas ---

    fun toggleNavBarVisibility() {
        navBarExpanded = !navBarExpanded
        setupBarraNavegacion()
        actualizarPosicionesSideNav()
    }

    fun toggleNavBarPosition() {
        navBarAtTop = !navBarAtTop
        setupBarraNavegacion()
        actualizarPosicionesSideNav()
        context.sendBroadcast(Intent(Constants.ACTION_NAVBAR_POSITION_CHANGED).putExtra("atTop", navBarAtTop))
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
            windowManager.addView(vistaSystemOptions, createOverlayParams(
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.MATCH_PARENT
            ))
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) {
                windowManager.removeView(vistaSystemOptions)
            }
            systemOptionsVisible = false
        }
    }

    fun actualizarColores(esClaro: Boolean) {
        navBarBackground = if (esClaro) Color.Black else Color.White
        iconColorNav = if (esClaro) Color.White else Color.Black
    }

    // --- Configuraciones de Vistas Individuales ---

    private fun setupBarraNavegacion() {
        val density = context.resources.displayMetrics.density
        val heightPx = if (!navBarExpanded) 1 else (48 * density).toInt()

        val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, heightPx).apply {
            gravity = if (navBarAtTop) Gravity.TOP else Gravity.BOTTOM
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            if (!navBarExpanded) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (!::vistaNav.isInitialized) {
            vistaNav = createComposeView {
                NavBar(
                    onActionClicked = { 
                        manejarComando(it)
                        context.sendBroadcast(Intent(Constants.ACTION_NAVBAR_COMMAND).putExtra("comando", it)) 
                    },
                    iconColor = iconColorNav,
                    backgroundColor = navBarBackground,
                    isAtTop = navBarAtTop,
                    isExpanded = navBarExpanded,
                    clockAtLeft = clockAtLeft
                )
            }
            windowManager.addView(vistaNav, params)
        } else {
            windowManager.updateViewLayout(vistaNav, params)
        }
    }

    private fun setupSideNavs() {
        val initialY = (context.resources.displayMetrics.heightPixels / 3)
        
        paramsSideLeft = createSideNavParams(Gravity.START, initialY)
        paramsSideRight = createSideNavParams(Gravity.END, initialY)

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
                    params.y += deltaY.toInt()
                    constrainSideNav(isLeft, params)
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

    private fun actualizarPosicionesSideNav() {
        ajustarPosicionSideNav(true)
        ajustarPosicionSideNav(false)
    }

    private fun ajustarPosicionSideNav(isLeft: Boolean) {
        if (!(::paramsSideLeft.isInitialized && ::paramsSideRight.isInitialized)) return
        val params = if (isLeft) paramsSideLeft else paramsSideRight
        val vista = if (isLeft) vistaSideNavLeft else vistaSideNavRight
        
        if (vista.parent != null) {
            constrainSideNav(isLeft, params)
            windowManager.updateViewLayout(vista, params)
        }
    }

    private fun constrainSideNav(isLeft: Boolean, params: WindowManager.LayoutParams) {
        val density = context.resources.displayMetrics.density
        val screenHeight = context.resources.displayMetrics.heightPixels
        val viewHeight = if (isLeft) sideNavHeightLeft else sideNavHeightRight
        
        val navHeight = if (!navBarExpanded) 0 else (48 * density).toInt()
        val topSafe = (60 * density).toInt() 
        
        val minY = if (navBarAtTop) (navHeight + topSafe) else topSafe
        val maxY = if (navBarAtTop) {
            screenHeight - viewHeight - (16 * density).toInt()
        } else {
            screenHeight - viewHeight - navHeight - (8 * density).toInt()
        }
        
        params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
    }

    private fun setupDrawerOverlay() {
        vistaDrawer = createComposeView {
            Box(
                modifier = Modifier.fillMaxSize().clickable(null, null) { toggleDrawer() },
                contentAlignment = Alignment.Center
            ) {
                AppDrawer(onClose = { toggleDrawer() })
            }
        }
    }

    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = createComposeView {
            Box(
                modifier = Modifier.fillMaxSize().clickable(null, null) { toggleSystemOptions() },
                contentAlignment = if (navBarAtTop) Alignment.TopCenter else Alignment.BottomCenter
            ) {
                SystemOptionsPanel(
                    onSettingsClick = { 
                        systemManager.launchSettings(Settings.ACTION_SETTINGS)
                        vistaSystemOptions.postDelayed({ toggleSystemOptions() }, 200)
                    },
                    onWifiClick = { 
                        systemManager.launchSettings(Settings.ACTION_WIFI_SETTINGS)
                        vistaSystemOptions.postDelayed({ toggleSystemOptions() }, 200)
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
                            toast("No soportado en esta versión")
                        }
                        toggleSystemOptions()
                    },
                    onRecordClick = { 
                        if (isRecording) {
                            context.stopService(Intent(context, com.example.launcherorbys.services.ScreenRecordService::class.java))
                        } else {
                            appLauncher.iniciarGrabacionEstandar()
                        }
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
                        .padding(top = if (navBarAtTop) 50.dp else 0.dp, bottom = if (navBarAtTop) 0.dp else 60.dp)
                        .clickable(null, null) { }
                )
            }
        }
    }

    private fun setupTimerOverlay() {
        paramsTimer = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * context.resources.displayMetrics.density).toInt()
            y = (8 * context.resources.displayMetrics.density).toInt()
        }

        vistaTimer = createComposeView {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isRecording) {
                    Box(modifier = Modifier.padding(top = 8.dp, start = 16.dp)) {
                        RecordingTimer(
                            seconds = recordingSeconds,
                            onStop = { 
                                showStopConfirmation = true 
                                actualizarVentanaTimer(true)
                            }
                        )
                    }
                }

                if (showStopConfirmation) {
                    StopRecordingDialog(
                        onConfirm = { 
                            showStopConfirmation = false
                            stopRecording() 
                        },
                        onCancel = { 
                            showStopConfirmation = false 
                            actualizarVentanaTimer(false)
                        }
                    )
                }
            }
        }
    }

    private fun actualizarVentanaTimer(full: Boolean) {
        if (!::paramsTimer.isInitialized || vistaTimer.parent == null) return
        if (full) {
            paramsTimer.width = WindowManager.LayoutParams.MATCH_PARENT
            paramsTimer.height = WindowManager.LayoutParams.MATCH_PARENT
        } else {
            paramsTimer.width = WindowManager.LayoutParams.WRAP_CONTENT
            paramsTimer.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        windowManager.updateViewLayout(vistaTimer, paramsTimer)
    }

    fun startRecording() {
        if (isRecording) return
        isRecording = true
        recordingSeconds = 0
        showStopConfirmation = false
        
        actualizarVentanaTimer(false)
        if (vistaTimer.parent == null) windowManager.addView(vistaTimer, paramsTimer)

        timerJob?.cancel()
        timerJob = (service as LifecycleOwner).lifecycleScope.launch {
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    fun stopRecording() {
        context.stopService(Intent(context, com.example.launcherorbys.services.ScreenRecordService::class.java))
        finishRecordingUI()
    }

    fun finishRecordingUI() {
        isRecording = false
        showStopConfirmation = false
        timerJob?.cancel()
        if (::vistaTimer.isInitialized && vistaTimer.parent != null) {
            windowManager.removeView(vistaTimer)
        }
    }

    // --- Utilidades Estáticas ---

    private fun createComposeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(service as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(service as SavedStateRegistryOwner)
            setContent { LauncherOrbysTheme { content() } }
        }
    }

    private fun createOverlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, 
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    private fun createSideNavParams(grav: Int, initialY: Int) = createOverlayParams(
        WindowManager.LayoutParams.WRAP_CONTENT, 
        WindowManager.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = grav or Gravity.TOP
        y = initialY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    private fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun onDestroy() {
        val vistas = listOf(
            if (::vistaNav.isInitialized) vistaNav else null,
            if (::vistaDrawer.isInitialized) vistaDrawer else null,
            if (::vistaSystemOptions.isInitialized) vistaSystemOptions else null,
            if (::vistaSideNavLeft.isInitialized) vistaSideNavLeft else null,
            if (::vistaSideNavRight.isInitialized) vistaSideNavRight else null,
            if (::vistaTimer.isInitialized) vistaTimer else null
        )
        
        vistas.forEach { vista ->
            if (vista != null && vista.parent != null) {
                try { windowManager.removeView(vista) } catch (e: Exception) {}
            }
        }
    }
}
