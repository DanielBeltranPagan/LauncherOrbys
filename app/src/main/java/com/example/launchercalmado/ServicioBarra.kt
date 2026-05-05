package com.example.launchercalmado

import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.launchercalmado.ui.components.NavBar
import com.example.launchercalmado.ui.theme.LauncherCalmadoTheme

class ServicioBarra : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var vistaNav: ComposeView
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

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

    override fun onDestroy() {
        super.onDestroy()
        if (::vistaNav.isInitialized) windowManager.removeView(vistaNav)
    }
}