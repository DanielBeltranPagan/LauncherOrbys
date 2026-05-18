package com.example.launcherorbys.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modelo de datos que representa un permiso requerido por la aplicación.
 * 
 * @property title Nombre del permiso.
 * @property description Explicación detallada de por qué se necesita.
 * @property isGranted Estado actual del permiso.
 * @property onGrantClick Acción a ejecutar cuando el usuario desea otorgar el permiso.
 */
data class PermissionItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onGrantClick: () -> Unit
)

/**
 * Pantalla de configuración inicial que solicita los permisos necesarios.
 * 
 * @param permissions Lista de objetos [PermissionItem] a mostrar.
 * @param onContinue Acción para navegar a la siguiente pantalla cuando todo esté listo.
 * @param allGranted Booleano que indica si todos los permisos críticos han sido concedidos.
 */
@Composable
fun PermissionScreen(
    permissions: List<PermissionItem>,
    onContinue: () -> Unit,
    allGranted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Cabecera de la pantalla
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Configuración Necesaria",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Para que Launcher Orbys funcione correctamente, por favor concede los siguientes permisos:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Lista scrollable de permisos
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(permissions) { item ->
                    PermissionCard(item)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón de acción principal
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = allGranted, // Solo se habilita si todos los permisos están concedidos
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continuar al Launcher", fontSize = 18.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Tarjeta individual para mostrar el estado de un permiso.
 * 
 * @param item El objeto [PermissionItem] que contiene la información del permiso.
 */
@Composable
fun PermissionCard(item: PermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            // Cambia el color de fondo según si el permiso está concedido o no
            containerColor = if (item.isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono de estado (Check o Error)
            Icon(
                imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (item.isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Textos informativos
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Botón de acción para el permiso (solo si no está concedido)
            if (!item.isGranted) {
                Button(
                    onClick = item.onGrantClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Configurar", fontSize = 12.sp)
                }
            }
        }
    }
}
