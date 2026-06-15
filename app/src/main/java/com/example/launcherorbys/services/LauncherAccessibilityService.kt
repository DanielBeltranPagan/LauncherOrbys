package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.launcherorbys.data.repository.SettingsRepository
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.managers.OverlayManager
import com.example.launcherorbys.managers.SystemControlManager
import com.example.launcherorbys.utils.Constants
import kotlinx.coroutines.launch

/**
 * Servicio de Accesibilidad que actúa como el motor central del Launcher Orbys.
 *
 * Este servicio es fundamental para el funcionamiento del launcher, ya que permite:
 * - Dibujar superposiciones (Overlays) sobre otras aplicaciones.
 * - Detectar cuándo se abren o cierran aplicaciones para gestionar la visibilidad de la barra de navegación.
 * - Interceptar eventos de teclas globales (como el botón Home o Recientes) para asegurar la experiencia del launcher.
 * - Ejecutar acciones de sistema (volver atrás, ir a home, abrir notificaciones) sin necesidad de root.
 * - Sincronizar el estado de grabación de pantalla mediante la lectura de notificaciones del sistema.
 *
 * Implementa múltiples interfaces de ciclo de vida de Android para permitir la integración
 * fluida con Jetpack Compose dentro de los Overlays.
 */
class LauncherAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    // --- Gestores de Lógica ---
    /** Repositorio para acceder a las configuraciones persistentes. */
    private lateinit var settingsRepository: SettingsRepository
    /** Gestor de controles de hardware y ajustes de sistema (brillo, volumen). */
    private lateinit var systemManager: SystemControlManager
    /** Gestor encargado de la creación y mantenimiento de las ventanas flotantes (UI). */
    private lateinit var overlayManager: OverlayManager
    /** Gestor para lanzar aplicaciones y gestionar el historial. */
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

    /** Nombre del paquete de la última aplicación detectada en primer plano. */
    private var lastPackageName: String? = null

    /**
     * Receptor de transmisiones encargado de coordinar la comunicación entre la
     * actividad principal y este servicio de fondo.
     */
    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_THEME_CHANGED -> {
                    val esClaro = intent.getBooleanExtra("esClaro", true)
                    overlayManager.actualizarColores(esClaro)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    systemManager.actualizarValoresSistema()
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
                Constants.ACTION_SETTINGS_SEARCH -> {
                    val query = intent.getStringExtra("query") ?: return
                    performSettingsSearch(query)
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
        settingsRepository = SettingsRepository(this)
        appLauncher = AppLauncher(this)
        systemManager = SystemControlManager(this)
        overlayManager = OverlayManager(this, systemManager, appLauncher)

        // Observar cambios de tema desde DataStore
        lifecycleScope.launch {
            settingsRepository.esClaroFlow.collect { esClaro ->
                overlayManager.actualizarColores(esClaro)
            }
        }
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
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Constants.ACTION_RECORDING_STARTED)
            addAction(Constants.ACTION_RECORDING_STOPPED)
            addAction(Constants.ACTION_NAVBAR_COMMAND)
            addAction(Constants.ACTION_SETTINGS_SEARCH)
        }
        ContextCompat.registerReceiver(this, receptorComandos, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Refrescamos los recursos del contexto base para asegurar que el sistema no use caché antigua
        try {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(newConfig, resources.displayMetrics)
        } catch (_: Exception) {}

        if (::overlayManager.isInitialized) {
            overlayManager.onConfigurationChanged(newConfig)
        }
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

        // --- BLOQUEAR PANEL DE RECIENTES (Gesto o Botón) ---
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""

            // Detección de panel de aplicaciones recientes (SystemUI o Launchers con QuickStep)
            val isSystemUI = pkg.contains("systemui") || pkg.contains("quickstep")
            val isRecents = cls.contains("Recents") || cls.contains("Overview") || cls.contains("RecentApps")

            if (isSystemUI && isRecents) {
                // Volver a Home si se intenta abrir Recientes
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    /**
     * Intercepta eventos de teclas físicas o virtuales.
     * Requiere flagRequestFilterKeyEvents en la configuración XML.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        
        // Bloquear botones/gestos de navegación si estamos en el Launcher
        val isLauncherInFront = lastPackageName == packageName || lastPackageName == null
        
        if (isLauncherInFront && (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH || event.keyCode == KeyEvent.KEYCODE_BACK)) {
            return true
        }
        
        return super.onKeyEvent(event)
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

    /**
     * Busca directamente en Settings.
     * Intenta usar el Intent específico de búsqueda para que sea instantáneo.
     */
    private fun performSettingsSearch(query: String) {
        // 1. Intentar el "Atajo Directo" (funciona en Android 10+)
        val intent = Intent("android.settings.APP_SEARCH_SETTINGS").apply {
            putExtra("query", query) // Extra estándar
            putExtra("search_query", query) // Extra para algunas capas (Samsung/Pixel)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
            
            // 2. Apoyo del AccessibilityService por si el Intent abre el buscador vacío
            Thread {
                var rootNode: AccessibilityNodeInfo? = null
                // Re-intentar encontrar la ventana activa durante 2 segundos
                for (i in 0..10) {
                    Thread.sleep(200) 
                    rootNode = rootInActiveWindow
                    if (rootNode != null) break
                }
                
                if (rootNode == null) return@Thread
                
                // Intentar encontrar el campo editable varias veces por si la UI tarda en pintar
                for (i in 0..5) {
                    val currentRoot = rootInActiveWindow ?: rootNode
                    val editNode = encontrarEditable(currentRoot)
                    if (editNode != null) {
                        if (editNode.text?.toString() != query) {
                            val arguments = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                            }
                            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }
                        editNode.recycle()
                        break
                    }
                    Thread.sleep(200)
                }
                rootNode.recycle()
            }.start()

        } catch (e: Exception) {
            // Fallback si el intent anterior falla: abrir ajustes normal y buscar
            abrirAjustesYBuscar(query)
        }
    }

    private fun abrirAjustesYBuscar(query: String) {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            Thread {
                var rootNode: AccessibilityNodeInfo? = null
                for (i in 0..10) {
                    Thread.sleep(300)
                    rootNode = rootInActiveWindow
                    if (rootNode != null) break
                }
                
                if (rootNode == null) return@Thread
                
                // Buscar botón de búsqueda (lupa) y pulsarlo
                val searchButtons = mutableListOf<AccessibilityNodeInfo>()
                buscarNodoPorViewId(rootNode, "search", searchButtons)
                buscarNodoPorContentDescription(rootNode, "search", searchButtons)
                
                searchButtons.firstOrNull()?.let {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    it.recycle()
                    
                    // Esperar a que el buscador se abra y buscar el campo editable
                    for (j in 0..5) {
                        Thread.sleep(300)
                        val newRoot = rootInActiveWindow
                        if (newRoot != null) {
                            val edit = encontrarEditable(newRoot)
                            if (edit != null) {
                                val args = android.os.Bundle().apply {
                                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                                }
                                edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                                edit.recycle()
                                newRoot.recycle()
                                break
                            }
                            newRoot.recycle()
                        }
                    }
                }
                rootNode.recycle()
            }.start()
        } catch (ex: Exception) {}
    }

    private fun encontrarEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Opción A: El que tiene el foco de entrada actualmente
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && (focused.isEditable || focused.className?.contains("EditText") == true)) {
            return focused
        }

        // Opción B: Buscar cualquier nodo que sea editable o EditText
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        buscarNodosEditables(root, editTexts)
        return editTexts.firstOrNull()
    }

    private fun buscarNodosEditables(
        node: AccessibilityNodeInfo?,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        if (node.isEditable || node.className?.contains("EditText") == true || node.className?.contains("SearchView") == true) {
            resultados.add(AccessibilityNodeInfo(node))
        }

        for (i in 0 until (node.childCount ?: 0)) {
            val child = node.getChild(i) ?: continue
            buscarNodosEditables(child, resultados)
            child.recycle()
        }
    }

    /**
     * Busca nodos por su resource ID (viewId).
     */
    private fun buscarNodoPorViewId(
        node: AccessibilityNodeInfo?,
        viewId: String,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        if (node.viewIdResourceName?.contains(viewId) == true) {
            resultados.add(AccessibilityNodeInfo(node))
        }

        for (i in 0 until (node.childCount ?: 0)) {
            val child = node.getChild(i) ?: continue
            buscarNodoPorViewId(child, viewId, resultados)
            child.recycle()
        }
    }

    /**
     * Busca nodos por su contentDescription.
     */
    private fun buscarNodoPorContentDescription(
        node: AccessibilityNodeInfo?,
        description: String,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        if (node.contentDescription?.contains(description, ignoreCase = true) == true) {
            resultados.add(AccessibilityNodeInfo(node))
        }

        for (i in 0 until (node.childCount ?: 0)) {
            val child = node.getChild(i) ?: continue
            buscarNodoPorContentDescription(child, description, resultados)
            child.recycle()
        }
    }

    /**
     * Busca nodos EditText vacíos (campos de búsqueda típicamente).
     */
    private fun buscarNodosDeTexto(
        node: AccessibilityNodeInfo?,
        text: String,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        // Buscar EditText o campos similares
        if ((node.className?.contains("EditText") == true ||
                    node.className?.contains("SearchView") == true) &&
            (node.text?.toString()?.isEmpty() == true)) {
            resultados.add(AccessibilityNodeInfo(node))
        }

        for (i in 0 until (node.childCount ?: 0)) {
            val child = node.getChild(i) ?: continue
            buscarNodosDeTexto(child, text, resultados)
            child.recycle()
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
