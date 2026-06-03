package com.example.launcherorbys

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.*
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
import com.example.launcherorbys.data.repository.SettingsRepository
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
 * Actúa como host de la UI base y punto de entrada para configuraciones críticas.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var permissionManager: PermissionManager
    private val viewModel: MainViewModel by viewModels()

    // --- Estados Locales de UI On-Demand ---
    private var showAudioDialog by mutableStateOf(false)
    private var showBluetoothDialog by mutableStateOf(false)
    private var pendingRecordingIntent by mutableStateOf<Intent?>(null)

    // --- Registradores de Resultados de Actividad ---

    private val requestAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingRecordingIntent?.let { startProjection(it) }
        showAudioDialog = false
        pendingRecordingIntent = null
    }

    private val requestBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permiso concedido, abrir ajustes de Bluetooth
            openBluetoothSettings()
        }
        viewModel.updatePermissionStates()
        showBluetoothDialog = false
    }

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
                Constants.ACTION_REQUEST_BLUETOOTH -> showBluetoothDialog = true
                Intent.ACTION_WALLPAPER_CHANGED -> {
                    viewModel.setBackground(null, null)
                    settingsRepository.saveFondo("")
                    settingsRepository.saveEsClaro(true)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository(this)
        permissionManager = PermissionManager(this)

        setupSystemUI()
        loadPreferences()
        registerReceivers()
        setupWallpaperListener()

        handleRecordingIntent(intent)

        onBackPressedDispatcher.addCallback(this) { /* Bloquear o manejar cierre */ }

        setContent {
            LauncherOrbysTheme(darkTheme = !viewModel.esTemaClaro) {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

                    HomeScreen(
                        viewModel = viewModel,
                        onBluetoothRequest = { showBluetoothDialog = true }
                    )

                    // Overlays de Permisos y Diálogos On-Demand
                    UIPermissionGuard()
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
                    PermissionItem("Lanzador Predeterminado", "Establecer Orbys como inicio principal", viewModel.isDefaultLauncher) {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        viewModel.startAutoCheck({ permissionManager.isDefaultLauncher() }, ::returnToMain)
                    },
                    PermissionItem("Ajustes del Sistema", "Permitir ajuste de brillo y volumen", viewModel.canWriteSettings) {
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                        viewModel.startAutoCheck({ permissionManager.canWriteSettings() }, ::returnToMain)
                    },
                    PermissionItem("Servicio de Accesibilidad", "Activar gestos y acciones rápidas", viewModel.isAccessibilityEnabled) {
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
            title = "¿Grabar con audio?",
            description = "Se requiere acceso al micrófono para capturar sonido.",
            icon = Icons.Default.Mic,
            onGrant = { requestAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
            onDismiss = {
                pendingRecordingIntent?.let { startProjection(it) }
                showAudioDialog = false
                pendingRecordingIntent = null
            },
            secondaryText = "Sin audio"
        )
    }

    @Composable
    private fun UIBluetoothDialog() {
        OnDemandPermissionDialog(
            title = "Permiso de Bluetooth",
            description = "Se requiere permiso para gestionar conexiones inalámbricas.",
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
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            settingsRepository.saveEsClaro(isLight)
            sendBroadcast(Intent(Constants.ACTION_THEME_CHANGED).putExtra("esClaro", isLight))
        }
    }

    private fun loadPreferences() {
        val fondoStr = settingsRepository.getFondo()
        viewModel.updateTheme(settingsRepository.getEsClaro())
        fondoStr?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("content://")) viewModel.setBackground(Uri.parse(it), null)
            else try { viewModel.setBackground(null, Color(it.toULong())) } catch (_: Exception) {}
        }
    }

    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Usamos BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE para que el sistema ignore deslizamientos simples
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun handleRecordingIntent(intent: Intent?) {
        if (intent?.action == Constants.ACTION_START_SCREEN_RECORD) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mpm.createScreenCaptureIntent()
            if (!permissionManager.hasAudioPermission()) {
                pendingRecordingIntent = captureIntent
                showAudioDialog = true
            } else {
                startProjection(captureIntent)
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
        handleRecordingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(internalReceiver) } catch (_: Exception) {}
    }
}
