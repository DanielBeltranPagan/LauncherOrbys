package com.example.launchercalmado

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
import com.example.launchercalmado.services.LauncherAccessibilityService
import com.example.launchercalmado.ui.home.HomeScreen
import com.example.launchercalmado.ui.home.HomeViewModel
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

/**
 * Actividad principal del Launcher.
 * Se encarga de la inicialización, gestión de permisos, configuración visual y
 * de la comunicación con otros componentes a través de BroadcastReceivers.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var showingPermissionDialog = false

    // Receptor para comandos enviados desde la barra de navegación personalizada
    private val receptorBarra = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("comando")) {
                "HOME", "BACK", "GOOGLE", "FILES", "RECENTS", "APPS" -> viewModel.cerrarTodo()
            }
        }
    }

    // Receptor para detectar cambios en el fondo de pantalla del sistema
    private val receptorWallpaper = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.setBackground(null, null)
            guardarPreferencias("", true)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupTransitions() // Configura transiciones sin animaciones para mayor fluidez
        cargarPreferencias() // Carga la configuración guardada del usuario
        
        // Configuración para que la app se dibuje detrás de las barras del sistema
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        hideStatusBar() // Oculta la barra de estado para un look más limpio

        registerReceivers() // Registra los receptores de eventos
        setupWallpaperColorsListener() // Escucha cambios de colores en el fondo de pantalla
        checkAndStartService() // Verifica permisos necesarios (Overlay, Accesibilidad)

        setContent {
            LauncherCalmadoTheme {
                // Elimina el efecto de "rebote" al final de las listas
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    HomeScreen(
                        viewModel = viewModel,
                        onPersonalizarClick = { abrirWallpaperStyleSistema() }
                    )
                }
            }
        }
    }

    /**
     * Elimina las animaciones de transición al abrir/cerrar la actividad.
     */
    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Registra los BroadcastReceivers necesarios.
     */
    private fun registerReceivers() {
        ContextCompat.registerReceiver(this, receptorBarra, IntentFilter("ACCION_BARRA"), ContextCompat.RECEIVER_EXPORTED)
        registerReceiver(receptorWallpaper, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    /**
     * Escucha los cambios de color del fondo de pantalla para adaptar el tema.
     */
    private fun setupWallpaperColorsListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(this)
            wm.addOnColorsChangedListener({ colors, _ ->
                if (viewModel.uriImagenFondo == null && viewModel.colorSolido == null) {
                    actualizarColoresDesdeSistema(colors)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    /**
     * Abre el selector de fondos de pantalla del sistema.
     */
    private fun abrirWallpaperStyleSistema() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No se pudo abrir el selector del sistema", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica y solicita permisos críticos para el funcionamiento del launcher.
     */
    private fun checkAndStartService() {
        if (showingPermissionDialog) return
        
        // Verifica si es el launcher predeterminado
        if (!isDefaultLauncher()) {
            mostrarDialogoPredeterminado()
            return
        }

        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        // Pide permiso de superposición si no lo tiene
        if (!hasOverlay) { 
            showingPermissionDialog = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) 
        } 
        // Pide activar el servicio de accesibilidad si es necesario
        else if (!hasAccessibility) { 
            showingPermissionDialog = true
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
        }
    }

    /**
     * Muestra un diálogo sugiriendo establecer la app como launcher predeterminado.
     */
    private fun mostrarDialogoPredeterminado() {
        showingPermissionDialog = true
        AlertDialog.Builder(this)
            .setTitle("Configurar Launcher")
            .setMessage("Establécelo como predeterminado para mejor estabilidad.")
            .setPositiveButton("Configurar") { _, _ -> 
                showingPermissionDialog = false
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) 
            }
            .setNegativeButton("Más tarde") { _, _ -> 
                showingPermissionDialog = false 
            }
            .show()
    }

    /**
     * Comprueba si esta aplicación es el launcher actual por defecto.
     */
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    /**
     * Comprueba si el servicio de accesibilidad del launcher está activo.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, LauncherAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * Oculta las barras del sistema (estado y navegación) para modo inmersivo.
     */
    private fun hideStatusBar() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Actualiza el tema (claro/oscuro) basándose en los colores del fondo de pantalla.
     */
    private fun actualizarColoresDesdeSistema(colors: WallpaperColors?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            val isLight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else true
            viewModel.updateTheme(isLight)
            notificarCambioTema(isLight)
        }
    }

    /**
     * Envía un broadcast avisando que el tema ha cambiado.
     */
    private fun notificarCambioTema(isLight: Boolean) {
        val intent = Intent("CAMBIO_TEMA")
        intent.putExtra("esClaro", isLight)
        sendBroadcast(intent)
    }

    /**
     * Guarda las preferencias de fondo y tema en SharedPreferences.
     */
    private fun guardarPreferencias(fondo: String, claro: Boolean) {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        prefs.edit().putString("fondo", fondo).putBoolean("esClaro", claro).apply()
    }

    /**
     * Carga las preferencias guardadas al iniciar.
     */
    private fun cargarPreferencias() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val fondoStr = prefs.getString("fondo", null)
        val esClaro = prefs.getBoolean("esClaro", true)
        viewModel.updateTheme(esClaro)
        
        if (!fondoStr.isNullOrEmpty()) {
            if (fondoStr.startsWith("content://")) { 
                viewModel.setBackground(Uri.parse(fondoStr), null)
            } else { 
                try { 
                    viewModel.setBackground(null, Color(fondoStr.toULong()))
                } catch (e: Exception) {} 
            }
        }
    }

    override fun onResume() { 
        super.onResume()
        setupTransitions()
        hideStatusBar()
        checkAndStartService() 
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupTransitions()
    }

    override fun onBackPressed() {
        // Si hay un menú contextual abierto, lo cierra en lugar de salir
        if (viewModel.mostrarMenuContextual) viewModel.cerrarTodo()
        else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar() // Asegura que las barras sigan ocultas al volver a la app
    }

    override fun onDestroy() { 
        super.onDestroy()
        // Desregistra los receptores para evitar fugas de memoria
        try { unregisterReceiver(receptorBarra) } catch (e: Exception) {}
        try { unregisterReceiver(receptorWallpaper) } catch (e: Exception) {}
    }
}
