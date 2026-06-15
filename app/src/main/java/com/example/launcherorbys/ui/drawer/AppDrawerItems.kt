package com.example.launcherorbys.ui.drawer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.launcherorbys.R
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.ui.theme.Dimens
import com.example.launcherorbys.utils.Constants

/**
 * Encabezado de sección para organizar los elementos en el LazyVerticalGrid.
 * Muestra un divisor y un texto descriptivo de la categoría o sección.
 *
 * @param texto El nombre de la sección a mostrar.
 */
@Composable
fun CabeceraSeccion(texto: String) {
    Column(modifier = Modifier.padding(vertical = Dimens.PaddingMedium, horizontal = Dimens.PaddingMedium)) {
        // Línea divisoria sutil sobre el texto
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        // Texto de la cabecera con estilo secundario
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Representa un item individual de aplicación dentro del cajón de apps.
 * Incluye soporte para lanzarla con un toque o entrar en modo gestión con una pulsación larga.
 *
 * @param app Datos de la aplicación (nombre, icono, paquete, etc).
 * @param estaSeleccionada Indica si esta app específica está en modo edición/gestión.
 * @param estaFilaSeleccionada Indica si alguna app de la misma fila está seleccionada (para ajustar el espaciado).
 * @param alSeleccionar Callback disparado al realizar una pulsación larga.
 * @param alDescartar Callback disparado para salir del modo edición.
 * @param alLanzarApp Callback que se ejecuta tras lanzar la app para cerrar el cajón.
 * @param lanzadorApp Instancia del gestor que ejecuta el lanzamiento de paquetes.
 */
@Composable
fun ItemAplicacion(
    app: AppInfo,
    estaSeleccionada: Boolean,
    estaFilaSeleccionada: Boolean,
    alSeleccionar: () -> Unit,
    alDescartar: () -> Unit,
    alLanzarApp: () -> Unit,
    lanzadorApp: AppLauncher
) {
    // Animación de desplazamiento vertical para dejar espacio a los botones de acción superiores
    val rellenoSuperiorExtra by animateDpAsState(
        targetValue = if (estaFilaSeleccionada) 42.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rellenoExtra"
    )

    // Configuración del efecto de vibración cuando la app está seleccionada
    val transicionInfinita = rememberInfiniteTransition(label = "vibracion")
    val rotacion by transicionInfinita.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotacion"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = rellenoSuperiorExtra),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .graphicsLayer {
                        // Aplicamos el efecto de rotación y escala si el item está activo
                        if (estaSeleccionada) {
                            rotationZ = rotacion
                            scaleX = 1.1f
                            scaleY = 1.1f
                        }
                    }
                    .clip(RoundedCornerShape(Dimens.RadiusMedium))
                    .pointerInput(Unit) {
                        // Gestión personalizada de gestos para diferenciar toque simple de largo
                        detectTapGestures(
                            onTap = {
                                if (estaSeleccionada) {
                                    alDescartar() // Tocar de nuevo la app seleccionada la deselecciona
                                } else {
                                    lanzadorApp.lanzarApp(app.nombrePaquete)
                                    alLanzarApp() // Cerramos el cajón al abrir una app
                                }
                            },
                            onLongPress = {
                                alSeleccionar() // Activamos el modo edición
                            }
                        )
                    }
                    .padding(Dimens.PaddingSmall),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Renderizado optimizado del icono de la aplicación usando Coil y esquema personalizado
                AsyncImage(
                    model = "appicon://${app.nombrePaquete}",
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.AppIconSize)
                )
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                // Nombre de la aplicación con recorte si es demasiado largo
                Text(
                    text = app.nombre,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (estaSeleccionada) Color.White.copy(alpha = 0.6f) else Color.White
                )
            }

            // Si la aplicación está seleccionada, superponemos los botones de acción
            if (estaSeleccionada) {
                BotonesAccionAplicacion(app = app, alDescartar = alDescartar, alLanzarApp = alLanzarApp)
            }
        }
    }
}

/**
 * Muestra el grupo de botones (Info y Borrar) sobre el icono de la aplicación seleccionada.
 *
 * @param app Datos de la aplicación para obtener el paquete.
 * @param alDescartar Callback para limpiar el estado de selección.
 * @param alLanzarApp Callback para ejecutar después de la acción.
 */
