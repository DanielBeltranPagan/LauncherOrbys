package com.example.launcherorbys

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.launcherorbys.services.LauncherAccessibilityService
import androidx.lifecycle.lifecycleScope
import com.example.launcherorbys.ui.home.HomeScreen
import com.example.launcherorbys.ui.home.HomeViewModel
import com.example.launcherorbys.ui.setup.PermissionItem
import com.example.launcherorbys.ui.setup.PermissionScreen
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Actividad principal que sirve como punto de entrada y contenedor de la pantalla de inicio.
 * Gestiona permisos, fondos de pantalla y la comunicación con la barra de navegación.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    
    // Estados de permisos para la pantalla de configuración
    private var isDefault by mutableStateOf(false)
    private var canDrawOverlays by mutableStateOf(false)
    private var isAccessibilityOn by mutableStateOf(false)
    private var canWriteSettings by mutableStateOf(false)
    private var initialAllGranted by mutableStateOf(false)

    // Escucha acciones externas para cerrar menús o actualizar UI
    private val receptorBarra = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("comando") != null) viewModel.cerrarTodo()
        }
    }

    // Detecta cambios en el fondo de pantalla del sistema
    private val receptorWallpaper = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.setBackground(null, null)
            savePrefs("", true)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupTransitions()
        loadPrefs()
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        hideStatusBar()

        registerReceivers()
        setupWallpaperListener()
        updatePermissionStates()
        initialAllGranted = isDefault && canDrawOverlays && isAccessibilityOn && canWriteSettings

        setContent {
            LauncherOrbysTheme {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    val allGranted = isDefault && canDrawOverlays && isAccessibilityOn && canWriteSettings

                    if (!allGranted) {
                        val permissionsList = listOf(
                            PermissionItem(
                                "Lanzador Predeterminado",
                                "Establece Orbys como tu pantalla de inicio principal.",
                                isDefault,
                                { 
                                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                    autoReturnCheck { isDefaultLauncher() }
                                }
                            ),
                            PermissionItem(
                                "Superposición de Pantalla",
                                "Permite mostrar la barra de navegación y paneles sobre otras apps.",
                                canDrawOverlays,
                                { 
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                    autoReturnCheck { Settings.canDrawOverlays(this) }
                                }
                            ),
                            PermissionItem(
                                "Ajustes del Sistema",
                                "Permite al launcher controlar el brillo de la pantalla.",
                                canWriteSettings,
                                { 
                                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                                    autoReturnCheck { Settings.System.canWrite(this) }
                                }
                            ),
                            PermissionItem(
                                "Servicio de Accesibilidad",
                                "Necesario para los gestos del sistema (Atrás, Recientes) y la UI.",
                                isAccessibilityOn,
                                { 
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    autoReturnCheck { isAccessibilityEnabled() }
                                }
                            )
                        )
                        
                        PermissionScreen(
                            permissions = permissionsList,
                            onContinue = { /* Ya no es necesario el click */ },
                            allGranted = allGranted
                        )
                    } else {
                        HomeScreen(viewModel = viewModel, onPersonalizarClick = { openWallpaperPicker() })
                    }
                }
            }
        }
    }

    /**
     * Monitoriza en segundo plano si un permiso ha sido concedido y trae la app al frente.
     */
    private fun autoReturnCheck(check: () -> Boolean) {
        lifecycleScope.launch {
            // Esperar un poco a que el usuario llegue a la pantalla de ajustes
            delay(1000)
            while (isActive) {
                if (check()) {
                    // Si el permiso se concedió, actualizar estados y volver a la app
                    updatePermissionStates()
                    val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    startActivity(intent)
                    break
                }
                delay(1000) // Comprobar cada segundo
            }
        }
    }

    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
    }

    private fun registerReceivers() {
        ContextCompat.registerReceiver(this, receptorBarra, IntentFilter("ACCION_BARRA"), ContextCompat.RECEIVER_EXPORTED)
        registerReceiver(receptorWallpaper, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    /**
     * Configura la escucha de cambios de color en el fondo de pantalla del sistema (Android 8.1+).
     * Esto permite que el launcher adapte su tema automáticamente al cambiar el wallpaper.
     */
    private fun setupWallpaperListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ colors, _ ->
                // Solo actualizamos si el usuario no ha establecido un fondo personalizado manual
                if (viewModel.uriImagenFondo == null && viewModel.colorSolido == null) {
                    updateColors(colors)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    /**
     * Abre el selector de fondos de pantalla del sistema.
     */
    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
        } catch (e: Exception) {
            try {
                // Intento alternativo para fondos animados si el anterior falla
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Error abriendo selector", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica que el launcher tenga los permisos necesarios y actualiza el estado reactivo.
     * Comprueba: Lanzador predeterminado, superposición, servicios de accesibilidad y ajustes.
     */
    private fun updatePermissionStates() {
        isDefault = isDefaultLauncher()
        canDrawOverlays = Settings.canDrawOverlays(this)
        isAccessibilityOn = isAccessibilityEnabled()
        canWriteSettings = Settings.System.canWrite(this)
    }

    /**
     * Comprueba si esta aplicación es la pantalla de inicio predeterminada del sistema.
     */
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    /**
     * Comprueba si el servicio de accesibilidad de la aplicación está activado.
     */
    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, LauncherAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * Oculta las barras del sistema (estado y navegación) para una experiencia inmersiva.
     */
    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Actualiza el tema del launcher basándose en los colores del fondo de pantalla actual.
     */
    private fun updateColors(colors: WallpaperColors?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            val isLight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else true
            viewModel.updateTheme(isLight)
            sendBroadcast(Intent("CAMBIO_TEMA").putExtra("esClaro", isLight))
        }
    }

    private fun savePrefs(fondo: String, claro: Boolean) {
        getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit()
            .putString("fondo", fondo)
            .putBoolean("esClaro", claro)
            .apply()
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val fondoStr = prefs.getString("fondo", null)
        val esClaro = prefs.getBoolean("esClaro", true)
        viewModel.updateTheme(esClaro)
        
        fondoStr?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("content://")) viewModel.setBackground(Uri.parse(it), null)
            else try { viewModel.setBackground(null, Color(it.toULong())) } catch (e: Exception) {}
        }
    }

    override fun onResume() { 
        super.onResume()
        setupTransitions()
        hideStatusBar()
        updatePermissionStates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupTransitions()
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
