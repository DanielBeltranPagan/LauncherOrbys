package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.managers.OverlayManager
import com.example.launcherorbys.managers.SystemControlManager
import com.example.launcherorbys.utils.Constants

/**
 * Servicio de Accesibilidad que actúa como el núcleo del Launcher Orbys.
 * Proporciona el contexto necesario para los Overlays y escucha eventos globales del sistema.
 */
class LauncherAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    // --- Gestores de Lógica ---
    private lateinit var systemManager: SystemControlManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var appLauncher: AppLauncher

    // --- Implementación de Ciclo de Vida para Compose ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val mViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore = mViewModelStore

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }

    private var lastPackageName: String? = null

    /**
     * Receptor central para coordinar acciones entre componentes del Launcher y el sistema.
     */
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_THEME_CHANGED -> {
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
                Constants.ACTION_RECORDING_STARTED -> {
                    overlayManager.startRecording()
                }
                Constants.ACTION_RECORDING_STOPPED -> {
                    overlayManager.finishRecordingUI()
                }
                Constants.ACTION_NAVBAR_COMMAND -> {
                    val comando = intent.getStringExtra("comando")
                    if (comando == "CLOSE_ALL") {
                        overlayManager.closeAllOverlays()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Restaurar estado y activar ciclo de vida
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Inicializar gestores de lógica
        appLauncher = AppLauncher(this)
        systemManager = SystemControlManager(this)
        overlayManager = OverlayManager(this, systemManager, appLauncher)
        
        // Cargar tema inicial
        val esClaro = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(Constants.KEY_IS_LIGHT_THEME, true)
        overlayManager.actualizarColores(esClaro)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Configuración del servicio (eventos que queremos interceptar)
        this.serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                         AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            notificationTimeout = 100
        }
        
        // Desplegar las capas visuales
        overlayManager.setupOverlays()
        registrarReceptores()
    }

    private fun registrarReceptores() {
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_THEME_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            @Suppress("DEPRECATION") 
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Constants.ACTION_RECORDING_STARTED)
            addAction(Constants.ACTION_RECORDING_STOPPED)
            addAction(Constants.ACTION_NAVBAR_COMMAND)
        }
        ContextCompat.registerReceiver(this, receptorComandos, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType
        
        // --- Detección de grabación de pantalla via notificaciones del sistema ---
        if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            detectarGrabacion(event)
        }

        // --- Auto-ocultar UI al abrir aplicaciones externas ---
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            gestionarAutoOcultado()
        }
    }

    private fun detectarGrabacion(event: AccessibilityEvent) {
        val notificationText = event.text.joinToString(" ").lowercase()
        val notification = event.parcelableData as? android.app.Notification
        val extraText = notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
        val extraTitle = notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
        val combinedText = "$notificationText $extraText $extraTitle"
        
        val esFin = combinedText.contains("saved") || combinedText.contains("guardada") || 
                    combinedText.contains("finished") || combinedText.contains("finalizada")

        if (combinedText.contains("recording") || combinedText.contains("grabando")) {
            if (esFin) overlayManager.finishRecordingUI()
            else overlayManager.startRecording()
        }
    }

    private fun gestionarAutoOcultado() {
        val currentWindows = windows 
        val myPackage = this.packageName
        
        val ventanaAppAlFrente = currentWindows.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
        
        ventanaAppAlFrente?.root?.let { rootNode ->
            val packageNameAlFrente = rootNode.packageName?.toString()
            
            if (packageNameAlFrente != null && packageNameAlFrente != myPackage) {
                // Solo colapsamos si el paquete ha cambiado (nueva app abierta)
                if (packageNameAlFrente != lastPackageName && overlayManager.navBarExpanded) {
                    overlayManager.toggleNavBarVisibility()
                }
                lastPackageName = packageNameAlFrente
            } else if (packageNameAlFrente == myPackage) {
                lastPackageName = myPackage
            }
            
            @Suppress("DEPRECATION") rootNode.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        overlayManager.onDestroy()
        try { unregisterReceiver(receptorComandos) } catch (e: Exception) {}
        super.onDestroy()
    }
}
