package com.example.launcherorbys.ui.drawer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.example.launcherorbys.R
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.managers.AppLauncher
import com.example.launcherorbys.ui.theme.Dimens
import com.example.launcherorbys.utils.Constants

/**
 * Encabezado de sección para el LazyVerticalGrid.
 */
@Composable
fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(vertical = Dimens.PaddingMedium, horizontal = Dimens.PaddingMedium)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
    }
}

/**
 * Representa un item de aplicación con soporte para pulsación larga (gestión).
 */
@Composable
fun AppItem(
    app: AppInfo,
    isSelected: Boolean,
    isRowSelected: Boolean,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit,
    appLauncher: AppLauncher
) {
    // Animación para el hueco superior (empuja la fila entera hacia abajo para que quepan los botones)
    val extraTopPadding by animateDpAsState(
        targetValue = if (isRowSelected) 42.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "extraPadding"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = extraTopPadding),
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
                        if (isSelected) {
                            rotationZ = rotation
                            scaleX = 1.1f
                            scaleY = 1.1f
                        }
                    }
                    .clip(RoundedCornerShape(Dimens.RadiusMedium))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isSelected) {
                                    onDismiss()
                                } else {
                                    appLauncher.lanzarApp(app.packageName)
                                    onAppLaunched()
                                }
                            },
                            onLongPress = {
                                onSelect()
                            }
                        )
                    }
                    .padding(Dimens.PaddingSmall),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                app.icon?.let {
                    Image(
                        bitmap = it.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.AppIconSize)
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White,
                    fontSize = Dimens.TextSmall
                )
            }

            if (isSelected) {
                AppActionButtons(app = app, onDismiss = onDismiss, onAppLaunched = onAppLaunched)
            }
        }
    }
}

@Composable
private fun BoxScope.AppActionButtons(
    app: AppInfo,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-38).dp) // Ajustado para que flote en el hueco creado
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botón INFO
        ActionButton(icon = Icons.Default.Info, color = Color.Black.copy(alpha = 0.8f)) {
            onDismiss()
            launchAppSettings(context, app.packageName)
            onAppLaunched()
        }

        if (app.isUninstallable) {
            // Botón BORRAR
            ActionButton(icon = Icons.Default.Delete, color = Color(0xFFE53935).copy(alpha = 0.9f)) {
                onDismiss()
                launchAppUninstall(context, app.packageName)
                onAppLaunched()
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Dimens.ActionButtonSize)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(Dimens.IconSizeSmall))
    }
}

@Composable
fun FileItem(file: LocalFile, onClicked: () -> Unit) {
    val context = LocalContext.current

    ItemContainer(
        label = file.name,
        icon = if (file.mimeType?.contains("image") == true) Icons.Default.Image else Icons.Default.Description,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(file.uri, file.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onClicked()
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun ContactItem(contact: LocalContact, onClicked: () -> Unit) {
    val context = LocalContext.current
    ItemContainer(
        label = contact.name,
        icon = Icons.Default.Person,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, contact.uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                onClicked()
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun MessageItem(message: LocalMessage, onClicked: () -> Unit) {
    val context = LocalContext.current
    ItemContainer(
        label = message.sender,
        icon = Icons.Default.ChatBubble,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, message.uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                onClicked()
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun SystemActionItem(action: SystemAction, onClicked: () -> Unit) {
    val context = LocalContext.current
    ItemContainer(
        label = action.label,
        icon = action.icon,
        onClick = {
            try {
                action.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(action.intent)
                onClicked()
            } catch (e: Exception) {}
        }
    )
}

@Composable
fun WebSearchItem(query: String, onClicked: () -> Unit) {
    val label = stringResource(R.string.search_prefix, query)
    SystemActionItem(
        action = SystemAction(
            label = label,
            icon = Icons.Default.Language,
            intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", query) }
        ),
        onClicked = onClicked
    )
}

@Composable
fun SuggestionItem(
    text: String,
    onSearch: (String) -> Unit,
    onAutocomplete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { onSearch(text) }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        IconButton(
            onClick = { onAutocomplete(text) },
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

@Composable
fun GoogleSearchItem(query: String, onClicked: () -> Unit) {
    val context = LocalContext.current
    LongSearchItem(
        label = stringResource(R.string.search_google, query),
        icon = Icons.Default.Public,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onClicked()
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun SettingsSearchItem(query: String, onClicked: () -> Unit) {
    val context = LocalContext.current
    LongSearchItem(
        label = stringResource(R.string.search_settings, query),
        icon = Icons.Default.Settings,
        onClick = {
            onClicked()

            // Enviar broadcast al AccessibilityService para realizar la búsqueda
            val intent = Intent(Constants.ACTION_SETTINGS_SEARCH).apply {
                putExtra("query", query)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)

            // Abrir Settings normalmente
            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )
}

@Composable
fun PlayStoreSearchItem(query: String, onClicked: () -> Unit) {
    val context = LocalContext.current
    LongSearchItem(
        label = stringResource(R.string.search_play_store, query),
        icon = Icons.Default.Shop,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://search?q=$query")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onClicked()
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/search?q=$query")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            onClicked()
        }
    )
}

@Composable
fun YouTubeSearchItem(query: String, onClicked: () -> Unit) {
    val context = LocalContext.current
    LongSearchItem(
        label = stringResource(R.string.search_youtube, query),
        icon = Icons.Default.PlayCircle,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://www.youtube.com/results?search_query=$query")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            onClicked()
        }
    )
}

@Composable
private fun LongSearchItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun ItemContainer(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusMedium))
            .clickable { onClick() }
            .padding(Dimens.PaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.AppIconSize)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(Dimens.IconSizeMedium))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = Color.White,
            fontSize = Dimens.TextTiny
        )
    }
}

private fun launchAppSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun launchAppUninstall(context: Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
