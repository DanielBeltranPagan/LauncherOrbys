package com.example.launcherorbys.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.launcherorbys.data.model.AppInfo

/**
 * Modelos de datos compartidos para el motor de búsqueda del cajón de aplicaciones.
 */

sealed class SearchResult {
    data class App(val appInfo: AppInfo) : SearchResult()
    data class System(val action: SystemAction) : SearchResult()
    data class File(val file: LocalFile) : SearchResult()
    data class Contact(val contact: LocalContact) : SearchResult()
    data class Message(val message: LocalMessage) : SearchResult()
    data class Web(val query: String) : SearchResult()
}

data class LocalFile(val name: String, val uri: Uri, val mimeType: String?)
data class LocalContact(val name: String, val phone: String, val uri: Uri)
data class LocalMessage(val sender: String, val snippet: String, val uri: Uri)

data class SystemAction(
    val label: String,
    val icon: ImageVector,
    val intent: Intent,
    val keywords: List<String> = emptyList()
)
