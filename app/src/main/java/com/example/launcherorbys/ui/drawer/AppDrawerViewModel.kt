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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.repository.AppRepository
import java.text.Normalizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel que gestiona la lógica del cajón de aplicaciones y el motor de búsqueda.
 */
class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val context get() = getApplication<Application>()

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _selectedPackage = mutableStateOf<String?>(null)
    val selectedPackage: State<String?> = _selectedPackage

    private var allApps: List<AppInfo> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.apps.collect { apps ->
                allApps = apps
                withContext(Dispatchers.Main) {
                    performSearch(_searchQuery.value)
                }
            }
        }
    }

    private fun String.normalize(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }

    private var selectionJob: Job? = null

    fun selectPackage(packageName: String?) {
        selectionJob?.cancel()
        _selectedPackage.value = packageName
        
        if (packageName != null) {
            selectionJob = viewModelScope.launch {
                delay(5000)
                if (_selectedPackage.value == packageName) {
                    _selectedPackage.value = null
                }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshApps()
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isEmpty()) {
                val appsToShow = allApps.filter { it.packageName != context.packageName }
                _searchResults.value = appsToShow.map { SearchResult.App(it) }
                return@launch
            }

            // Pequeño debounce para no saturar con cada letra
            delay(150)

            val normalizedQuery = query.normalize()
            
            // 1. Apps y Contactos (Local - Rápido)
            val filteredApps = withContext(Dispatchers.IO) {
                allApps.filter { 
                    it.packageName != context.packageName && 
                    it.label.normalize().contains(normalizedQuery) 
                }.sortedBy { it.label }
            }
            val contacts = withContext(Dispatchers.IO) { searchContacts(query) }
            
            val localResults = mutableListOf<SearchResult>()
            localResults.addAll(filteredApps.map { SearchResult.App(it) })
            localResults.addAll(contacts.map { SearchResult.Contact(it) })
            localResults.add(SearchResult.SettingsSearch(query))
            localResults.add(SearchResult.GoogleSearch(query))
            
            // Mostramos resultados locales primero
            _searchResults.value = localResults

            // 2. Sugerencias de Autocompletado (Red - Lento)
            val suggestions = getAutocompleteSuggestions(query)
            if (suggestions.isNotEmpty()) {
                val allResults = mutableListOf<SearchResult>()
                allResults.addAll(localResults)
                allResults.addAll(suggestions.map { SearchResult.Suggestion(it) })
                _searchResults.value = allResults
            }
        }
    }

    private suspend fun getAutocompleteSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // Usamos el cliente "chrome" o "firefox" para obtener JSON simple
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=${Uri.encode(query)}"
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val response = connection.inputStream.bufferedReader().readText()
            // El formato de Firefox es: ["query", ["sug1", "sug2", ...]]
            // Usamos una limpieza manual simple para evitar dependencias de JSON pesadas
            val jsonArrayString = response.substringAfter(",[").substringBeforeLast("]")
            if (jsonArrayString.isEmpty()) return@withContext emptyList()
            
            jsonArrayString.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() && !it.equals(query, ignoreCase = true) }
                .take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchContacts(query: String): List<LocalContact> {
        // Comprobar permiso de forma silenciosa
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val results = mutableListOf<LocalContact>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts._ID
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                
                var count = 0
                while (cursor.moveToNext() && count < 10) {
                    if (nameCol != -1 && idCol != -1) {
                        val name = cursor.getString(nameCol) ?: "Sin nombre"
                        val id = cursor.getLong(idCol)
                        results.add(LocalContact(
                            name = name,
                            phone = "",
                            uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
                        ))
                    }
                    count++
                }
            }
        } catch (_: Exception) {}
        return results
    }
}
