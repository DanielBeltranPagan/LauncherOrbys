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
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class AccesibilidadService : AccessibilityService(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaStatus: ComposeView
    private lateinit var vistaDrawer: ComposeView
    
    // Estados adaptativos
    private var iconColorStatus by mutableStateOf(Color.White)
    private var iconColorNav by mutableStateOf(Color.White)
    private var navBarBackground by mutableStateOf(Color.Black)

    private var estaUSB by mutableStateOf(false)
    private var wifiActivo by mutableStateOf(false)
    private var bluetoothActivo by mutableStateOf(false)

    // Estados del Drawer Global
    private var drawerVisible by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var appsList by mutableStateOf<List<ResolveInfo>>(emptyList())

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "CAMBIO_TEMA") {
                val esClaro = intent.getBooleanExtra("esClaro", true)
                actualizarColores(esClaro)
                return
            }
            val comando = intent?.getStringExtra("comando")
            when (comando) {
                "BACK" -> {
                    if (drawerVisible) toggleDrawer()
                    else performGlobalAction(GLOBAL_ACTION_BACK)
                }
                "HOME" -> {
                    if (drawerVisible) toggleDrawer()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                "RECENTS" -> {
                    if (drawerVisible) toggleDrawer()
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
                "APPS" -> toggleDrawer()
            }
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

    private val receptorSensores = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> estaUSB = true
                Intent.ACTION_POWER_DISCONNECTED -> estaUSB = false
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    wifiActivo = state == WifiManager.WIFI_STATE_ENABLED
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    bluetoothActivo = state == BluetoothAdapter.STATE_ON
                }
            }
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
        val density = resources.displayMetrics.density

        setupDrawerOverlay()
        setupBarraNavegacion(density)
        setupBarraStatus(density)
        registrarReceptores()
    }

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
                                .clickable(interactionSource = null, indication = null) { /* Consume */ }
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
                                                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                                }
                            }
                        }
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        vistaStatus = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

            setContent {
                LauncherCalmadoTheme {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (estaUSB) {
                            Icon(Icons.Default.Usb, null, tint = if(iconColorStatus == Color.Black) Color.Blue else Color.Cyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (bluetoothActivo) {
                            Icon(Icons.Default.Bluetooth, null, tint = iconColorStatus, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (wifiActivo) {
                            Icon(Icons.Default.Wifi, null, tint = iconColorStatus, modifier = Modifier.size(20.dp))
                        }
                    }
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccesibilidadService)
            setViewTreeSavedStateRegistryOwner(this@AccesibilidadService)

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
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try { startActivity(intent) } catch (e: Exception) {}
                                    }
                                    "FILES" -> {
                                        val intent = packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                                            ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try { startActivity(intent) } catch (e: Exception) {}
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
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(receptorSensores, filter)

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
        
        try {
            unregisterReceiver(receptorComandos)
            unregisterReceiver(receptorSensores)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
