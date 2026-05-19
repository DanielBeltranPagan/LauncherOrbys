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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.launcherorbys.data.repository.SettingsRepository
import com.example.launcherorbys.managers.PermissionManager
import com.example.launcherorbys.ui.home.HomeScreen
import com.example.launcherorbys.ui.home.HomeViewModel
import com.example.launcherorbys.ui.setup.OnDemandPermissionDialog
import com.example.launcherorbys.ui.setup.PermissionDialog
import com.example.launcherorbys.ui.setup.PermissionItem
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import com.example.launcherorbys.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Actividad principal del Launcher Orbys.
 * Responsable del ciclo de vida de la UI principal y la gestión de permisos críticos bajo demanda.
 */
class MainActivity : ComponentActivity() {

    // --- Repositorios y Gestores ---
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var permissionManager: PermissionManager
    private val viewModel: HomeViewModel by viewModels()
    
    // --- Estados Reactivos de Permisos ---
    private var isDefault by mutableStateOf(false)
    private var isAccessibilityOn by mutableStateOf(false)
    private var canWriteSettings by mutableStateOf(false)
    private var hasBluetoothPermission by mutableStateOf(false)
    
    // --- Lógica de Grabación (On-Demand) ---
    private var showAudioDialog by mutableStateOf(false)
    private var pendingRecordingIntent by mutableStateOf<Intent?>(null)

    private val requestAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingRecordingIntent?.let { startProjection(it) }
        showAudioDialog = false
        pendingRecordingIntent = null
    }

    private val requestBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionStates() }

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
    private val receptorBarra = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("comando") != null) viewModel.cerrarTodo()
        }
    }

    private val receptorWallpaper = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.setBackground(null, null)
            settingsRepository.saveFondo("")
            settingsRepository.saveEsClaro(true)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsRepository = SettingsRepository(this)
        permissionManager = PermissionManager(this)
        
        setupTransitions()
        loadPreferences()
        
        intent?.also { handleRecordingIntent(it) }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        hideStatusBar()

        registerReceivers()
        setupWallpaperListener()
        updatePermissionStates()

        setContent {
            LauncherOrbysTheme(darkTheme = !viewModel.esTemaClaro) {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    
                    HomeScreen(viewModel = viewModel, onPersonalizarClick = { openWallpaperPicker() })

                    // Gestión de Permisos Críticos
                    val allGranted = isDefault && isAccessibilityOn && canWriteSettings && hasBluetoothPermission
                    if (!allGranted) {
                        UIPermissionOverlay()
                    }

                    // Gestión de Grabación con Audio
                    if (showAudioDialog) {
                        UIAudioDialog()
                    }
                }
            }
        }
    }

    @Composable
    private fun UIPermissionOverlay() {
        val permissionsList = remember(isDefault, canWriteSettings, hasBluetoothPermission, isAccessibilityOn) {
            mutableListOf<PermissionItem>().apply {
                add(PermissionItem("Inicio Orbys", "Pantalla principal", isDefault) {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    autoReturnCheck { permissionManager.isDefaultLauncher() }
                })
                add(PermissionItem("Brillo", "Control de pantalla", canWriteSettings) {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                    autoReturnCheck { permissionManager.canWriteSettings() }
                })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(PermissionItem("Bluetooth", "Dispositivos cercanos", hasBluetoothPermission) {
                        requestBluetoothLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
                    })
                }
                add(PermissionItem("Accesibilidad", "Gestos y barras", isAccessibilityOn) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    autoReturnCheck { permissionManager.isAccessibilityEnabled() }
                })
            }
        }
        PermissionDialog(permissions = permissionsList, onDismiss = {})
    }

    @Composable
    private fun UIAudioDialog() {
        OnDemandPermissionDialog(
            title = "¿Grabar con audio?",
            description = "Se requiere acceso al micrófono.",
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

    private fun autoReturnCheck(check: () -> Boolean) {
        lifecycleScope.launch {
            delay(800)
            while (isActive) {
                if (check()) {
                    updatePermissionStates()
                    startActivity(Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    })
                    break
                }
                delay(1000)
            }
        }
    }

    private fun updatePermissionStates() {
        isDefault = permissionManager.isDefaultLauncher()
        isAccessibilityOn = permissionManager.isAccessibilityEnabled()
        canWriteSettings = permissionManager.canWriteSettings()
        hasBluetoothPermission = permissionManager.hasBluetoothPermission()
    }

    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
    }

    private fun registerReceivers() {
        ContextCompat.registerReceiver(this, receptorBarra, IntentFilter(Constants.ACTION_NAVBAR_COMMAND), ContextCompat.RECEIVER_EXPORTED)
        registerReceiver(receptorWallpaper, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
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

    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Error en selector", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
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
        val esClaro = settingsRepository.getEsClaro()
        viewModel.updateTheme(esClaro)
        fondoStr?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("content://")) viewModel.setBackground(Uri.parse(it), null)
            else try { viewModel.setBackground(null, Color(it.toULong())) } catch (e: Exception) {}
        }
    }

    override fun onResume() { 
        super.onResume()
        hideStatusBar()
        updatePermissionStates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecordingIntent(intent)
    }

    private fun handleRecordingIntent(intent: Intent) {
        if (intent.action == Constants.ACTION_START_SCREEN_RECORD) {
            val hasAudio = permissionManager.hasAudioPermission()
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mpm.createScreenCaptureIntent()

            if (!hasAudio) {
                pendingRecordingIntent = captureIntent
                showAudioDialog = true
            } else {
                startProjection(captureIntent)
            }
        }
    }

    private fun startProjection(intent: Intent) {
        screenCaptureLauncher.launch(intent)
    }

    override fun onBackPressed() {
        if (viewModel.mostrarMenuContextual) viewModel.cerrarTodo()
        else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() { 
        super.onDestroy()
        try { unregisterReceiver(receptorBarra) } catch (e: Exception) {}
        try { unregisterReceiver(receptorWallpaper) } catch (e: Exception) {}
    }
}
