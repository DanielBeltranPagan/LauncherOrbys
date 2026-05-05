package com.example.launchercalmado

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.*
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class MainActivity : ComponentActivity() {

    private var appsList by mutableStateOf<List<ResolveInfo>>(emptyList())
    private var drawerVisible by mutableStateOf(false)

    private val receptorBarra = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("comando")) {
                "APPS" -> drawerVisible = !drawerVisible
                "HOME", "BACK", "GOOGLE", "FILES", "RECENTS" -> drawerVisible = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appsList = getInstalledApps()
        
        ContextCompat.registerReceiver(
            this,
            receptorBarra,
            IntentFilter("ACCION_BARRA"),
            ContextCompat.RECEIVER_EXPORTED
        )

        checkAndStartService()


        setContent {
            LauncherCalmadoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.White
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 45.dp)) {
                        if (drawerVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.40f) // Menos ancho
                                    .fillMaxHeight(0.5f) // Altura reducida
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 60.dp) // Más separación de la barra
                                    .clip(RoundedCornerShape(24.dp)) // Bordes redondeados
                                    .background(Color.Gray.copy(alpha = 0.8f)) // Gris transparente
                            ) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    items(appsList) { app ->
                                        Column(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .clickable {
                                                    packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                                                        ?.let { startActivity(it) }
                                                    drawerVisible = false // Cerrar al abrir app
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                bitmap = app.loadIcon(packageManager).toBitmap().asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(45.dp)
                                            )
                                            Text(
                                                app.loadLabel(packageManager).toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = Color.White // Texto blanco sobre fondo gris
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

    private var showingPermissionDialog = false

    override fun onResume() {
        super.onResume()
        checkAndStartService()
    }

    private fun checkAndStartService() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        if (hasOverlay) {
            startService(Intent(this, ServicioBarra::class.java))
        }

        if (showingPermissionDialog) return

        if (!isDefaultLauncher()) {
            showingPermissionDialog = true
            AlertDialog.Builder(this)
                .setTitle("Configurar Launcher")
                .setMessage("Para que la app funcione siempre correctamente, establécela como tu Launcher predeterminado.")
                .setPositiveButton("Configurar") { _, _ ->
                    showingPermissionDialog = false
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Más tarde") { _, _ -> showingPermissionDialog = false }
                .show()
            return
        }

        if (!hasOverlay) {
            showingPermissionDialog = true
            AlertDialog.Builder(this)
                .setTitle("Permiso de Superposición")
                .setMessage("Para mostrar la barra de navegación, activa 'Mostrar sobre otras aplicaciones'.")
                .setPositiveButton("Configurar") { _, _ ->
                    showingPermissionDialog = false
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Ahora no") { _, _ -> showingPermissionDialog = false }
                .setCancelable(false)
                .show()
        } else if (!hasAccessibility) {
            showingPermissionDialog = true
            AlertDialog.Builder(this)
                .setTitle("Permiso de Accesibilidad")
                .setMessage("Para que los botones de Atrás, Inicio y Recientes funcionen, activa el servicio en Ajustes.")
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    showingPermissionDialog = false
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Ahora no") { _, _ -> showingPermissionDialog = false }
                .setCancelable(false)
                .show()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, AccesibilidadService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = splitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun getInstalledApps(): List<ResolveInfo> {
        return packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receptorBarra)
    }
}