@Composable
private fun BoxScope.BotonesAccionAplicacion(
    app: AppInfo,
    alDescartar: () -> Unit,
    alLanzarApp: () -> Unit
) {
    val contexto = LocalContext.current
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-38).dp) // Posicionamos por encima del icono
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botón para abrir la información del sistema de la aplicación
        BotonAccion(icono = Icons.Default.Info, color = Color.Black.copy(alpha = 0.8f)) {
            alDescartar()
            abrirAjustesApp(contexto, app.nombrePaquete)
            alLanzarApp()
        }

        // El botón de desinstalar solo se muestra si la app no es del sistema o es desinstalable
        if (app.esDesinstalable) {
            BotonAccion(icono = Icons.Default.Delete, color = Color(0xFFE53935).copy(alpha = 0.9f)) {
                alDescartar()
                desinstalarApp(contexto, app.nombrePaquete)
                alLanzarApp()
            }
        }
    }
}

/**
 * Componente base para botones circulares de acción con icono central.
 *
 * @param icono El icono vectorial a mostrar.
 * @param color El color de fondo del círculo.
 * @param alHacerClic La acción que se ejecutará al pulsar.
 */
@Composable
private fun BotonAccion(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    alHacerClic: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Dimens.ActionButtonSize)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
            .clickable { alHacerClic() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, null, tint = Color.White, modifier = Modifier.size(Dimens.IconSizeSmall))
    }
}

/**
 * Representa un contacto del dispositivo encontrado en la búsqueda.
 * Al pulsar, abre la ficha del contacto en la aplicación de contactos por defecto.
 *
 * @param contacto Datos del contacto (nombre, URI).
 * @param alHacerClic Callback para notificar la interacción.
 */
