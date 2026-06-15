package com.example.launcherorbys.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
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
 */
class LauncherAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private lateinit var repositorioAjustes: SettingsRepository
    private lateinit var gestorSistema: SystemControlManager
    private lateinit var gestorCapas: OverlayManager
    private lateinit var lanzadorApp: AppLauncher

    private val registroCicloVida = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registroCicloVida

    private val controladorRegistroEstadoGuardado = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = controladorRegistroEstadoGuardado.savedStateRegistry

    private val almacenViewModel = ViewModelStore()
    override val viewModelStore: ViewModelStore = almacenViewModel

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }

    private var ultimoNombrePaquete: String? = null

    private val receptorComandos = object : BroadcastReceiver() {
        override fun onReceive(contexto: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_THEME_CHANGED -> {
                    val esClaro = intent.getBooleanExtra("esClaro", true)
                    gestorCapas.actualizarColores(esClaro)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    gestorSistema.actualizarValoresSistema()
                }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> {
                    gestorSistema.actualizarValoresSistema()
                }
                Constants.ACTION_NAVBAR_COMMAND -> {
                    val comando = intent.getStringExtra("comando")
                    if (comando == "CLOSE_ALL") {
                        gestorCapas.cerrarTodasLasCapas()
                    }
                }
                Constants.ACTION_SETTINGS_SEARCH -> {
                    val consulta = intent.getStringExtra("query") ?: return
                    realizarBusquedaAjustes(consulta)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        controladorRegistroEstadoGuardado.performRestore(null)
        registroCicloVida.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        repositorioAjustes = SettingsRepository(this)
        lanzadorApp = AppLauncher(this)
        gestorSistema = SystemControlManager(this)
        gestorCapas = OverlayManager(this, gestorSistema, lanzadorApp)

        lifecycleScope.launch {
            repositorioAjustes.flujoEsClaro.collect { esClaro ->
                gestorCapas.actualizarColores(esClaro)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registroCicloVida.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registroCicloVida.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

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

        gestorCapas.configurarCapas()
        registrarReceptores()
    }

    private fun registrarReceptores() {
        val filtro = IntentFilter().apply {
            addAction(Constants.ACTION_THEME_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Constants.ACTION_NAVBAR_COMMAND)
            addAction(Constants.ACTION_SETTINGS_SEARCH)
        }
        ContextCompat.registerReceiver(this, receptorComandos, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onConfigurationChanged(nuevaConfiguracion: Configuration) {
        super.onConfigurationChanged(nuevaConfiguracion)
        
        try {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(nuevaConfiguracion, resources.displayMetrics)
        } catch (_: Exception) {}

        if (::gestorCapas.isInitialized) {
            gestorCapas.alCambiarConfiguracion(nuevaConfiguracion)
        }
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        val tipo = evento?.eventType

        if (tipo == AccessibilityEvent.TYPE_WINDOWS_CHANGED || tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            gestionarAutoOcultado()
        }

        if (tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val paquete = evento.packageName?.toString() ?: ""
            val clase = evento.className?.toString() ?: ""

            val esInterfazSistema = paquete.contains("systemui") || paquete.contains("quickstep")
            val esRecientes = clase.contains("Recents") || clase.contains("Overview") || clase.contains("RecentApps")

            if (esInterfazSistema && esRecientes) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onKeyEvent(evento: KeyEvent?): Boolean {
        if (evento == null) return false
        
        val lanzadorAlFrente = ultimoNombrePaquete == packageName || ultimoNombrePaquete == null
        
        if (lanzadorAlFrente && (evento.keyCode == KeyEvent.KEYCODE_APP_SWITCH || evento.keyCode == KeyEvent.KEYCODE_BACK)) {
            return true
        }
        
        return super.onKeyEvent(evento)
    }

    private fun gestionarAutoOcultado() {
        val ventanasActuales = windows
        val miPaquete = this.packageName

        val ventanaAppAlFrente = ventanasActuales.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }

        ventanaAppAlFrente?.root?.let { nodoRaiz ->
            val nombrePaqueteAlFrente = nodoRaiz.packageName?.toString()

            if (nombrePaqueteAlFrente != null && nombrePaqueteAlFrente != miPaquete) {
                if (nombrePaqueteAlFrente != ultimoNombrePaquete && gestorCapas.barraNavExpandida) {
                    gestorCapas.alternarVisibilidadBarraNav()
                }
                ultimoNombrePaquete = nombrePaqueteAlFrente
            } else if (nombrePaqueteAlFrente == miPaquete) {
                ultimoNombrePaquete = miPaquete
            }

            @Suppress("DEPRECATION") nodoRaiz.recycle()
        }
    }

    private fun realizarBusquedaAjustes(consulta: String) {
        val intent = Intent("android.settings.APP_SEARCH_SETTINGS").apply {
            putExtra("query", consulta)
            putExtra("search_query", consulta)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
            
            Thread {
                var nodoRaiz: AccessibilityNodeInfo? = null
                for (i in 0..10) {
                    Thread.sleep(200) 
                    nodoRaiz = rootInActiveWindow
                    if (nodoRaiz != null) break
                }
                
                if (nodoRaiz == null) return@Thread
                
                for (i in 0..5) {
                    val raizActual = rootInActiveWindow ?: nodoRaiz
                    val nodoEdicion = encontrarEditable(raizActual)
                    if (nodoEdicion != null) {
                        if (nodoEdicion.text?.toString() != consulta) {
                            val argumentos = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, consulta)
                            }
                            nodoEdicion.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argumentos)
                        }
                        nodoEdicion.recycle()
                        break
                    }
                    Thread.sleep(200)
                }
                nodoRaiz.recycle()
            }.start()

        } catch (e: Exception) {
            abrirAjustesYBuscar(consulta)
        }
    }

    private fun abrirAjustesYBuscar(consulta: String) {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            Thread {
                var nodoRaiz: AccessibilityNodeInfo? = null
                for (i in 0..10) {
                    Thread.sleep(300)
                    nodoRaiz = rootInActiveWindow
                    if (nodoRaiz != null) break
                }
                
                if (nodoRaiz == null) return@Thread
                
                val botonesBusqueda = mutableListOf<AccessibilityNodeInfo>()
                buscarNodoPorViewId(nodoRaiz, "search", botonesBusqueda)
                buscarNodoPorContentDescription(nodoRaiz, "search", botonesBusqueda)
                
                botonesBusqueda.firstOrNull()?.let {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    it.recycle()
                    
                    for (j in 0..5) {
                        Thread.sleep(300)
                        val nuevaRaiz = rootInActiveWindow
                        if (nuevaRaiz != null) {
                            val edicion = encontrarEditable(nuevaRaiz)
                            if (edicion != null) {
                                val argumentos = android.os.Bundle().apply {
                                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, consulta)
                                }
                                edicion.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argumentos)
                                edicion.recycle()
                                nuevaRaiz.recycle()
                                break
                            }
                            nuevaRaiz.recycle()
                        }
                    }
                }
                nodoRaiz.recycle()
            }.start()
        } catch (ex: Exception) {}
    }

    private fun encontrarEditable(raiz: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val enfocado = raiz.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (enfocado != null && (enfocado.isEditable || enfocado.className?.contains("EditText") == true)) {
            return enfocado
        }

        val listaEditables = mutableListOf<AccessibilityNodeInfo>()
        buscarNodosEditables(raiz, listaEditables)
        return listaEditables.firstOrNull()
    }

    private fun buscarNodosEditables(
        nodo: AccessibilityNodeInfo?,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (nodo == null) return

        if (nodo.isEditable || nodo.className?.contains("EditText") == true || nodo.className?.contains("SearchView") == true) {
            @Suppress("DEPRECATION")
            resultados.add(AccessibilityNodeInfo.obtain(nodo))
        }

        for (i in 0 until (nodo.childCount ?: 0)) {
            val hijo = nodo.getChild(i) ?: continue
            buscarNodosEditables(hijo, resultados)
            hijo.recycle()
        }
    }

    private fun buscarNodoPorViewId(
        nodo: AccessibilityNodeInfo?,
        idVista: String,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (nodo == null) return

        if (nodo.viewIdResourceName?.contains(idVista) == true) {
            @Suppress("DEPRECATION")
            resultados.add(AccessibilityNodeInfo.obtain(nodo))
        }

        for (i in 0 until (nodo.childCount ?: 0)) {
            val hijo = nodo.getChild(i) ?: continue
            buscarNodoPorViewId(hijo, idVista, resultados)
            hijo.recycle()
        }
    }

    private fun buscarNodoPorContentDescription(
        nodo: AccessibilityNodeInfo?,
        descripcion: String,
        resultados: MutableList<AccessibilityNodeInfo>
    ) {
        if (nodo == null) return

        if (nodo.contentDescription?.contains(descripcion, ignoreCase = true) == true) {
            @Suppress("DEPRECATION")
            resultados.add(AccessibilityNodeInfo.obtain(nodo))
        }

        for (i in 0 until (nodo.childCount ?: 0)) {
            val hijo = nodo.getChild(i) ?: continue
            buscarNodoPorContentDescription(hijo, descripcion, resultados)
            hijo.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        registroCicloVida.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        almacenViewModel.clear()
        gestorCapas.onDestroy()
        try { unregisterReceiver(receptorComandos) } catch (e: Exception) {}
        super.onDestroy()
    }
}
