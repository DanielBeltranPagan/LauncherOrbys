package com.example.launcherorbys

import android.app.AlertDialog
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
import com.example.launcherorbys.ui.home.HomeScreen
import com.example.launcherorbys.ui.home.HomeViewModel
import com.example.launcherorbys.ui.theme.LauncherOrbysTheme

/**
 * Actividad principal que sirve como punto de entrada y contenedor de la pantalla de inicio.
 * Gestiona permisos, fondos de pantalla y la comunicación con la barra de navegación.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var showingPermissionDialog = false

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
        checkPermissions()

        setContent {
            LauncherOrbysTheme {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    HomeScreen(viewModel = viewModel, onPersonalizarClick = { openWallpaperPicker() })
                }
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

    private fun setupWallpaperListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ colors, _ ->
                if (viewModel.uriImagenFondo == null && viewModel.colorSolido == null) {
                    updateColors(colors)
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
                Toast.makeText(this, "Error abriendo selector", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica que el launcher tenga los permisos necesarios (Superposición, Accesibilidad)
     * y sea el launcher predeterminado.
     */
    private fun checkPermissions() {
        if (showingPermissionDialog) return
        
        if (!isDefaultLauncher()) {
            showLauncherDialog()
            return
        }

        if (!Settings.canDrawOverlays(this)) { 
            showingPermissionDialog = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) 
        } else if (!isAccessibilityEnabled()) { 
            showingPermissionDialog = true
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
        }
    }

    private fun showLauncherDialog() {
        showingPermissionDialog = true
        AlertDialog.Builder(this)
            .setTitle("Configurar Launcher")
            .setMessage("Pon Launcher Orbys como predeterminado para que funcione mejor.")
            .setPositiveButton("Ir") { _, _ -> 
                showingPermissionDialog = false
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) 
            }
            .setNegativeButton("Luego") { _, _ -> showingPermissionDialog = false }
            .show()
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

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
        checkPermissions() 
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
