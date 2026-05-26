package com.example.launcherorbys.ui.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diálogo de confirmación para detener la grabación de pantalla.
 * Se muestra sobre un fondo transparente que bloquea las interacciones con el sistema.
 */
@Composable
fun StopRecordingDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .clickable(enabled = false) {}, // Evita que toques accidentales pasen al fondo del diálogo
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(32.dp).widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "¿Detener grabación?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "El video se guardará en tu galería.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("No", color = Color.Black)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Sí, detener", color = Color.White)
                    }
                }
            }
        }
    }
}
