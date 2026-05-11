package com.example.launchercalmado

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launchercalmado.ui.components.NavBar
import com.example.launchercalmado.ui.components.SystemOptionsPanel
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class ServicioBarra : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private lateinit var vistaSystemOptions: ComposeView

    private var systemOptionsVisible by mutableStateOf(false)

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        setupSystemOptionsOverlay()
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (45 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        vistaNav = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ServicioBarra)
            setViewTreeSavedStateRegistryOwner(this@ServicioBarra)

            // Intentamos configurar consumeWindowInsets si está disponible via reflexión o si el SDK es suficiente
            // Para evitar errores de compilación si la versión de la librería es anterior a la que lo introdujo:
            try {
                val method = javaClass.getMethod("setConsumeWindowInsets", Boolean::class.javaPrimitiveType)
                method.invoke(this, false)
            } catch (e: Exception) {
                // Si no existe el método, simplemente lo ignoramos
            }

            setPadding(0, 0, 0, 0)

            setContent {
                LauncherCalmadoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavBar(onActionClicked = { accion ->
                            when (accion) {
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
                                "SYSTEM_OPTIONS" -> toggleSystemOptions()
                            }
                            // Enviamos los broadcasts para que otros componentes (como el drawer) reaccionen
                            sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            sendBroadcast(Intent("COMANDO_SISTEMA").putExtra("comando", accion))
                        })
                    }
                }
            }
        }
        windowManager.addView(vistaNav, params)
    }

    private fun toggleSystemOptions() {
        if (!systemOptionsVisible) {
            systemOptionsVisible = true
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    private fun setupSystemOptionsOverlay() {
        vistaSystemOptions = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ServicioBarra)
            setViewTreeSavedStateRegistryOwner(this@ServicioBarra)

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
                                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onWifiClick = {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onBluetoothClick = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            onAirplaneModeClick = {
                                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                toggleSystemOptions()
                            },
                            modifier = Modifier
                                .padding(bottom = 60.dp)
                                .clickable(interactionSource = null, indication = null) { /* Consume */ }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
        if (::vistaSystemOptions.isInitialized && vistaSystemOptions.parent != null) windowManager.removeView(vistaSystemOptions)
    }
}
