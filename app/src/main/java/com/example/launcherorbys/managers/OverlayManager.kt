package com.example.launcherorbys.managers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
import com.example.launcherorbys.ui.system.SystemOptionsPanel
import com.example.launcherorbys.ui.theme.Dimens
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants

/**
 * Orquestador central de las capas de superposición (Overlays).
 * Gestiona la jerarquía visual de Compose sobre el sistema Android.
 */
class OverlayManager(
    private val servicio: AccessibilityService,
    private val gestorSistema: SystemControlManager,
    private val lanzadorApp: AppLauncher
) {
    private val gestorVentanas = servicio.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Estado observable para forzar la actualización de recursos cuando cambia la configuración
    private var tickConfiguracion by mutableIntStateOf(0)
    private var configuracionActual by mutableStateOf(Configuration(servicio.resources.configuration))

    fun alCambiarConfiguracion(nuevaConfiguracion: Configuration) {
        configuracionActual = Configuration(nuevaConfiguracion)
        tickConfiguracion++
        if (::vistaNav.isInitialized) configurarBarraNavegacion()
        if (::vistaNavLateralIzquierda.isInitialized) actualizarPosicionesNavLateral()
    }

    // --- Vistas de Superposición ---
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaCajon: ComposeView
    private lateinit var vistaOpcionesSistema: ComposeView
    private lateinit var vistaNavLateralIzquierda: ComposeView
    private lateinit var vistaNavLateralDerecha: ComposeView

    // --- Parámetros de Ventana ---
    private lateinit var parametrosLateralIzquierdo: WindowManager.LayoutParams
    private lateinit var parametrosLateralDerecho: WindowManager.LayoutParams

    // --- Estado de la Interfaz ---
    var colorIconoNav by mutableStateOf(Color.White)
    var fondoBarraNav by mutableStateOf(Color.Black)
    var cajonVisible by mutableStateOf(false)
    var opcionesSistemaVisibles by mutableStateOf(false)
    var barraNavArriba by mutableStateOf(false)
    var barraNavExpandida by mutableStateOf(true)
    var relojALaIzquierda by mutableStateOf(true)
    
    private var alturaNavLateralIzquierda by mutableIntStateOf(0)
    private var alturaNavLateralDerecha by mutableIntStateOf(0)

    private var ultimoTiempoAccionCajon = 0L

    private fun puedeAlternarCajon(): Boolean {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoTiempoAccionCajon >= 500L) {
            ultimoTiempoAccionCajon = ahora
            return true
        }
        return false
    }

    /**
     * Despliega todas las capas visuales necesarias.
     */
    fun configurarCapas() {
        configurarCapaCajon()
        configurarCapaOpcionesSistema()
        configurarBarraNavegacion()
        configurarNavLaterales()
    }

    /**
     * Canaliza comandos desde la UI hacia acciones del sistema o cambios de estado.
     */
    fun gestionarComando(comando: String?) {
        when (comando) {
            "BACK" -> if (cajonVisible) alternarCajon() else servicio.performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> {
                if (cajonVisible) alternarCajon()
                servicio.performGlobalAction(GLOBAL_ACTION_HOME)
                lanzarIntentHome()
            }
            "RECENTS" -> {
                if (cajonVisible) alternarCajon()
                if (opcionesSistemaVisibles) alternarOpcionesSistema()
                servicio.performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "APPS" -> alternarCajon()
            "SYSTEM_OPTIONS" -> alternarOpcionesSistema()
            "WALLPAPER" -> abrirSelectorFondo()
            "GOOGLE" -> lanzadorApp.abrirUrl("https://www.google.com")
            "FILES" -> lanzadorApp.abrirAppArchivos()
            "CLOCK" -> if (!lanzadorApp.abrirRelojSistema()) mostrarMensaje("Reloj no encontrado")
            "TOGGLE_NAVBAR_POSITION" -> alternarPosicionBarraNav()
            "TOGGLE_NAVBAR_VISIBILITY" -> alternarVisibilidadBarraNav()
            "TOGGLE_CLOCK_SIDE" -> {
                relojALaIzquierda = !relojALaIzquierda
                configurarBarraNavegacion()
            }
        }
    }

    fun cerrarTodasLasCapas() {
        if (cajonVisible) alternarCajon()
        if (opcionesSistemaVisibles) alternarOpcionesSistema()
    }

    fun alternarVisibilidadBarraNav() {
        barraNavExpandida = !barraNavExpandida
        configurarBarraNavegacion()
        actualizarPosicionesNavLateral()
    }

    fun alternarPosicionBarraNav() {
        barraNavArriba = !barraNavArriba
        configurarBarraNavegacion()
        actualizarPosicionesNavLateral()
        servicio.sendBroadcast(Intent(Constants.ACTION_NAVBAR_POSITION_CHANGED).putExtra("atTop", barraNavArriba))
    }

    fun alternarCajon() {
        if (!puedeAlternarCajon()) return

        if (!cajonVisible) {
            tickConfiguracion++ 
            cajonVisible = true
            
            // Calculamos el espacio disponible para que el cajón no tape la barra de navegación
            val densidad = servicio.resources.displayMetrics.density
            val alturaNav = if (!barraNavExpandida) 0 else (48 * densidad).toInt()
            val alturaPantalla = servicio.resources.displayMetrics.heightPixels
            
            val parametros = crearParametrosSuperposicion(MATCH_PARENT, alturaPantalla - alturaNav).apply {
                // Si la barra está arriba, el cajón se pega abajo. Si está abajo, el cajón se pega arriba.
                gravity = if (barraNavArriba) Gravity.BOTTOM else Gravity.TOP
                // Importante: No usar FLAG_LAYOUT_IN_SCREEN para que respete el área de la ventana
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
            
            gestorVentanas.addView(vistaCajon, parametros)
        } else {
            if (::vistaCajon.isInitialized && vistaCajon.parent != null) gestorVentanas.removeView(vistaCajon)
            cajonVisible = false
        }
    }

    fun alternarOpcionesSistema() {
        if (!opcionesSistemaVisibles) {
            tickConfiguracion++
            gestorSistema.actualizarValoresSistema()
            opcionesSistemaVisibles = true
            gestorVentanas.addView(vistaOpcionesSistema, crearParametrosSuperposicion(MATCH_PARENT, MATCH_PARENT))
        } else {
            if (::vistaOpcionesSistema.isInitialized && vistaOpcionesSistema.parent != null) gestorVentanas.removeView(vistaOpcionesSistema)
            opcionesSistemaVisibles = false
        }
    }

    fun actualizarColores(esClaro: Boolean) {
        fondoBarraNav = if (esClaro) Color.Black else Color.White
        colorIconoNav = if (esClaro) Color.White else Color.Black
    }

    private fun configurarBarraNavegacion() {
        val densidad = servicio.resources.displayMetrics.density
        val alturaPx = if (!barraNavExpandida) 1 else (48 * densidad).toInt()

        val parametros = crearParametrosSuperposicion(MATCH_PARENT, alturaPx).apply {
            gravity = if (barraNavArriba) Gravity.TOP else Gravity.BOTTOM
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (!barraNavExpandida) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (!::vistaNav.isInitialized) {
            vistaNav = crearVistaCompose {
                NavBar(
                    onActionClicked = { 
                        gestionarComando(it)
                        servicio.sendBroadcast(Intent(Constants.ACTION_NAVBAR_COMMAND).putExtra("comando", it)) 
                    },
                    iconColor = colorIconoNav,
                    backgroundColor = fondoBarraNav,
                    isAtTop = barraNavArriba,
                    isExpanded = barraNavExpandida,
                    clockAtLeft = relojALaIzquierda
                )
            }
            gestorVentanas.addView(vistaNav, parametros)
        } else {
            gestorVentanas.updateViewLayout(vistaNav, parametros)
        }
    }

    private fun configurarNavLaterales() {
        val yInicial = (servicio.resources.displayMetrics.heightPixels / 3)
        parametrosLateralIzquierdo = crearParametrosNavLateral(Gravity.START, yInicial)
        parametrosLateralDerecho = crearParametrosNavLateral(Gravity.END, yInicial)

        vistaNavLateralIzquierda = crearVistaNavLateral(true, parametrosLateralIzquierdo)
        vistaNavLateralDerecha = crearVistaNavLateral(false, parametrosLateralDerecho)
        
        gestorVentanas.addView(vistaNavLateralIzquierda, parametrosLateralIzquierdo)
        gestorVentanas.addView(vistaNavLateralDerecha, parametrosLateralDerecho)
    }

    private fun crearVistaNavLateral(esIzquierda: Boolean, parametros: WindowManager.LayoutParams): ComposeView {
        return crearVistaCompose {
            SideNavBar(
                isLeft = esIzquierda,
                onAction = { gestionarComando(it) },
                onDrag = { desplazamientoY ->
                    parametros.y += desplazamientoY.toInt()
                    restringirNavLateral(esIzquierda, parametros)
                    gestorVentanas.updateViewLayout(if (esIzquierda) vistaNavLateralIzquierda else vistaNavLateralDerecha, parametros)
                },
                isNavBarVisible = barraNavExpandida,
                isNavBarAtTop = barraNavArriba,
                onHeightChanged = { nuevaAltura ->
                    if (esIzquierda) alturaNavLateralIzquierda = nuevaAltura else alturaNavLateralDerecha = nuevaAltura
                    ajustarPosicionNavLateral(esIzquierda)
                }
            )
        }
    }

    private fun configurarCapaCajon() {
        vistaCajon = crearVistaCompose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(null, null) { alternarCajon() },
                contentAlignment = Alignment.Center
            ) {
                AppDrawer(alCerrar = { alternarCajon() })
            }
        }
    }

    private fun configurarCapaOpcionesSistema() {
        vistaOpcionesSistema = crearVistaCompose {
            Box(
                modifier = Modifier.fillMaxSize().clickable(null, null) { alternarOpcionesSistema() },
                contentAlignment = Alignment.TopCenter
            ) {
                val alturaFondoCajon = 0.5f + (Dimens.DrawerHeightPercent / 2f)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(alturaFondoCajon),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SystemOptionsPanel(
                        onSettingsClick = { abrirAjustes(Settings.ACTION_SETTINGS) },
                        onWifiClick = { 
                            gestorSistema.abrirAjustesWifi()
                            vistaOpcionesSistema.postDelayed({ alternarOpcionesSistema() }, 200)
                        },
                        onBluetoothClick = { gestionarAccionBluetooth() },
                        onWallpaperClick = { abrirSelectorFondo() },
                        onMuteClick = { gestorSistema.alternarSilencio() },
                        onPowerClick = { servicio.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); alternarOpcionesSistema() },
                        onScreenshotClick = { tomarCapturaPantalla() },
                        isWifiOn = gestorSistema.estaWifiActivado,
                        isBluetoothOn = gestorSistema.estaBluetoothActivado,
                        isMuted = gestorSistema.estaSilenciado,
                        currentBrightness = gestorSistema.brilloActual,
                        onBrightnessChange = { gestorSistema.cambiarBrillo(it) },
                        isAutoBrightness = gestorSistema.esBrilloAutomatico,
                        onAutoBrightnessChange = { gestorSistema.cambiarModoBrillo(it) },
                        currentVolume = gestorSistema.volumenActual,
                        onVolumeChange = { gestorSistema.cambiarVolumen(it) },
                        modifier = Modifier.clickable(null, null) { }
                    )
                }
            }
        }
    }

    private fun gestionarAccionBluetooth() {
        val tienePermiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            servicio.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!tienePermiso) {
            val intent = Intent(servicio, com.example.launcherorbys.MainActivity::class.java).apply {
                action = Constants.ACTION_REQUEST_BLUETOOTH
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            servicio.startActivity(intent)
            servicio.sendBroadcast(Intent(Constants.ACTION_REQUEST_BLUETOOTH).setPackage(servicio.packageName))
            
            if (opcionesSistemaVisibles) alternarOpcionesSistema()
            return
        }

        gestorSistema.abrirAjustesBluetooth()

        if (opcionesSistemaVisibles) alternarOpcionesSistema()
    }

    private fun tomarCapturaPantalla() {
        if (barraNavExpandida) {
            barraNavExpandida = false
            configurarBarraNavegacion()
            actualizarPosicionesNavLateral()
        }

        vistaNav.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                servicio.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                mostrarMensaje("No soportado")
            }
        }, 300)

        alternarOpcionesSistema()
    }

    private fun abrirAjustes(accion: String) {
        gestorSistema.lanzarAjustes(accion)
        vistaOpcionesSistema.postDelayed({ alternarOpcionesSistema() }, 200)
    }

    private fun abrirSelectorFondo() {
        val intents = mutableListOf<Intent>()
        
        intents.add(Intent("com.google.android.apps.wallpaper.VIEW_WALLPAPER_COLLECTION"))
        intents.add(Intent().setClassName("com.google.android.apps.wallpaper", "com.google.android.apps.wallpaper.WallpaperPickerActivity"))
        intents.add(Intent().setClassName("com.google.android.apps.wallpaper", "com.google.android.apps.wallpaper.PickerActivity"))
        intents.add(Intent().setClassName("com.samsung.android.app.wallpaper", "com.samsung.android.app.wallpaper.WallpaperStyleActivity"))
        intents.add(Intent().setClassName("com.samsung.android.app.wallpaper", "com.samsung.android.app.wallpaper.KeyguardWallpaperActivity"))
        intents.add(Intent().setClassName("com.android.thememanager", "com.android.thememanager.WallpaperSettingsActivity"))
        intents.add(Intent("com.oplus.wallpaper.PICKER"))
        intents.add(Intent().setClassName("com.motorola.personalize", "com.motorola.personalize.app.PersonalizeActivity"))
        intents.add(Intent().setClassName("com.huawei.android.totemweather", "com.huawei.android.totemweather.WallpaperPickerActivity"))
        intents.add(Intent("android.settings.WALLPAPER_SETTINGS"))
        intents.add(Intent().setClassName("com.android.settings", "com.android.settings.Settings\$WallpaperSettingsActivity"))
        intents.add(Intent(Intent.ACTION_SET_WALLPAPER))

        var iniciado = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                servicio.startActivity(intent)
                iniciado = true
                break
            } catch (_: Exception) { }
        }

        if (!iniciado) {
            try {
                servicio.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                mostrarMensaje("No se encontró el selector de fondo")
            }
        }

        if (opcionesSistemaVisibles) alternarOpcionesSistema()
    }

    private fun lanzarIntentHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        servicio.startActivity(intent)
    }

    private fun actualizarPosicionesNavLateral() {
        ajustarPosicionNavLateral(true)
        ajustarPosicionNavLateral(false)
    }

    private fun ajustarPosicionNavLateral(esIzquierda: Boolean) {
        if (!(::parametrosLateralIzquierdo.isInitialized && ::parametrosLateralDerecho.isInitialized)) return
        val parametros = if (esIzquierda) parametrosLateralIzquierdo else parametrosLateralDerecho
        val vista = if (esIzquierda) vistaNavLateralIzquierda else vistaNavLateralDerecha
        if (vista.parent != null) {
            restringirNavLateral(esIzquierda, parametros)
            gestorVentanas.updateViewLayout(vista, parametros)
        }
    }

    private fun restringirNavLateral(esIzquierda: Boolean, parametros: WindowManager.LayoutParams) {
        val densidad = servicio.resources.displayMetrics.density
        val alturaPantalla = servicio.resources.displayMetrics.heightPixels
        val alturaVista = if (esIzquierda) alturaNavLateralIzquierda else alturaNavLateralDerecha
        val alturaNav = if (!barraNavExpandida) 0 else (48 * densidad).toInt()
        val minY = if (barraNavArriba) (alturaNav + (60 * densidad).toInt()) else (60 * densidad).toInt()
        val maxY = alturaPantalla - alturaVista - (if (barraNavArriba) 16 * densidad else (alturaNav + 8 * densidad)).toInt()
        parametros.y = parametros.y.coerceIn(minY, maxY.coerceAtLeast(minY))
    }

    private fun crearVistaCompose(contenido: @Composable () -> Unit): ComposeView {
        return ComposeView(servicio).apply {
            setViewTreeLifecycleOwner(servicio as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(servicio as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(servicio as ViewModelStoreOwner)
            setContent { 
                val config = configuracionActual
                val escalaFuente = config.fontScale
                val textoNegrita = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    config.fontWeightAdjustment
                } else 0
                
                val valorDensidad = servicio.resources.displayMetrics.density
                val densidadPersonalizada = Density(valorDensidad, escalaFuente)

                key(config.locales[0].language, tickConfiguracion, escalaFuente, textoNegrita) {
                    CompositionLocalProvider(
                        LocalConfiguration provides config,
                        LocalDensity provides densidadPersonalizada
                    ) {
                        LauncherOrbysTheme(
                            darkTheme = fondoBarraNav == Color.White,
                            baseWeight = if (textoNegrita > 0) FontWeight.Bold else FontWeight.Normal
                        ) {
                            contenido() 
                        }
                    }
                }
            }
        }
    }

    private fun crearParametrosSuperposicion(ancho: Int, alto: Int) = WindowManager.LayoutParams(
        ancho, alto, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private fun crearParametrosNavLateral(gravedad: Int, yInicial: Int) = crearParametrosSuperposicion(WRAP_CONTENT, WRAP_CONTENT).apply {
        gravity = gravedad or Gravity.TOP
        y = yInicial
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    private fun mostrarMensaje(m: String) = Toast.makeText(servicio, m, Toast.LENGTH_SHORT).show()

    fun onDestroy() {
        val vistas = listOf(
            if (::vistaNav.isInitialized) vistaNav else null,
            if (::vistaCajon.isInitialized) vistaCajon else null,
            if (::vistaOpcionesSistema.isInitialized) vistaOpcionesSistema else null,
            if (::vistaNavLateralIzquierda.isInitialized) vistaNavLateralIzquierda else null,
            if (::vistaNavLateralDerecha.isInitialized) vistaNavLateralDerecha else null
        )
        vistas.forEach { vista ->
            if (vista != null && vista.parent != null) {
                try { gestorVentanas.removeView(vista) } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val MATCH_PARENT = WindowManager.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = WindowManager.LayoutParams.WRAP_CONTENT
    }
}
