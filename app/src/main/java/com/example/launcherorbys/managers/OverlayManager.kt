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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launcherorbys.ui.drawer.AppDrawer
import com.example.launcherorbys.ui.navigation.NavBar
import com.example.launcherorbys.ui.navigation.SideNavBar
import com.example.launcherorbys.ui.recording.RecordingTimer
import com.example.launcherorbys.ui.recording.StopRecordingDialog
import com.example.launcherorbys.ui.system.SystemOptionsPanel
import com.example.launcherorbys.ui.theme.Dimens
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants

/**
 * Orquestador central de las capas de superposición (Overlays).
 * Gestiona la jerarquía visual de Compose sobre el sistema Android.
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val systemManager: SystemControlManager,
    private val appLauncher: AppLauncher
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val recordingManager = RecordingManager(service)

    // --- Vistas de Superposición ---
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    private lateinit var vistaSideNavLeft: ComposeView
    private lateinit var vistaSideNavRight: ComposeView
    private lateinit var vistaTimer: ComposeView

    // --- Parámetros de Ventana ---
    private lateinit var paramsSideLeft: WindowManager.LayoutParams
    private lateinit var paramsSideRight: WindowManager.LayoutParams
    private lateinit var paramsTimer: WindowManager.LayoutParams

    // --- Estado de la Interfaz ---
    var iconColorNav by mutableStateOf(Color.White)
    var navBarBackground by mutableStateOf(Color.Black)
    var drawerVisible by mutableStateOf(false)
    var systemOptionsVisible by mutableStateOf(false)
    var navBarAtTop by mutableStateOf(false)
    var navBarExpanded by mutableStateOf(true)
    var clockAtLeft by mutableStateOf(true)
    
    private var sideNavHeightLeft by mutableIntStateOf(0)
    private var sideNavHeightRight by mutableIntStateOf(0)

    /**
     * Despliega todas las capas visuales necesarias.
     */
    fun setupOverlays() {
        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion()
        setupSideNavs()
        setupTimerOverlay()
    }

    /**
     * Canaliza comandos desde la UI hacia acciones del sistema o cambios de estado.
     */
    fun manejarComando(comando: String?) {
        when (comando) {
            "BACK" -> if (drawerVisible) toggleDrawer() else service.performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> {
                if (drawerVisible) toggleDrawer()
                service.performGlobalAction(GLOBAL_ACTION_HOME)
                launchHomeIntent()
            }
            "RECENTS" -> {
                if (drawerVisible) toggleDrawer()
                if (systemOptionsVisible) toggleSystemOptions()
                service.performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "APPS" -> toggleDrawer()
            "SYSTEM_OPTIONS" -> toggleSystemOptions()
            "WALLPAPER" -> openWallpaperPicker()
            "GOOGLE" -> appLauncher.abrirUrl("https://www.google.com")
            "FILES" -> appLauncher.abrirAppArchivos()
            "CLOCK" -> if (!appLauncher.abrirRelojSistema()) toast("Reloj no encontrado")
            "TOGGLE_NAVBAR_POSITION" -> toggleNavBarPosition()
            "TOGGLE_NAVBAR_VISIBILITY" -> toggleNavBarVisibility()
            "TOGGLE_CLOCK_SIDE" -> {
                clockAtLeft = !clockAtLeft
                setupBarraNavegacion()
            }
        }
    }

    // --- Gestión de Capas Específicas ---

    fun closeAllOverlays() {
        if (drawerVisible) toggleDrawer()
        if (systemOptionsVisible) toggleSystemOptions()
    }

    fun toggleNavBarVisibility() {
        navBarExpanded = !navBarExpanded
        setupBarraNavegacion()
        actualizarPosicionesSideNav()
    }

    fun toggleNavBarPosition() {
        navBarAtTop = !navBarAtTop
        setupBarraNavegacion()
        actualizarPosicionesSideNav()
        service.sendBroadcast(Intent(Constants.ACTION_NAVBAR_POSITION_CHANGED).putExtra("atTop", navBarAtTop))
    }

    fun toggleDrawer() {
        if (!drawerVisible) {
            drawerVisible = true
            windowManager.addView(vistaDrawer, createOverlayParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) windowManager.removeView(vistaDrawer)
            drawerVisible = false
        }
    }

    fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            systemManager.actualizarValoresSistema()
            systemOptionsVisible = true
            windowManager.addView(vistaSystemOptions, createOverlayParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
            systemOptionsVisible = false
        }
    }

    fun actualizarColores(esClaro: Boolean) {
        navBarBackground = if (esClaro) Color.Black else Color.White
        iconColorNav = if (esClaro) Color.White else Color.Black
    }

    // --- Configuración de Vistas Compose ---

    private fun setupBarraNavegacion() {
        val density = service.resources.displayMetrics.density
        val heightPx = if (!navBarExpanded) 1 else (48 * density).toInt()

        val params = createOverlayParams(MATCH_PARENT, heightPx).apply {
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
                        service.sendBroadcast(Intent(Constants.ACTION_NAVBAR_COMMAND).putExtra("comando", it)) 
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
        val initialY = (service.resources.displayMetrics.heightPixels / 3)
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
                contentAlignment = Alignment.TopCenter
            ) {
                // Alineamos el panel para que su parte inferior coincida con la del AppDrawer (que está centrado y mide 65%)
                val drawerBottomHeight = 0.5f + (Dimens.DrawerHeightPercent / 2f)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(drawerBottomHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SystemOptionsPanel(
                        onSettingsClick = { openSettings(Settings.ACTION_SETTINGS) },
                        onWifiClick = { 
                            systemManager.abrirAjustesWifi()
                            vistaSystemOptions.postDelayed({ toggleSystemOptions() }, 200)
                        },
                        onBluetoothClick = { handleBluetoothAction() },
                        onWallpaperClick = { openWallpaperPicker() },
                        onMuteClick = { systemManager.toggleMute() },
                        onPowerClick = { service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); toggleSystemOptions() },
                        onScreenshotClick = { takeScreenshot() },
                        onRecordClick = { handleRecordAction() },
                        isWifiOn = systemManager.isWifiOn,
                        isBluetoothOn = systemManager.isBluetoothOn,
                        isMuted = systemManager.isMuted,
                        currentBrightness = systemManager.currentBrightness,
                        onBrightnessChange = { systemManager.cambiarBrillo(it) },
                        isAutoBrightness = systemManager.isAutoBrightness,
                        onAutoBrightnessChange = { systemManager.cambiarModoBrillo(it) },
                        currentVolume = systemManager.currentVolume,
                        onVolumeChange = { systemManager.cambiarVolumen(it) },
                        modifier = Modifier.clickable(null, null) { }
                    )
                }
            }
        }
    }

    private fun setupTimerOverlay() {
        paramsTimer = createSideNavParams(Gravity.START, 8).apply {
            x = (16 * service.resources.displayMetrics.density).toInt()
        }

        vistaTimer = createComposeView {
            Box(modifier = Modifier.fillMaxSize()) {
                if (recordingManager.isRecording) {
                    Box(modifier = Modifier.padding(top = 8.dp, start = 16.dp)) {
                        RecordingTimer(
                            seconds = recordingManager.recordingSeconds,
                            onStop = { 
                                recordingManager.showStopConfirmation = true 
                                actualizarVentanaTimer(true)
                            }
                        )
                    }
                }
                if (recordingManager.showStopConfirmation) {
                    StopRecordingDialog(
                        onConfirm = { recordingManager.stopRecording() },
                        onCancel = { 
                            recordingManager.showStopConfirmation = false 
                            actualizarVentanaTimer(false)
                        }
                    )
                }
            }
        }
    }

    // --- Lógica de Grabación ---

    fun startRecording() {
        recordingManager.startTimer(service as LifecycleOwner)
        actualizarVentanaTimer(false)
        if (vistaTimer.parent == null) windowManager.addView(vistaTimer, paramsTimer)
    }

    fun finishRecordingUI() {
        recordingManager.resetState()
        if (::vistaTimer.isInitialized && vistaTimer.parent != null) windowManager.removeView(vistaTimer)
    }

    private fun handleRecordAction() {
        if (recordingManager.isRecording) {
            recordingManager.stopRecording()
        } else {
            appLauncher.iniciarGrabacionEstandar()
        }
        toggleSystemOptions()
    }

    private fun actualizarVentanaTimer(full: Boolean) {
        if (!::paramsTimer.isInitialized || vistaTimer.parent == null) return
        paramsTimer.width = if (full) MATCH_PARENT else WRAP_CONTENT
        paramsTimer.height = if (full) MATCH_PARENT else WRAP_CONTENT
        windowManager.updateViewLayout(vistaTimer, paramsTimer)
    }

    // --- Helpers Internos ---

    private fun handleBluetoothAction() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) {
            // Si falta el permiso en Android 12+, SOLO pedir permiso
            launchHomeIntent()
            service.sendBroadcast(Intent(Constants.ACTION_REQUEST_BLUETOOTH).setPackage(service.packageName))
            // Cerramos el panel de opciones
            if (systemOptionsVisible) toggleSystemOptions()
            return  // ← IMPORTANTE: SALIR SIN ABRIR AJUSTES
        }

        // Si ya tiene permiso, abrir directamente ajustes de Bluetooth
        systemManager.abrirAjustesBT()

        // Cerramos el panel de opciones para que el usuario vea la pantalla de ajustes
        if (systemOptionsVisible) toggleSystemOptions()
    }




    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else toast("No soportado")
        toggleSystemOptions()
    }

    private fun openSettings(action: String) {
        systemManager.launchSettings(action)
        vistaSystemOptions.postDelayed({ toggleSystemOptions() }, 200)
    }

    private fun openWallpaperPicker() {
        val intents = mutableListOf<Intent>()
        
        // 1. Wallpaper & Style (Google / Pixel / Android 12+)
        intents.add(Intent("com.google.android.apps.wallpaper.VIEW_WALLPAPER_COLLECTION"))
        intents.add(Intent().setClassName("com.google.android.apps.wallpaper", "com.google.android.apps.wallpaper.WallpaperPickerActivity"))
        intents.add(Intent().setClassName("com.google.android.apps.wallpaper", "com.google.android.apps.wallpaper.PickerActivity"))

        // 2. Samsung (One UI)
        intents.add(Intent().setClassName("com.samsung.android.app.wallpaper", "com.samsung.android.app.wallpaper.WallpaperStyleActivity"))
        intents.add(Intent().setClassName("com.samsung.android.app.wallpaper", "com.samsung.android.app.wallpaper.KeyguardWallpaperActivity"))

        // 3. Xiaomi (MIUI)
        intents.add(Intent().setClassName("com.android.thememanager", "com.android.thememanager.WallpaperSettingsActivity"))
        
        // 4. Otros (Oppo, Motorola, Huawei)
        intents.add(Intent("com.oplus.wallpaper.PICKER"))
        intents.add(Intent().setClassName("com.motorola.personalize", "com.motorola.personalize.app.PersonalizeActivity"))
        intents.add(Intent().setClassName("com.huawei.android.totemweather", "com.huawei.android.totemweather.WallpaperPickerActivity"))

        // 5. Ajustes de Android estándar
        intents.add(Intent("android.settings.WALLPAPER_SETTINGS"))
        intents.add(Intent().setClassName("com.android.settings", "com.android.settings.Settings\$WallpaperSettingsActivity"))
        
        // 6. Selector estándar (Chooser)
        intents.add(Intent(Intent.ACTION_SET_WALLPAPER))

        var started = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                started = true
                break
            } catch (_: Exception) { }
        }

        if (!started) {
            try {
                service.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                toast("No se encontró el selector de fondo")
            }
        }

        if (systemOptionsVisible) toggleSystemOptions()
    }

    private fun launchHomeIntent() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        service.startActivity(intent)
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
        val density = service.resources.displayMetrics.density
        val screenHeight = service.resources.displayMetrics.heightPixels
        val viewHeight = if (isLeft) sideNavHeightLeft else sideNavHeightRight
        val navHeight = if (!navBarExpanded) 0 else (48 * density).toInt()
        val minY = if (navBarAtTop) (navHeight + (60 * density).toInt()) else (60 * density).toInt()
        val maxY = screenHeight - viewHeight - (if (navBarAtTop) 16 * density else (navHeight + 8 * density)).toInt()
        params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
    }

    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(service).apply {
            setViewTreeLifecycleOwner(service as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(service as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(service as ViewModelStoreOwner)
            setContent { 
                LauncherOrbysTheme(darkTheme = navBarBackground == Color.White) { 
                    content() 
                } 
            }
        }
    }

    private fun createOverlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private fun createSideNavParams(grav: Int, initialY: Int) = createOverlayParams(WRAP_CONTENT, WRAP_CONTENT).apply {
        gravity = grav or Gravity.TOP
        y = initialY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    private fun toast(m: String) = Toast.makeText(service, m, Toast.LENGTH_SHORT).show()

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
                try { windowManager.removeView(vista) } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val MATCH_PARENT = WindowManager.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = WindowManager.LayoutParams.WRAP_CONTENT
    }
}
