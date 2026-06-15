package com.example.launcherorbys

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.*
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.launcherorbys.managers.PermissionManager
import com.example.launcherorbys.ui.home.HomeScreen
import com.example.launcherorbys.ui.home.MainViewModel
import com.example.launcherorbys.ui.setup.PermissionDialog
import com.example.launcherorbys.ui.setup.PermissionItem
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants

/**
 * Actividad principal del Launcher Orbys.
 *
 * Esta clase sirve como el host principal para la interfaz de usuario basada en Jetpack Compose
 * y coordina la inicialización, gestión de permisos críticos y respuesta a eventos del sistema.
 */
class MainActivity : ComponentActivity() {

    /** Gestor encargado de verificar y solicitar permisos del sistema. */
    private lateinit var gestorPermisos: PermissionManager
    /** Modelo de vista (ViewModel) que contiene el estado global de la aplicación. */
    private val modelo: MainViewModel by viewModels()

    /** 
     * Registrador para manejar la respuesta de la solicitud de múltiples permisos de Bluetooth. 
     * Se activa en Android 12 (API 31) o superior.
     */
    private val lanzadorSolicitudBluetooth = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Actualizamos el estado de los permisos una vez que el usuario responde al diálogo del sistema.
        modelo.actualizarEstadosPermisos()
    }

    /** 
     * Receptor de transmisiones internas para responder a eventos específicos, 
     * como cambios en el fondo de pantalla o solicitudes de Bluetooth desde servicios de fondo.
     */
    private val receptorInterno = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_REQUEST_BLUETOOTH -> {
                    // Refrescamos los estados de permisos locales.
                    modelo.actualizarEstadosPermisos()
                    
                    // Aseguramos que la actividad esté en primer plano para mostrar el diálogo de permisos.
                    val it = Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(it)
                }
                Intent.ACTION_WALLPAPER_CHANGED -> {
                    // Si el fondo del sistema cambia, reseteamos la configuración de fondo personalizada.
                    modelo.persistFondo("")
                    modelo.persistEsClaro(true)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización del gestor de permisos.
        gestorPermisos = PermissionManager(this)

        // Configuración de la interfaz (pantalla completa, barras de sistema, etc.).
        configurarInterfazSistema()
        // Registro de los receptores de eventos.
        registrarReceptores()
        // Configuración del oyente para detectar cambios de color en el fondo de pantalla.
        configurarEscuchaFondo()

        // Manejo inicial del Intent con el que se abrió la actividad.
        gestionarIntent(intent)

        // Bloqueamos o manejamos el botón "Atrás" del sistema para evitar cierres accidentales del launcher.
        onBackPressedDispatcher.addCallback(this) { /* No hacemos nada para bloquear el cierre */ }

        // Definimos el contenido de la UI usando Jetpack Compose.
        setContent {
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            // Detectamos si el usuario tiene activado el texto en negrita en los ajustes de accesibilidad.
            val negrita = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) config.fontWeightAdjustment else 0

            // Aplicamos el tema visual del launcher.
            LauncherOrbysTheme(
                darkTheme = !modelo.esTemaClaro,
                baseWeight = if (negrita > 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            ) {
                // Eliminamos el efecto visual de "overscroll" (resplandor al llegar al final del scroll).
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

                    // Pantalla principal del launcher.
                    HomeScreen(
                        modelo = modelo,
                        alSolicitarBluetooth = { modelo.actualizarEstadosPermisos() }
                    )

                    // Componente que bloquea el uso si faltan permisos críticos.
                    ProtectorPermisosUI()
                }
            }
        }
    }

    /**
     * Configura el comportamiento visual de la ventana y oculta las barras del sistema.
     */
    private fun configurarInterfazSistema() {
        // Deshabilitamos las transiciones por defecto al abrir el launcher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
        // Permitimos que la UI se dibuje debajo de las barras de sistema.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Fondo transparente para evitar parpadeos durante transiciones.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        ocultarBarraEstado()
    }

    /**
     * Composable que verifica el estado de los permisos obligatorios y muestra un diálogo si alguno falta.
     */
    @Composable
    private fun ProtectorPermisosUI() {
        // Verificamos si todos los permisos críticos han sido concedidos.
        val todosConcedidos = modelo.esLauncherPorDefecto && 
                             modelo.estaAccesibilidadHabilitada && 
                             modelo.puedeEscribirAjustes && 
                             modelo.tienePermisoBluetooth

        if (!todosConcedidos) {
            // Creamos la lista de ítems de permisos para el diálogo.
            val listaPermisos = remember(
                modelo.esLauncherPorDefecto, 
                modelo.puedeEscribirAjustes, 
                modelo.estaAccesibilidadHabilitada,
                modelo.tienePermisoBluetooth
            ) {
                listOf(
                    PermissionItem(
                        getString(R.string.permission_default_launcher),
                        getString(R.string.permission_default_launcher_desc),
                        modelo.esLauncherPorDefecto
                    ) {
                        // Abrimos ajustes para poner la app como launcher predeterminado.
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        modelo.iniciarComprobacionAutomatica({ gestorPermisos.esLauncherPorDefecto() }, ::volverAMain)
                    },
                    PermissionItem(
                        getString(R.string.permission_system_settings),
                        getString(R.string.permission_system_settings_desc),
                        modelo.puedeEscribirAjustes
                    ) {
                        // Abrimos ajustes para permitir modificar configuraciones del sistema (como brillo).
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                        modelo.iniciarComprobacionAutomatica({ gestorPermisos.puedeEscribirAjustes() }, ::volverAMain)
                    },
                    PermissionItem(
                        getString(R.string.permission_accessibility_service),
                        getString(R.string.permission_accessibility_service_desc),
                        modelo.estaAccesibilidadHabilitada
                    ) {
                        // Abrimos ajustes de accesibilidad para activar el servicio de fondo.
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        modelo.iniciarComprobacionAutomatica({ gestorPermisos.estaAccesibilidadHabilitada() }, ::volverAMain)
                    },
                    PermissionItem(
                        getString(R.string.dialog_bluetooth_title),
                        getString(R.string.dialog_bluetooth_desc),
                        modelo.tienePermisoBluetooth
                    ) {
                        // Solicitamos permisos de Bluetooth en Android 12+.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            lanzadorSolicitudBluetooth.launch(
                                arrayOf(
                                    android.Manifest.permission.BLUETOOTH_CONNECT,
                                    android.Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        }
                    }
                )
            }
            // Mostramos el diálogo de permisos (no se puede cerrar hasta que se concedan todos).
            PermissionDialog(permissions = listaPermisos, onDismiss = {})
        }
    }

    /**
     * Fuerza el regreso a la actividad principal desde una pantalla de ajustes externa.
     */
    private fun volverAMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
    }

    /**
     * Registra los filtros de eventos para el receptor interno.
     */
    private fun registrarReceptores() {
        val filtro = IntentFilter().apply {
            addAction(Constants.ACTION_NAVBAR_COMMAND)
            addAction(Constants.ACTION_REQUEST_BLUETOOTH)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        // Usamos RECEIVER_EXPORTED para permitir la comunicación entre componentes.
        ContextCompat.registerReceiver(this, receptorInterno, filtro, ContextCompat.RECEIVER_EXPORTED)
    }

    /**
     * Configura el motor del sistema para avisar cuando cambien los colores del fondo de pantalla (Material You).
     */
    private fun configurarEscuchaFondo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ colores, _ ->
                // Solo actualizamos el tema si no hay un fondo personalizado (imagen o color) activo.
                if (modelo.uriImagenFondo == null && modelo.colorSolido == null) {
                    actualizarTemaDesdeColores(colores)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    /**
     * Extrae información de color del fondo de pantalla para decidir si usar tema claro u oscuro.
     */
    private fun actualizarTemaDesdeColores(colores: WallpaperColors?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colores != null) {
            val esClaro = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // En Android 12+ el sistema nos indica directamente si el fondo admite texto oscuro.
                (colores.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else true // Por defecto asumimos claro para versiones anteriores.

            modelo.actualizarTema(esClaro)
            modelo.persistEsClaro(esClaro)
            // Notificamos a otros componentes del cambio de tema.
            sendBroadcast(Intent(Constants.ACTION_THEME_CHANGED).putExtra("esClaro", esClaro))
        }
    }

    /**
     * Oculta de forma persistente la barra de estado y los iconos del sistema.
     */
    private fun ocultarBarraEstado() {
        val controlador = WindowCompat.getInsetsController(window, window.decorView)
        // Hacemos que las barras solo aparezcan temporalmente al deslizar.
        controlador.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controlador.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Analiza el Intent recibido para ejecutar acciones específicas bajo demanda.
     */
    private fun gestionarIntent(intent: Intent?) {
        if (intent == null) return
        
        when (intent.action) {
            Constants.ACTION_REQUEST_BLUETOOTH -> {
                // Refresca estados; el UIPermissionGuard se encargará de mostrar el diálogo si es necesario.
                modelo.actualizarEstadosPermisos()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Aseguramos que las barras sigan ocultas y los permisos actualizados al volver a la app.
        ocultarBarraEstado()
        modelo.actualizarEstadosPermisos()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Actualizamos el intent actual y lo gestionamos.
        setIntent(intent)
        gestionarIntent(intent)
    }

    /**
     * Define áreas en los bordes de la pantalla donde los gestos del sistema (como "Atrás") 
     * deben ser ignorados para priorizar la interacción con el launcher.
     */
    private fun aplicarExclusionGestos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vistaRaiz = window.decorView
            vistaRaiz.post {
                val ancho = vistaRaiz.width
                val alto = vistaRaiz.height
                val densidad = resources.displayMetrics.density

                // Definimos el tamaño de las zonas de exclusión.
                val alturaLateral = (200 * densidad).toInt()
                val alturaInferior = (80 * densidad).toInt()
                val anchoLateral = (40 * densidad).toInt()

                val rectangulos = listOf(
                    // Lado izquierdo (centro).
                    Rect(0, (alto / 2) - (alturaLateral / 2), anchoLateral, (alto / 2) + (alturaLateral / 2)),
                    // Lado derecho (centro).
                    Rect(ancho - anchoLateral, (alto / 2) - (alturaLateral / 2), ancho, (alto / 2) + (alturaLateral / 2)),
                    // Parte inferior (navegación).
                    Rect(0, alto - alturaInferior, ancho, alto)
                )
                vistaRaiz.systemGestureExclusionRects = rectangulos
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-aplicamos ocultación y exclusión de gestos al recuperar el foco.
            ocultarBarraEstado()
            aplicarExclusionGestos()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistramos el receptor para evitar fugas de memoria.
        try { unregisterReceiver(receptorInterno) } catch (_: Exception) {}
    }
}
