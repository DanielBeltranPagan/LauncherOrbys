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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.launcherorbys.receivers.PackageReceiver
import com.example.launcherorbys.ui.theme.Dimens

/**
 * Componente principal del Cajón de Aplicaciones.
 * Ahora utiliza [AppDrawerViewModel] para gestionar la lógica de búsqueda y estado.
 */
@Composable
fun AppDrawer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AppDrawerViewModel = viewModel(
        factory = remember {
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
        }
    )

    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedPackage by viewModel.selectedPackage

    // Escuchar cambios en las aplicaciones instaladas
    DisposableEffect(Unit) {
        val receiver = PackageReceiver { viewModel.refreshApps() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
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
                detectTapGestures { viewModel.selectPackage(null) } 
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
                onClose = onClose
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
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(Dimens.PaddingSmall),
        placeholder = { 
            Text(
                "Buscar apps, archivos o ajustes...", 
                color = Color.White.copy(alpha = 0.5f), 
                style = MaterialTheme.typography.bodySmall
            ) 
        },
        leadingIcon = { 
            Icon(Icons.Default.Search, "Buscar", tint = Color.White.copy(alpha = 0.7f)) 
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.RadiusLarge),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
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
    onClose: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingTiny),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val appsList = searchResults.filterIsInstance<SearchResult.App>()
        val contactsList = searchResults.filterIsInstance<SearchResult.Contact>()
        val filesList = searchResults.filterIsInstance<SearchResult.File>()
        val messagesList = searchResults.filterIsInstance<SearchResult.Message>()
        val systemList = searchResults.filterIsInstance<SearchResult.System>()
        val webList = searchResults.filterIsInstance<SearchResult.Web>()

        // 1. Sección de Aplicaciones
        if (appsList.isNotEmpty()) {
            if (searchQuery.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("APLICACIONES") }
            }
            items(appsList) { result ->
                AppItem(
                    app = result.appInfo,
                    isSelected = selectedPackage == result.appInfo.packageName,
                    onSelect = { onSelectPackage(result.appInfo.packageName) },
                    onDismiss = { onSelectPackage(null) },
                    onAppLaunched = onClose
                )
            }
        }

        // 2. Sección de Contactos
        if (contactsList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("CONTACTOS") }
            items(contactsList) { result ->
                ContactItem(contact = result.contact, onClicked = onClose)
            }
        }

        // 3. Sección de Archivos
        if (filesList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("ARCHIVOS") }
            items(filesList) { result ->
                FileItem(file = result.file, onClicked = onClose)
            }
        }

        // 4. Sección de Mensajes
        if (messagesList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("MENSAJES") }
            items(messagesList) { result ->
                MessageItem(message = result.message, onClicked = onClose)
            }
        }

        // 5. Sección de Sistema y Web
        if (systemList.isNotEmpty() || webList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("SISTEMA Y WEB") }
            
            items(systemList) { result ->
                SystemActionItem(action = result.action, onClicked = onClose)
            }
            
            items(webList) { result ->
                WebSearchItem(query = result.query, onClicked = onClose)
            }
        }
    }
}
