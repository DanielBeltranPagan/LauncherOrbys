package com.example.launchercalmado

import android.app.AlertDialog
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class MainActivity : ComponentActivity() {

    private var mostrarMenuContextual by mutableStateOf(false)
    private var uriImagenFondo by mutableStateOf<Uri?>(null)
    private var colorSolido by mutableStateOf<Color?>(null)
    private var esTemaClaro by mutableStateOf(true)

    private val receptorBarra = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("comando")) {
                "HOME", "BACK", "GOOGLE", "FILES", "RECENTS", "APPS" -> cerrarTodo()
            }
        }
    }

    private val receptorWallpaper = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            uriImagenFondo = null
            colorSolido = null
            guardarPreferencias("", true)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cargarPreferencias()
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        ContextCompat.registerReceiver(this, receptorBarra, IntentFilter("ACCION_BARRA"), ContextCompat.RECEIVER_EXPORTED)
        registerReceiver(receptorWallpaper, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(this)
            wm.addOnColorsChangedListener({ colors, _ ->
                if (uriImagenFondo == null && colorSolido == null) actualizarColoresDesdeSistema(colors)
            }, Handler(Looper.getMainLooper()))
        }

        checkAndStartService()

        setContent {
            LauncherCalmadoTheme {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { mostrarMenuContextual = true },
                                    onTap = { cerrarTodo() }
                                )
                            },
                        color = colorSolido ?: Color.Transparent
                    ) {
                        // Fondo personalizado si existe
                        uriImagenFondo?.let { uri ->
                            val bitmap = remember(uri) { 
                                try {
                                    val inputStream = contentResolver.openInputStream(uri)
                                    BitmapFactory.decodeStream(inputStream)
                                } catch (e: Exception) { null }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (mostrarMenuContextual) {
                                MenuContextual(
                                    onPersonalizarClick = {
                                        abrirWallpaperStyleSistema()
                                        mostrarMenuContextual = false
                                    },
                                    onDismiss = { mostrarMenuContextual = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BoxScope.MenuContextual(onPersonalizarClick: () -> Unit, onDismiss: () -> Unit) {
        // Fondo invisible para cerrar al tocar fuera
        Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
        
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .width(220.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                TextButton(
                    onClick = onPersonalizarClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Fondo y estilo",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    private fun abrirWallpaperStyleSistema() {
        try {
            // Intent estándar para abrir el selector de fondos y estilos en Android 12+
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback para dispositivos que no soportan el intent directo
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No se pudo abrir el selector del sistema", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cerrarTodo() {
        mostrarMenuContextual = false
    }

    private fun actualizarColoresDesdeSistema(colors: WallpaperColors?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            esTemaClaro = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else true
            notificarCambioTema()
        }
    }

    override fun onBackPressed() {
        if (mostrarMenuContextual) mostrarMenuContextual = false
        else super.onBackPressed()
    }

    private fun notificarCambioTema() {
        val intent = Intent("CAMBIO_TEMA")
        intent.putExtra("esClaro", esTemaClaro)
        sendBroadcast(intent)
    }

    private fun guardarPreferencias(fondo: String, claro: Boolean) {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        prefs.edit().putString("fondo", fondo).putBoolean("esClaro", claro).apply()
    }

    private fun cargarPreferencias() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val fondoStr = prefs.getString("fondo", null)
        esTemaClaro = prefs.getBoolean("esClaro", true)
        if (fondoStr != null && fondoStr.isNotEmpty()) {
            if (fondoStr.startsWith("content://")) { uriImagenFondo = Uri.parse(fondoStr); colorSolido = null }
            else { try { colorSolido = Color(fondoStr.toULong()) } catch (e: Exception) {} }
        }
    }

    override fun onResume() { super.onResume(); checkAndStartService() }

    private fun checkAndStartService() {
        if (showingPermissionDialog) return
        if (!isDefaultLauncher()) {
            showingPermissionDialog = true
            AlertDialog.Builder(this).setTitle("Configurar Launcher").setMessage("Establécelo como predeterminado para mejor estabilidad.")
                .setPositiveButton("Configurar") { _, _ -> showingPermissionDialog = false; startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .setNegativeButton("Más tarde") { _, _ -> showingPermissionDialog = false }.show()
            return
        }
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        if (!hasOverlay) { showingPermissionDialog = true; startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        else if (!hasAccessibility) { showingPermissionDialog = true; startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private var showingPermissionDialog = false
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AccesibilidadService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    override fun onDestroy() { 
        super.onDestroy()
        try { unregisterReceiver(receptorBarra) } catch (e: Exception) {}
        try { unregisterReceiver(receptorWallpaper) } catch (e: Exception) {}
    }
}
