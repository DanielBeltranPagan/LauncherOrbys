package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.managers.OverlayManager
import com.example.launcherorbys.managers.SystemControlManager

/**
 * Servicio de Accesibilidad que actúa como el núcleo del Launcher.
 * Este servicio permite que el launcher se mantenga persistente y pueda reaccionar a eventos del sistema,
 * además de gestionar las capas de interfaz (overlays) sobre otras aplicaciones.
 * 
 * Implementa [SavedStateRegistryOwner] y [LifecycleOwner] para permitir el uso de Jetpack Compose
 * y componentes conscientes del ciclo de vida dentro del servicio.
 */
class LauncherAccessibilityService : AccessibilityService(), SavedStateRegistryOwner, LifecycleOwner {

    // --- Gestores de Lógica ---
    private lateinit var systemManager: SystemControlManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var appLauncher: AppLauncher

    // --- Configuración de Ciclo de Vida para Compose ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    /**
     * Receptor de eventos del sistema y comandos internos.
     */
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CAMBIO_TEMA" -> {
                    val esClaro = intent.getBooleanExtra("esClaro", true)
                    overlayManager.actualizarColores(esClaro)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                    systemManager.isBluetoothOn = (state == android.bluetooth.BluetoothAdapter.STATE_ON)
                }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> {
                    systemManager.actualizarValoresSistema()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Inicializar el estado guardado y el ciclo de vida
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Inicializar gestores
        appLauncher = AppLauncher(this)
        systemManager = SystemControlManager(this)
        overlayManager = OverlayManager(this, systemManager, appLauncher)
        
        // Aplicar tema inicial basado en preferencias
        val esClaro = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            .getBoolean("esClaro", true)
        overlayManager.actualizarColores(esClaro)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // Configuración detallada del servicio de accesibilidad
        this.serviceInfo = AccessibilityServiceInfo().apply {
            // Escuchar cambios en las ventanas para controlar la visibilidad de la UI
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // Flags para obtener información detallada de las ventanas e IDs de vista
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            notificationTimeout = 100
        }
        
        // Configurar la interfaz visual
        overlayManager.setupOverlays()
        registrarReceptores()
    }

    /**
     * Registra los receptores de intents necesarios para el funcionamiento del sistema.
     */
    private fun registrarReceptores() {
        val filter = IntentFilter().apply {
            addAction("CAMBIO_TEMA")
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            @Suppress("DEPRECATION") 
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        }
        // Usamos RECEIVER_EXPORTED para permitir que comandos externos lleguen al launcher si es necesario
        ContextCompat.registerReceiver(this, receptorComandos, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType
        
        // Gestionar el auto-ocultado de la barra de navegación al cambiar de contexto
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || 
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            val currentWindows = windows 
            val myPackage = this.packageName
            
            // Detectar si hay ventanas de sistema al frente o si el usuario está en otra aplicación
            val hayVentanaDelSistema = currentWindows.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            
            val ventanaAppAlFrente = currentWindows.find { 
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused 
            }
            
            var usuarioAbrioOtraApp = false
            ventanaAppAlFrente?.root?.let { rootNode ->
                val packageNameAlFrente = rootNode.packageName?.toString()
                usuarioAbrioOtraApp = packageNameAlFrente != null && packageNameAlFrente != myPackage
                
                // NOTA: A partir de API 33, recycle() es automático. Para versiones anteriores
                // se recomienda llamarlo, pero para evitar advertencias en compilaciones modernas
                // y dado que el impacto es mínimo en dispositivos actuales, lo omitimos o 
                // lo manejamos con supresión si fuera crítico.
                @Suppress("DEPRECATION")
                rootNode.recycle()
            }

            // Si se detecta un cambio a una aplicación externa o sistema, colapsar el menú si estaba abierto
            if (hayVentanaDelSistema || usuarioAbrioOtraApp) {
                if (overlayManager.navBarExpanded) {
                    overlayManager.toggleNavBarVisibility()
                }
            }
        }
    }

    override fun onInterrupt() {
        // Método requerido por AccessibilityService
    }

    override fun onDestroy() {
        // Limpieza de recursos y finalización del ciclo de vida
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayManager.onDestroy()
        
        try { 
            unregisterReceiver(receptorComandos) 
        } catch (e: Exception) {
            // Ignorar si el receptor no estaba registrado
        }

        super.onDestroy()
    }
}