@Composable
fun ItemContacto(contacto: LocalContact, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ContenedorItem(
        etiqueta = contacto.nombre,
        icono = Icons.Default.Person,
        alHacerClic = {
            try {
                // Intent para visualizar los detalles del contacto
                val intent = Intent(Intent.ACTION_VIEW, contacto.uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                contexto.startActivity(intent)
                alHacerClic()
            } catch (_: Exception) {
                // Silenciamos excepciones si no hay app de contactos disponible
            }
        }
    )
}

/**
 * Componente genérico para acciones que interactúan con el sistema mediante Intents.
 *
 * @param accion Modelo que contiene el nombre, icono e intent de la acción.
 * @param alHacerClic Callback tras ejecutar la acción.
 */
@Composable
fun ItemAccionSistema(accion: SystemAction, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ContenedorItem(
        etiqueta = accion.nombre,
        icono = accion.icono,
        alHacerClic = {
            try {
                // Aseguramos que el intent se lance en una nueva tarea
                accion.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                contexto.startActivity(accion.intent)
                alHacerClic()
            } catch (_: Exception) {
                // Error al lanzar la actividad del sistema
            }
        }
    )
}

/**
 * Muestra una opción de búsqueda web rápida basada en el texto introducido por el usuario.
 *
 * @param consulta El texto a buscar en internet.
 * @param alHacerClic Callback tras iniciar la búsqueda.
 */
@Composable
fun ItemBusquedaWeb(consulta: String, alHacerClic: () -> Unit) {
    // Texto formateado como "Buscar 'consulta' en la web"
    val etiqueta = stringResource(R.string.search_prefix, consulta)
    ItemAccionSistema(
        accion = SystemAction(
            nombre = etiqueta,
            icono = Icons.Default.Language,
            intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", consulta) }
        ),
        alHacerClic = alHacerClic
    )
}

/**
 * Muestra una sugerencia de búsqueda de Google (autocompletado).
 * Permite buscar directamente o copiar el texto al campo de búsqueda.
 *
 * @param texto El texto de la sugerencia.
 * @param alBuscar Acción para realizar la búsqueda directamente.
 * @param alAutocompletar Acción para volcar el texto en la barra de búsqueda.
 */
@Composable
fun ItemSugerencia(
    texto: String,
    alBuscar: (String) -> Unit,
    alAutocompletar: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { alBuscar(texto) }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de lupa para indicar que es una sugerencia de búsqueda
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        // El texto de la sugerencia ocupa el espacio disponible
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Botón a la derecha para autocompletar la barra sin buscar aún
        IconButton(
            onClick = { alAutocompletar(texto) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ArrowOutward,
                contentDescription = stringResource(R.string.content_desc_autocomplete),
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Especialización de búsqueda que lanza explícitamente una búsqueda global en Google.
 *
 * @param consulta Texto a buscar.
 * @param alHacerClic Callback tras el lanzamiento.
 */
@Composable
fun ItemBusquedaGoogle(consulta: String, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ItemBusquedaLargo(
        etiqueta = stringResource(R.string.search_google, consulta),
        icono = Icons.Default.Public,
        alHacerClic = {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", consulta)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
                alHacerClic()
            } catch (_: Exception) {}
        }
    )
}

/**
 * Permite buscar el término actual dentro del menú de Ajustes del dispositivo.
 * Envía un broadcast si es soportado e intenta abrir la actividad de Ajustes.
 *
 * @param consulta Término de búsqueda.
 * @param alHacerClic Callback tras la acción.
 */
@Composable
fun ItemBusquedaAjustes(consulta: String, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ItemBusquedaLargo(
        etiqueta = stringResource(R.string.search_settings, consulta),
        icono = Icons.Default.Settings,
        alHacerClic = {
            alHacerClic()

            // Intento de integración con búsqueda interna de ajustes si existe el receptor
            val intent = Intent(Constants.ACTION_SETTINGS_SEARCH).apply {
                putExtra("query", consulta)
                setPackage(contexto.packageName)
            }
            contexto.sendBroadcast(intent)

            try {
                // Abrir la pantalla principal de Ajustes
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(settingsIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )
}

/**
 * Lanza una búsqueda de aplicaciones en la Google Play Store.
 * Intenta abrir la app de Play Store y cae a la versión web si falla.
 *
 * @param consulta Término a buscar en la tienda.
 * @param alHacerClic Callback tras la acción.
 */
@Composable
fun ItemBusquedaPlayStore(consulta: String, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ItemBusquedaLargo(
        etiqueta = stringResource(R.string.search_play_store, consulta),
        icono = Icons.Default.Shop,
        alHacerClic = {
            try {
                // Intent nativo de market://
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://search?q=$consulta")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
                alHacerClic()
            } catch (_: Exception) {
                try {
                    // Fallback a URL de navegador si la tienda no está instalada
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/search?q=$consulta")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    contexto.startActivity(intent)
                } catch (_: Exception) {}
            }
            alHacerClic()
        }
    )
}

/**
 * Lanza una búsqueda de vídeos en YouTube.
 * Intenta usar la aplicación oficial y cae a la web si no está disponible.
 *
 * @param consulta Término de búsqueda.
 * @param alHacerClic Callback tras la acción.
 */
@Composable
fun ItemBusquedaYouTube(consulta: String, alHacerClic: () -> Unit) {
    val contexto = LocalContext.current
    ItemBusquedaLargo(
        etiqueta = stringResource(R.string.search_youtube, consulta),
        icono = Icons.Default.PlayCircle,
        alHacerClic = {
            try {
                // Intent específico para la aplicación de YouTube
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", consulta)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
            } catch (_: Exception) {
                try {
                    // Fallback a la web oficial de YouTube
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://www.youtube.com/results?search_query=$consulta")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    contexto.startActivity(intent)
                } catch (_: Exception) {}
            }
            alHacerClic()
        }
    )
}

/**
 * Contenedor de ancho completo para opciones de búsqueda.
 * Muestra un icono circular a la izquierda y el texto descriptivo al lado.
 *
 * @param etiqueta Texto a mostrar.
 * @param icono Icono descriptivo de la fuente de búsqueda.
 * @param alHacerClic Callback al pulsar el elemento.
 */
@Composable
private fun ItemBusquedaLargo(
    etiqueta: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    alHacerClic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { alHacerClic() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contenedor circular sutil para el icono
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Texto de la acción de búsqueda
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Diseño base vertical (Icono arriba, Texto abajo) para elementos de la rejilla.
 * Se utiliza para aplicaciones, contactos y acciones rápidas.
 *
 * @param etiqueta El nombre del elemento.
 * @param icono Icono vectorial representativo.
 * @param alHacerClic Acción al pulsar.
 */
@Composable
private fun ContenedorItem(
    etiqueta: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    alHacerClic: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { alHacerClic() }
            .padding(Dimens.PaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fondo circular para el icono por defecto
        Box(
            modifier = Modifier
                .size(Dimens.AppIconSize)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, tint = Color.White, modifier = Modifier.size(Dimens.IconSizeMedium))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        // Etiqueta de texto centrada
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White
        )
    }
}

/**
 * Abre la pantalla de detalles de la aplicación en los ajustes del sistema de Android.
 * Permite al usuario forzar detención, borrar caché, ver permisos, etc.
 *
 * @param contexto Contexto necesario para lanzar el Intent.
 * @param nombrePaquete El ID único (package name) de la aplicación.
 */
private fun abrirAjustesApp(contexto: Context, nombrePaquete: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$nombrePaquete")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        contexto.startActivity(intent)
    } catch (_: Exception) {
        // Manejo silencioso en caso de error al abrir ajustes
    }
}

/**
 * Lanza el diálogo del sistema para desinstalar una aplicación específica.
 *
 * @param contexto Contexto necesario para lanzar el Intent.
 * @param nombrePaquete El ID único de la aplicación a eliminar.
 */
private fun desinstalarApp(contexto: Context, nombrePaquete: String) {
    try {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$nombrePaquete")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        contexto.startActivity(intent)
    } catch (_: Exception) {
        // Manejo silencioso si el Intent no puede ser procesado
    }
}
