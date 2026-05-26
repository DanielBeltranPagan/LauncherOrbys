package com.example.launcherorbys.ui.drawer

import android.app.Application
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel que gestiona la lógica del cajón de aplicaciones y el motor de búsqueda.
 */
class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = AppRepository(context)

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _selectedPackage = mutableStateOf<String?>(null)
    val selectedPackage: State<String?> = _selectedPackage

    private var allApps: List<AppInfo> = emptyList()

    init {
        viewModelScope.launch {
            repository.apps.collect { apps ->
                allApps = apps
                performSearch(_searchQuery.value)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }

    fun selectPackage(packageName: String?) {
        _selectedPackage.value = packageName
    }

    fun refreshApps() {
        repository.refreshApps()
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            if (query.isEmpty()) {
                _searchResults.value = allApps.map { SearchResult.App(it) }
                return@launch
            }

            withContext(Dispatchers.IO) {
                val lowercaseQuery = query.lowercase().trim()
                val results = mutableListOf<SearchResult>()

                // 1. Filtrar Apps (Prioridad 1)
                val filteredApps = allApps.filter { it.label.lowercase().contains(lowercaseQuery) }
                results.addAll(filteredApps.map { SearchResult.App(it) })

                // 2. Buscar en Sistema (Ajustes)
                val systemActions = getSystemActions().filter { action ->
                    action.label.lowercase().contains(lowercaseQuery) ||
                            action.keywords.any { it.contains(lowercaseQuery) }
                }
                results.addAll(systemActions.map { SearchResult.System(it) })

                // 3. Motores de búsqueda específicos si la query es suficientemente larga
                if (lowercaseQuery.length >= 2) {
                    results.addAll(searchLocalFiles(lowercaseQuery).map { SearchResult.File(it) })
                    results.addAll(searchContacts(lowercaseQuery).map { SearchResult.Contact(it) })
                    results.addAll(searchMessages(lowercaseQuery).map { SearchResult.Message(it) })
                }

                // 4. Fallback: Búsqueda Web
                results.add(SearchResult.Web(lowercaseQuery))

                _searchResults.value = results
            }
        }
    }

    private fun getSystemActions() = listOf(
        SystemAction("Ajustes", Icons.Default.Settings, Intent(Settings.ACTION_SETTINGS), listOf("configuración", "opciones")),
        SystemAction("Wi-Fi", Icons.Default.Wifi, Intent(Settings.ACTION_WIFI_SETTINGS), listOf("internet", "red", "conexión")),
        SystemAction("Bluetooth", Icons.Default.Bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), listOf("inalámbrico", "dispositivos")),
        SystemAction("Pantalla", Icons.Default.Tune, Intent(Settings.ACTION_DISPLAY_SETTINGS), listOf("brillo", "fondo", "tema", "fuente", "luz")),
        SystemAction("Sonido", Icons.AutoMirrored.Filled.VolumeUp, Intent(Settings.ACTION_SOUND_SETTINGS), listOf("volumen", "tono", "vibración", "audio")),
        SystemAction("Batería", Icons.Default.BatteryFull, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), listOf("energía", "carga", "ahorro")),
        SystemAction("Archivos", Icons.Default.Folder, Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }, listOf("documentos", "descargas", "explorador"))
    )

    private suspend fun searchLocalFiles(query: String): List<LocalFile> {
        val results = mutableListOf<LocalFile>()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )

        for (uri in uris) {
            try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                    var count = 0
                    while (cursor.moveToNext() && count < 5) {
                        val id = cursor.getLong(idCol)
                        results.add(LocalFile(
                            name = cursor.getString(nameCol),
                            uri = ContentUris.withAppendedId(uri, id),
                            mimeType = cursor.getString(mimeCol)
                        ))
                        count++
                    }
                }
            } catch (_: Exception) {}
            if (results.size > 15) break
        }
        return results
    }

    private suspend fun searchContacts(query: String): List<LocalContact> {
        val results = mutableListOf<LocalContact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val id = cursor.getLong(idCol)
                    results.add(LocalContact(
                        name = cursor.getString(nameCol),
                        phone = cursor.getString(numCol),
                        uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
                    ))
                    count++
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private suspend fun searchMessages(query: String): List<LocalMessage> {
        val results = mutableListOf<LocalMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "_id")
        val selection = "body LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")?.use { cursor ->
                val addrCol = cursor.getColumnIndex("address")
                val bodyCol = cursor.getColumnIndex("body")
                val idCol = cursor.getColumnIndex("_id")

                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val id = cursor.getLong(idCol)
                    results.add(LocalMessage(
                        sender = cursor.getString(addrCol) ?: "Desconocido",
                        snippet = cursor.getString(bodyCol) ?: "",
                        uri = Uri.parse("content://sms/$id")
                    ))
                    count++
                }
            }
        } catch (_: Exception) {}
        return results
    }
}
