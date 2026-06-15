package com.example.launcherorbys

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.*
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.stringResource
import com.example.launcherorbys.managers.PermissionManager
import com.example.launcherorbys.ui.home.HomeScreen
import com.example.launcherorbys.ui.home.MainViewModel
import com.example.launcherorbys.ui.setup.OnDemandPermissionDialog
import com.example.launcherorbys.ui.setup.PermissionDialog
import com.example.launcherorbys.ui.setup.PermissionItem
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants

/**
 * Actividad principal del Launcher Orbys.
 *
 * Esta clase sirve como el host principal para la interfaz de usuario basada en Jetpack Compose
 * y coordina la lógica de inicialización, gestión de permisos críticos y respuesta a eventos
 * del sistema.
 *
 * **Responsabilidades:**
 * - Configurar la visualización "Full Screen" ocultando las barras del sistema.
 * - Gestionar el flujo de permisos iniciales (Launcher por defecto, Accesibilidad, Ajustes del sistema).
 * - Manejar solicitudes "On-Demand" de permisos para funcionalidades específicas como grabación de audio o Bluetooth.
 * - Escuchar cambios en el fondo de pantalla para adaptar el tema visual.
 * - Actuar como receptor de comandos internos mediante transmisiones (Broadcasts).
 */
class MainActivity : ComponentActivity() {

    /** Gestor encargado de verificar el estado de los permisos de sistema. */
    private lateinit var permissionManager: PermissionManager
    /** ViewModel que mantiene el estado global de la pantalla de inicio. */
    private val viewModel: MainViewModel by viewModels()

    // --- Estados Locales de UI On-Demand ---
    /** Controla la visibilidad del diálogo de permiso de audio. */
    private var showAudioDialog by mutableStateOf(false)
    /** Controla la visibilidad del diálogo de permiso/ajustes de Bluetooth. */
    private var showBluetoothDialog by mutableStateOf(false)
    /** Almacena temporalmente el intento de captura de pantalla mientras se solicita permiso de audio. */
    private var pendingRecordingIntent by mutableStateOf<Intent?>(null)

    // --- Registradores de Resultados de Actividad ---

