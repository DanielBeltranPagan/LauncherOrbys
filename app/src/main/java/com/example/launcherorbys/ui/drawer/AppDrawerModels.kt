package com.example.launcherorbys.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.launcherorbys.data.model.AppInfo

/**
 * Modelos de datos compartidos para el motor de búsqueda del cajón de aplicaciones.
 */
sealed class SearchResult {
    data class App(val infoApp: AppInfo) : SearchResult()
    data class System(val accionSistema: SystemAction) : SearchResult()
    data class Contact(val contacto: LocalContact) : SearchResult()
    data class Suggestion(val texto: String) : SearchResult()
    data class GoogleSearch(val consulta: String) : SearchResult()
    data class SettingsSearch(val consulta: String) : SearchResult()
    data class PlayStoreSearch(val consulta: String) : SearchResult()
    data class YouTubeSearch(val consulta: String) : SearchResult()
}

data class LocalContact(val nombre: String, val telefono: String, val uri: Uri)

data class SystemAction(
    val nombre: String,
    val icono: ImageVector,
    val intent: Intent,
    val palabrasClave: List<String> = emptyList()
)
