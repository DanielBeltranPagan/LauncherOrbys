package com.example.launcherorbys.ui.drawer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
class AppDrawerViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio = AppRepository(aplicacion)
    private val contexto get() = getApplication<Application>()

    private val _consultaBusqueda = mutableStateOf("")
    val consultaBusqueda: State<String> = _consultaBusqueda

    private val _resultadosBusqueda = MutableStateFlow<List<SearchResult>>(emptyList())
    val resultadosBusqueda: StateFlow<List<SearchResult>> = _resultadosBusqueda.asStateFlow()

    private val _paqueteSeleccionado = mutableStateOf<String?>(null)
    val paqueteSeleccionado: State<String?> = _paqueteSeleccionado

    private var todasLasApps: List<AppInfo> = emptyList()
    private var tareaBusqueda: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repositorio.apps.collect { apps ->
                todasLasApps = apps
                withContext(Dispatchers.Main) {
                    realizarBusqueda(_consultaBusqueda.value)
                }
            }
        }
    }

    private fun String.normalizar(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }

    fun alCambiarConsultaBusqueda(nuevaConsulta: String) {
        _consultaBusqueda.value = nuevaConsulta
        realizarBusqueda(nuevaConsulta)
    }

    private var tareaSeleccion: Job? = null

    fun seleccionarPaquete(nombrePaquete: String?) {
        tareaSeleccion?.cancel()
        _paqueteSeleccionado.value = nombrePaquete
        
        if (nombrePaquete != null) {
            tareaSeleccion = viewModelScope.launch {
                delay(5000)
                if (_paqueteSeleccionado.value == nombrePaquete) {
                    _paqueteSeleccionado.value = null
                }
            }
        }
    }

    fun refrescarApps() {
        viewModelScope.launch(Dispatchers.IO) {
            repositorio.refrescarApps()
        }
    }

    private fun realizarBusqueda(consulta: String) {
        tareaBusqueda?.cancel()
        tareaBusqueda = viewModelScope.launch {
            if (consulta.isEmpty()) {
                val appsAMostrar = todasLasApps.filter { it.nombrePaquete != contexto.packageName }
                _resultadosBusqueda.value = appsAMostrar.map { SearchResult.App(it) }
                return@launch
            }

            delay(500)

            val consultaNormalizada = consulta.normalizar()
            
            val appsFiltradas = withContext(Dispatchers.IO) {
                todasLasApps.filter { 
                    it.nombrePaquete != contexto.packageName && 
                    it.nombre.normalizar().contains(consultaNormalizada) 
                }.sortedBy { it.nombre }
            }
            val contactos = withContext(Dispatchers.IO) { buscarContactos(consulta) }
            
            val resultadosLocales = mutableListOf<SearchResult>()
            resultadosLocales.addAll(appsFiltradas.map { SearchResult.App(it) })
            resultadosLocales.addAll(contactos.map { SearchResult.Contact(it) })
            resultadosLocales.add(SearchResult.SettingsSearch(consulta))
            resultadosLocales.add(SearchResult.GoogleSearch(consulta))
            
            _resultadosBusqueda.value = resultadosLocales

            val sugerencias = obtenerSugerenciasAutocompletado(consulta)
            if (sugerencias.isNotEmpty()) {
                val todosLosResultados = mutableListOf<SearchResult>()
                todosLosResultados.addAll(resultadosLocales)
                todosLosResultados.addAll(sugerencias.map { SearchResult.Suggestion(it) })
                _resultadosBusqueda.value = todosLosResultados
            }
        }
    }

    private suspend fun obtenerSugerenciasAutocompletado(consulta: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=${Uri.encode(consulta)}"
            val conexion = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conexion.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val respuesta = conexion.inputStream.bufferedReader().readText()
            val jsonArrayString = respuesta.substringAfter(",[").substringBeforeLast("]")
            if (jsonArrayString.isEmpty()) return@withContext emptyList()
            
            jsonArrayString.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() && !it.equals(consulta, ignoreCase = true) }
                .take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun buscarContactos(consulta: String): List<LocalContact> {
        if (androidx.core.content.ContextCompat.checkSelfPermission(contexto, android.Manifest.permission.READ_CONTACTS) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val resultados = mutableListOf<LocalContact>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val proyeccion = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts._ID
        )
        val seleccion = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val argumentosSeleccion = arrayOf("%$consulta%")

        try {
            contexto.contentResolver.query(uri, proyeccion, seleccion, argumentosSeleccion, null)?.use { cursor ->
                val colNombre = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val colId = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                
                var contador = 0
                while (cursor.moveToNext() && contador < 10) {
                    if (colNombre != -1 && colId != -1) {
                        val nombre = cursor.getString(colNombre) ?: "Sin nombre"
                        val id = cursor.getLong(colId)
                        resultados.add(LocalContact(
                            nombre = nombre,
                            telefono = "",
                            uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
                        ))
                    }
                    contador++
                }
            }
        } catch (_: Exception) {}
        return resultados
    }
}
