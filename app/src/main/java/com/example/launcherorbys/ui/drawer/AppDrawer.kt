package com.example.launcherorbys.ui.drawer

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.example.launcherorbys.R
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.receivers.PackageReceiver
import com.example.launcherorbys.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Componente principal del Cajón de Aplicaciones.
 */
@Composable
fun AppDrawer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Forzamos que el componente observe cambios de configuración (idioma, etc)
    val config = LocalConfiguration.current
    
    // Obtenemos el ViewModel usando la factoría por defecto si es posible
    val viewModel: AppDrawerViewModel = viewModel()

    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedPackage by viewModel.selectedPackage
    val appLauncher = remember { AppLauncher(context) }

    // Limpiar selección y búsqueda al cerrar/desaparecer el cajón.
    // También refrescamos las apps al abrir para asegurar etiquetas actualizadas tras cambio de idioma.
    LaunchedEffect(Unit) {
        viewModel.refreshApps()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.selectPackage(null)
            viewModel.onSearchQueryChange("")
        }
    }

    // Auto-deselección tras 5 segundos
    LaunchedEffect(selectedPackage) {
        if (selectedPackage != null) {
            delay(5000)
            viewModel.selectPackage(null)
        }
    }

    // Escuchar cambios en las aplicaciones instaladas
    DisposableEffect(Unit) {
        val receiver = PackageReceiver { viewModel.refreshApps() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(Dimens.DrawerWidthPercent)
            .fillMaxHeight(Dimens.DrawerHeightPercent)
            .clip(RoundedCornerShape(Dimens.RadiusExtraLarge))
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { 
                detectTapGestures { 
                    viewModel.selectPackage(null)
                    onClose()
                }
            }
    ) {
        Column(modifier = Modifier.padding(Dimens.PaddingSmall)) {
            // Barra de búsqueda
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) }
            )
            
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            
            // Rejilla de resultados
            ResultsGrid(
                searchResults = searchResults,
                searchQuery = searchQuery,
                selectedPackage = selectedPackage,
                onSelectPackage = { viewModel.selectPackage(it) },
                onClose = onClose,
                appLauncher = appLauncher,
                onQueryChange = { viewModel.onSearchQueryChange(it) }
            )
        }
    }
}

/**
 * Componente de la barra de búsqueda interna del drawer.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    // Aseguramos que el componente se invalide si cambia el idioma
    val config = LocalConfiguration.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(Dimens.PaddingSmall),
        placeholder = { 
            Text(
                stringResource(R.string.drawer_search_placeholder), 
                color = Color.White.copy(alpha = 0.5f), 
                style = MaterialTheme.typography.labelMedium
            ) 
        },
        leadingIcon = { 
            Icon(Icons.Default.Search, stringResource(R.string.drawer_section_searches), tint = Color.White.copy(alpha = 0.7f)) 
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.RadiusLarge),
        textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color.White
        )
    )
}

/**
 * Rejilla que muestra los resultados filtrados por el motor de búsqueda.
 */
@Composable
private fun ResultsGrid(
    searchResults: List<SearchResult>,
    searchQuery: String,
    selectedPackage: String?,
    onSelectPackage: (String?) -> Unit,
    onClose: () -> Unit,
    appLauncher: AppLauncher,
    onQueryChange: (String) -> Unit
) {
    val context = LocalContext.current
    val suggestions = searchResults.filterIsInstance<SearchResult.Suggestion>()
    val appsList = searchResults.filterIsInstance<SearchResult.App>()
    val contactsList = searchResults.filterIsInstance<SearchResult.Contact>()
    val googleSearch = searchResults.filterIsInstance<SearchResult.GoogleSearch>().firstOrNull()
    val settingsSearch = searchResults.filterIsInstance<SearchResult.SettingsSearch>().firstOrNull()

    // Calculamos qué fila está seleccionada para moverla entera (Simetría)
    val selectedIndex = appsList.indexOfFirst { it.appInfo.packageName == selectedPackage }
    val selectedRow = if (selectedIndex != -1) selectedIndex / 4 else -1

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingTiny),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Sección de Aplicaciones (Lo más importante arriba)
        if (appsList.isNotEmpty()) {
            if (searchQuery.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_apps)) }
            }
            itemsIndexed(appsList) { index, result ->
                val row = index / 4
                AppItem(
                    app = result.appInfo,
                    isSelected = selectedPackage == result.appInfo.packageName,
                    isRowSelected = row == selectedRow,
                    onSelect = { onSelectPackage(result.appInfo.packageName) },
                    onDismiss = { onSelectPackage(null) },
                    onAppLaunched = onClose,
                    appLauncher = appLauncher
                )
            }
        }

        // 2. Sección de Contactos
        if (contactsList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_contacts)) }
            items(contactsList) { result ->
                ContactItem(contact = result.contact, onClicked = onClose)
            }
        }

        // 3. Sección de Búsquedas Especiales y Autocompletado
        if (searchQuery.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_searches)) }
            
            // 3.1. Buscar en Ajustes (Local)
            if (settingsSearch != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { 
                    SettingsSearchItem(query = settingsSearch.query, onClicked = onClose) 
                }
            }

            // 3.2. Buscar en Google (Web)
            if (googleSearch != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { 
                    GoogleSearchItem(query = googleSearch.query, onClicked = onClose) 
                }
            }

            // 3.3. Sugerencias de Autocompletado de Google (Debajo del todo)
            if (suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_suggestions)) }
                items(suggestions, span = { GridItemSpan(maxLineSpan) }) { sug ->
                    SuggestionItem(
                        text = sug.text, 
                        onSearch = { query ->
                            try {
                                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra("query", query)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                onClose()
                            } catch (_: Exception) {}
                        },
                        onAutocomplete = { onQueryChange(it) }
                    )
                }
            }
            
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
