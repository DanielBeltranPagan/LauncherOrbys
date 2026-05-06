package com.example.launchercalmado

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
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
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val comando = intent?.getStringExtra("comando")
            when (comando) {
                "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        // TIPO DE VENTANA DE MÁXIMA PRIORIDAD
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
                            }
                            sendBroadcast(Intent("ACCION_BARRA").putExtra("comando", accion))
                            // Aquí procesamos BACK/HOME/RECENTS directamente ya que estamos en el servicio
                            when (accion) {
                                "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
                                "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
                                "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                            }
                        })
                    }
                }
            }
        }

        windowManager.addView(vistaNav, params)

        ContextCompat.registerReceiver(
            this,
            receptorComandos,
            IntentFilter("COMANDO_SISTEMA"),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::vistaNav.isInitialized) {
            windowManager.removeView(vistaNav)
        }
        try {
            unregisterReceiver(receptorComandos)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
