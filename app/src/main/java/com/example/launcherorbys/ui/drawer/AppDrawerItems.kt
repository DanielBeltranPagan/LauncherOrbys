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
import androidx.core.graphics.drawable.toBitmap
import com.example.launcherorbys.data.model.AppInfo

/**
 * Encabezado de sección para el LazyVerticalGrid.
 */
@Composable
fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))
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
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit
) {
    val context = LocalContext.current
    
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

    Box(
        modifier = Modifier.fillMaxWidth(),
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
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isSelected) {
                                onDismiss()
                            } else {
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    context.startActivity(intent)
                                }
                                onAppLaunched()
                            }
                        },
                        onLongPress = {
                            onSelect()
                        }
                    )
                }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.label, 
                style = MaterialTheme.typography.labelSmall, 
                maxLines = 1, 
                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            AppActionButtons(app = app, onDismiss = onDismiss, onAppLaunched = onAppLaunched)
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
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = 4.dp, y = (-12).dp)
            .wrapContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
    SystemActionItem(
        action = SystemAction(
            label = "Buscar: $query",
            icon = Icons.Default.Language,
            intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", query) }
        ),
        onClicked = onClicked
    )
}

@Composable
private fun ItemContainer(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = Color.White,
            fontSize = 10.sp
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
