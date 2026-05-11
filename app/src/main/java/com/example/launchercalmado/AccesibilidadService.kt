package com.example.launchercalmado

import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.provider.AlarmClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launchercalmado.ui.components.NavBar
import com.example.launchercalmado.ui.components.StatusBar
import com.example.launchercalmado.ui.components.SystemOptionsPanel
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class AccesibilidadService : AccessibilityService(), SavedStateRegistryOwner, LifecycleOwner {

    // --- Gestores de Ventanas y Vistas ---
    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaStatus: ComposeView
    private lateinit var vistaDrawer: ComposeView
    private lateinit var vistaSystemOptions: ComposeView
    
    // --- Estados de Apariencia ---
    private var iconColorStatus by mutableStateOf(Color.White)
    private var iconColorNav by mutableStateOf(Color.White)
    private var navBarBackground by mutableStateOf(Color.Black)

    // --- Estados del Drawer (Lista de Apps) ---
    private var drawerVisible by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var appsList by mutableStateOf<List<ResolveInfo>>(emptyList())

    // --- Estados del Panel de Sistema ---
    private var systemOptionsVisible by mutableStateOf(false)
    private var currentBrightness by mutableStateOf(0.5f)
    private var currentVolume by mutableStateOf(0.5f)
    private lateinit var audioManager: AudioManager

    // --- Lifecycle Support ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    // --- Receptor de Comandos ---
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "CAMBIO_TEMA") {
                val esClaro = intent.getBooleanExtra("esClaro", true)
                actualizarColores(esClaro)
                return
            }
            manejarComando(intent?.getStringExtra("comando"))
        }
    }

    private fun manejarComando(comando: String?) {
        when (comando) {
            "BACK" -> {
                if (drawerVisible) toggleDrawer()
                else performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "HOME" -> {
                if (drawerVisible) toggleDrawer()
                // Enviamos la acción de HOME al sistema
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Y forzamos el inicio de nuestra actividad sin animación para asegurar que sea instantáneo
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
            }
            "RECENTS" -> {
                if (drawerVisible) toggleDrawer()
                if (systemOptionsVisible) toggleSystemOptions()
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            "APPS" -> toggleDrawer()
            "SYSTEM_OPTIONS" -> toggleSystemOptions()
        }
    }

    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            actualizarValoresSistema()
            systemOptionsVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(vistaSystemOptions, params)
        } else {
            if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) {
                windowManager.removeView(vistaSystemOptions)
            }
            systemOptionsVisible = false
        }
    }

    private fun actualizarValoresSistema() {
        // Volumen
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = curVol.toFloat() / maxVol
        }

        // Brillo
        try {
            val curBright = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness = curBright.toFloat() / 255f
        } catch (e: Exception) {
            currentBrightness = 0.5f
        }
    }

    private fun cambiarVolumen(valor: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (valor * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        currentVolume = valor
    }

    private fun cambiarBrillo(valor: Float) {
        if (Settings.System.canWrite(this)) {
            val newBright = (valor * 255).toInt()
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBright)
            currentBrightness = valor
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(this, "Concede permiso para cambiar el brillo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDrawer() {
        if (!drawerVisible) {
            appsList = getInstalledApps()
            drawerVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(vistaDrawer, params)
        } else {
            if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) {
                windowManager.removeView(vistaDrawer)
            }
            drawerVisible = false
            searchQuery = ""
        }
    }

    private fun actualizarColores(esClaro: Boolean) {
        if (esClaro) {
            iconColorStatus = Color.Black
            navBarBackground = Color.Black
            iconColorNav = Color.White
        } else {
            iconColorStatus = Color.White
            navBarBackground = Color.White
            iconColorNav = Color.Black
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val esClaro = prefs.getBoolean("esClaro", true)
        actualizarColores(esClaro)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val density = resources.displayMetrics.density

        setupDrawerOverlay()
        setupSystemOptionsOverlay()
        setupBarraNavegacion(density)
        setupBarraStatus(density)
        registrarReceptores()
    }

    // --- Configuración de Overlays ---
    private fun setupDrawerOverlay() {
        vistaDrawer = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { toggleDrawer() },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        DrawerContenido()
                    }
                }
            }
        }
    }

    @Composable
    private fun DrawerContenido() {
        val filteredApps = remember(searchQuery, appsList) {
            if (searchQuery.isEmpty()) appsList
            else appsList.filter { it.loadLabel(packageManager).toString().contains(searchQuery, ignoreCase = true) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .fillMaxHeight(0.5f)
                .padding(bottom = 100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.DarkGray.copy(alpha = 0.95f))
                .clickable(interactionSource = null, indication = null) { /* Consume clics en el panel */ }
        ) {
            Column {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text("Buscar...", color = Color.Gray, style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(8.dp)) {
                    items(filteredApps) { app ->
                        AppItem(app)
                    }
                }
            }
        }
    }

    @Composable
    private fun AppItem(app: ResolveInfo) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            startActivity(it)
                        }
                        toggleDrawer()
                    }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = app.loadIcon(packageManager).toBitmap().asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.size(35.dp)
                )
                Text(
                    app.loadLabel(packageManager).toString(), 
                    style = MaterialTheme.typography.labelSmall, 
                    maxLines = 1, 
                    color = Color.White
                )
            }
        }
    }

    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

            setContent {
                LauncherCalmadoTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { toggleSystemOptions() },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        SystemOptionsPanel(
                            onSettingsClick = {
                                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            onWifiClick = {
                                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            onBluetoothClick = {
                                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            onAirplaneModeClick = {
                                startActivity(Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                                toggleSystemOptions()
                            },
                            currentBrightness = currentBrightness,
                            onBrightnessChange = { cambiarBrillo(it) },
                            currentVolume = currentVolume,
                            onVolumeChange = { cambiarVolumen(it) },
                            modifier = Modifier
                                .padding(bottom = 60.dp)
                                .clickable(interactionSource = null, indication = null) { /* Consume */ }
                        )
                    }
                }
            }
        }
    }

    private fun setupBarraStatus(density: Float) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (40 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        vistaStatus = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

            // Evitar saltos por insets del sistema
            try {
                val method = javaClass.getMethod("setConsumeWindowInsets", Boolean::class.javaPrimitiveType)
                method.invoke(this, false)
            } catch (e: Exception) {}

            setContent {
                LauncherCalmadoTheme {
                    StatusBar(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        windowManager.addView(vistaStatus, params)
    }

    private fun setupBarraNavegacion(density: Float) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (45 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

            // Evitar saltos por insets del sistema
            try {
                val method = javaClass.getMethod("setConsumeWindowInsets", Boolean::class.javaPrimitiveType)
                method.invoke(this, false)
            } catch (e: Exception) {}

            setContent {
                LauncherCalmadoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(
                            onActionClicked = { accion ->
                                when (accion) {
                                    "APPS" -> {
                                        // No llamamos a toggleDrawer aquí para evitar doble ejecución
                                        // El broadcast que enviamos abajo será captado por el receptor de este mismo servicio
                                    }
                                    "GOOGLE" -> {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        try { startActivity(intent) } catch (e: Exception) {}
                                    }
                                    "FILES" -> {
                                        val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                                            ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        try { startActivity(intent) } catch (e: Exception) {}
                                    }
                                    "CLOCK" -> {
                                        try {
                                            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@AccesibilidadService, "No se pudo abrir el reloj", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            }, 
                            iconColor = iconColorNav,
                            backgroundColor = navBarBackground
                        )
                    }
                }
            }
        }

        windowManager.addView(vistaNav, params)
    }

    private fun getInstalledApps(): List<ResolveInfo> {
        return packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }

    private fun registrarReceptores() {
        val themeFilter = IntentFilter("CAMBIO_TEMA")
        themeFilter.addAction("COMANDO_SISTEMA")
        themeFilter.addAction("ACCION_BARRA")
        ContextCompat.registerReceiver(this, receptorComandos, themeFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
        if (::vistaStatus.isInitialized) windowManager.removeView(vistaStatus)
        if (::vistaDrawer.isInitialized && vistaDrawer.parent != null) windowManager.removeView(vistaDrawer)
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
        
        try {
            unregisterReceiver(receptorComandos)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
