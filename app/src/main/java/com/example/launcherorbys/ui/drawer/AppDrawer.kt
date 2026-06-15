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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val config = LocalConfiguration.current
    
    val modelo: AppDrawerViewModel = viewModel()

    val consultaBusqueda by modelo.consultaBusqueda
    val resultadosBusqueda by modelo.resultadosBusqueda.collectAsState()
    val paqueteSeleccionado by modelo.paqueteSeleccionado
    val lanzadorApp = remember { AppLauncher(contexto) }

    LaunchedEffect(Unit) {
        modelo.refrescarApps()
    }

    DisposableEffect(Unit) {
        onDispose {
            modelo.seleccionarPaquete(null)
            modelo.alCambiarConsultaBusqueda("")
        }
    }

    LaunchedEffect(paqueteSeleccionado) {
        if (paqueteSeleccionado != null) {
            delay(5000)
            modelo.seleccionarPaquete(null)
        }
    }

    DisposableEffect(Unit) {
        val receptor = PackageReceiver { modelo.refrescarApps() }
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            contexto.registerReceiver(receptor, filtro, android.content.Context.RECEIVER_EXPORTED)
        } else {
            contexto.registerReceiver(receptor, filtro)
        }
        onDispose {
            try {
                contexto.unregisterReceiver(receptor)
            } catch (_: Exception) {}
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
                    modelo.seleccionarPaquete(null)
                    alCerrar()
                }
            }
    ) {
        Column(modifier = Modifier.padding(Dimens.PaddingSmall)) {
            BarraBusqueda(
                consulta = consultaBusqueda,
                alCambiarConsulta = { modelo.alCambiarConsultaBusqueda(it) }
            )
            
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            
            CuadriculaResultados(
                resultadosBusqueda = resultadosBusqueda,
                consultaBusqueda = consultaBusqueda,
                paqueteSeleccionado = paqueteSeleccionado,
                alSeleccionarPaquete = { modelo.seleccionarPaquete(it) },
                alCerrar = alCerrar,
                lanzadorApp = lanzadorApp,
                alCambiarConsulta = { modelo.alCambiarConsultaBusqueda(it) }
            )
        }
    }
}

/**
 * Componente de la barra de búsqueda interna del drawer.
 */
@Composable
private fun BarraBusqueda(
    consulta: String,
    alCambiarConsulta: (String) -> Unit
) {
    TextField(
        value = consulta,
        onValueChange = alCambiarConsulta,
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
private fun CuadriculaResultados(
    resultadosBusqueda: List<SearchResult>,
    consultaBusqueda: String,
    paqueteSeleccionado: String?,
    alSeleccionarPaquete: (String?) -> Unit,
    alCerrar: () -> Unit,
    lanzadorApp: AppLauncher,
    alCambiarConsulta: (String) -> Unit
) {
    val contexto = LocalContext.current
    val sugerencias = resultadosBusqueda.filterIsInstance<SearchResult.Suggestion>()
    val listaApps = resultadosBusqueda.filterIsInstance<SearchResult.App>()
    val listaContactos = resultadosBusqueda.filterIsInstance<SearchResult.Contact>()
    val busquedaGoogle = resultadosBusqueda.filterIsInstance<SearchResult.GoogleSearch>().firstOrNull()
    val busquedaAjustes = resultadosBusqueda.filterIsInstance<SearchResult.SettingsSearch>().firstOrNull()

    val indiceSeleccionado = listaApps.indexOfFirst { it.infoApp.nombrePaquete == paqueteSeleccionado }
    val filaSeleccionada = if (indiceSeleccionado != -1) indiceSeleccionado / 4 else -1

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingTiny),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (listaApps.isNotEmpty()) {
            if (consultaBusqueda.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { CabeceraSeccion(stringResource(R.string.drawer_section_apps)) }
            }
            itemsIndexed(
                items = listaApps,
                key = { _, resultado -> resultado.infoApp.nombrePaquete }
            ) { indice, resultado ->
                val fila = indice / 4
                ItemAplicacion(
                    app = resultado.infoApp,
                    estaSeleccionada = paqueteSeleccionado == resultado.infoApp.nombrePaquete,
                    estaFilaSeleccionada = fila == filaSeleccionada,
                    alSeleccionar = { alSeleccionarPaquete(resultado.infoApp.nombrePaquete) },
                    alDescartar = { alSeleccionarPaquete(null) },
                    alLanzarApp = alCerrar,
                    lanzadorApp = lanzadorApp
                )
            }
        }

        if (listaContactos.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { CabeceraSeccion(stringResource(R.string.drawer_section_contacts)) }
            items(
                items = listaContactos,
                key = { it.contacto.uri.toString() }
            ) { resultado ->
                ItemContacto(contacto = resultado.contacto, alHacerClic = alCerrar)
            }
        }

        if (consultaBusqueda.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { CabeceraSeccion(stringResource(R.string.drawer_section_searches)) }
            
            if (busquedaAjustes != null) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "search_settings"
                ) { 
                    ItemBusquedaAjustes(consulta = busquedaAjustes.consulta, alHacerClic = alCerrar) 
                }
            }

            if (busquedaGoogle != null) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "search_google"
                ) { 
                    ItemBusquedaGoogle(consulta = busquedaGoogle.consulta, alHacerClic = alCerrar) 
                }
            }

            if (sugerencias.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { CabeceraSeccion(stringResource(R.string.drawer_section_suggestions)) }
                items(
                    items = sugerencias,
                    key = { "sug_${it.texto}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { sug ->
                    ItemSugerencia(
                        texto = sug.texto, 
                        alBuscar = { consulta ->
                            try {
                                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra("query", consulta)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                contexto.startActivity(intent)
                                alCerrar()
                            } catch (_: Exception) {}
                        },
                        alAutocompletar = { alCambiarConsulta(it) }
                    )
                }
            }
            
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