    /** Registrador para la solicitud del permiso de grabación de audio. */
    private val requestAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingRecordingIntent?.let { startProjection(it) }
        showAudioDialog = false
        pendingRecordingIntent = null
    }

    /** Registrador para la solicitud del permiso de conexión Bluetooth (Android 12+). */
    private val requestBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openBluetoothSettings()
        }
        viewModel.updatePermissionStates()
        showBluetoothDialog = false
    }

    /** Registrador para el resultado de la solicitud de captura de pantalla del sistema. */
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, com.example.launcherorbys.services.ScreenRecordService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }
    }

    // --- Receptores de Eventos ---

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_REQUEST_BLUETOOTH -> {
                    // Acción inmediata: Forzar la visibilidad del diálogo
                    showBluetoothDialog = true
                    viewModel.updatePermissionStates()
                    
                    // Forzar que la actividad se ponga al frente
                    val it = Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(it)
                }
                Intent.ACTION_WALLPAPER_CHANGED -> {
                    viewModel.persistFondo("")
                    viewModel.persistEsClaro(true)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        setupSystemUI()
        registerReceivers()
        setupWallpaperListener()

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this) { /* Bloquear o manejar cierre */ }

        setContent {
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            val fontScale = config.fontScale
            val boldText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) config.fontWeightAdjustment else 0

            LauncherOrbysTheme(
                darkTheme = !viewModel.esTemaClaro,
                baseWeight = if (boldText > 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            ) {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

                    HomeScreen(
                        viewModel = viewModel,
                        onBluetoothRequest = { showBluetoothDialog = true }
                    )

                    // Overlays de Permisos y Diálogos On-Demand
                    val anyOnDemandShowing = showAudioDialog || showBluetoothDialog
                    
                    if (!anyOnDemandShowing) {
                        UIPermissionGuard()
                    }
                    
                    if (showAudioDialog) UIAudioDialog()
                    if (showBluetoothDialog) UIBluetoothDialog()
                }
            }
        }
    }

    private fun setupSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        hideStatusBar()
    }

    @Composable
    private fun UIPermissionGuard() {
        val allGranted = viewModel.isDefaultLauncher && viewModel.isAccessibilityEnabled && viewModel.canWriteSettings

        if (!allGranted) {
            val permissionsList = remember(viewModel.isDefaultLauncher, viewModel.canWriteSettings, viewModel.isAccessibilityEnabled) {
                listOf(
                    PermissionItem(
                        getString(R.string.permission_default_launcher),
                        getString(R.string.permission_default_launcher_desc),
                        viewModel.isDefaultLauncher
                    ) {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        viewModel.startAutoCheck({ permissionManager.isDefaultLauncher() }, ::returnToMain)
                    },
                    PermissionItem(
                        getString(R.string.permission_system_settings),
                        getString(R.string.permission_system_settings_desc),
                        viewModel.canWriteSettings
                    ) {
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                        viewModel.startAutoCheck({ permissionManager.canWriteSettings() }, ::returnToMain)
                    },
                    PermissionItem(
                        getString(R.string.permission_accessibility_service),
                        getString(R.string.permission_accessibility_service_desc),
                        viewModel.isAccessibilityEnabled
                    ) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        viewModel.startAutoCheck({ permissionManager.isAccessibilityEnabled() }, ::returnToMain)
                    }
                )
            }
            PermissionDialog(permissions = permissionsList, onDismiss = {})
        }
    }

    @Composable
    private fun UIAudioDialog() {
        OnDemandPermissionDialog(
            title = stringResource(R.string.dialog_audio_record_title),
            description = stringResource(R.string.dialog_audio_record_desc),
            icon = Icons.Default.Mic,
            onGrant = { requestAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
            onDismiss = {
                pendingRecordingIntent?.let { startProjection(it) }
                showAudioDialog = false
                pendingRecordingIntent = null
            },
            secondaryText = stringResource(R.string.dialog_audio_record_negative)
        )
    }

    @Composable
    private fun UIBluetoothDialog() {
        OnDemandPermissionDialog(
            title = stringResource(R.string.dialog_bluetooth_title),
            description = stringResource(R.string.dialog_bluetooth_desc),
            icon = Icons.Default.Bluetooth,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Solicitar BLUETOOTH_CONNECT en Android 12+
                    requestBluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    // En versiones anteriores, abrir directamente los ajustes
                    openBluetoothSettings()
                }
            },
            onDismiss = { showBluetoothDialog = false }
        )
    }

    private fun openBluetoothSettings() {
        val intents = listOf(
            // Intent definitivo basado en el ID de pantalla: bluetooth_switchbar_screen
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                putExtra(":settings:show_fragment", "com.android.settings.bluetooth.BluetoothDashboardFragment")
                putExtra(":settings:fragment_args_key", "bluetooth_switchbar_screen")
                putExtra(":android:show_fragment", "com.android.settings.bluetooth.BluetoothDashboardFragment")
                putExtra(":android:show_fragment_args", android.os.Bundle().apply {
                    putString("category", "bluetooth_switchbar_screen")
                    putString(":settings:fragment_args_key", "bluetooth_switchbar_screen")
                })
            },
            Intent().apply {
                setComponent(android.content.ComponentName("com.android.settings", "com.android.settings.Settings\$BluetoothSettingsActivity"))
            },
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                return
            } catch (e: Exception) {}
        }
    }

    private fun returnToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_NAVBAR_COMMAND)
            addAction(Constants.ACTION_REQUEST_BLUETOOTH)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        ContextCompat.registerReceiver(this, internalReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun setupWallpaperListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ colors, _ ->
                if (viewModel.uriImagenFondo == null && viewModel.colorSolido == null) {
                    updateThemeFromColors(colors)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun updateThemeFromColors(colors: WallpaperColors?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            val isLight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else true

            viewModel.updateTheme(isLight)
            viewModel.persistEsClaro(isLight)
            sendBroadcast(Intent(Constants.ACTION_THEME_CHANGED).putExtra("esClaro", isLight))
        }
    }

    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Usamos BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE para que el sistema ignore deslizamientos simples
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        when (intent.action) {
            Constants.ACTION_START_SCREEN_RECORD -> {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = mpm.createScreenCaptureIntent()
                if (!permissionManager.hasAudioPermission()) {
                    pendingRecordingIntent = captureIntent
                    showAudioDialog = true
                } else {
                    startProjection(captureIntent)
                }
            }
            Constants.ACTION_REQUEST_BLUETOOTH -> {
                showBluetoothDialog = true
            }
        }
    }

    private fun startProjection(intent: Intent) = screenCaptureLauncher.launch(intent)

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        viewModel.updatePermissionStates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun applyGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rootView = window.decorView
            rootView.post {
                val width = rootView.width
                val height = rootView.height
                val density = resources.displayMetrics.density

                // El máximo permitido para exclusión lateral es 200dp de altura total.
                // Para la parte inferior, intentamos cubrir el área de gestos de Home/Recientes.
                val lateralHeight = (200 * density).toInt()
                val bottomHeight = (80 * density).toInt()
                val lateralWidth = (40 * density).toInt()

                val rects = listOf(
                    // Borde izquierdo (zona central para maximizar efectividad del "Atrás")
                    Rect(0, (height / 2) - (lateralHeight / 2), lateralWidth, (height / 2) + (lateralHeight / 2)),
                    // Borde derecho (zona central)
                    Rect(width - lateralWidth, (height / 2) - (lateralHeight / 2), width, (height / 2) + (lateralHeight / 2)),
                    // Borde inferior (Home y Recientes)
                    Rect(0, height - bottomHeight, width, height)
                )
                rootView.systemGestureExclusionRects = rects
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideStatusBar()
            applyGestureExclusion()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(internalReceiver) } catch (_: Exception) {}
    }
}
