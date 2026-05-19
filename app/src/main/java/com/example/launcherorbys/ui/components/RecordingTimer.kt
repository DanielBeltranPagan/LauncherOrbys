package com.example.launcherorbys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Componente que muestra el tiempo transcurrido de grabación con un diseño destacado (píldora roja).
 * 
 * @param seconds Segundos totales transcurridos.
 * @param onStop Acción a realizar al pulsar sobre el cronómetro.
 */
@Composable
fun RecordingTimer(seconds: Int, onStop: () -> Unit) {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)

    Surface(
        color = Color.Red,
        shape = CircleShape,
        modifier = Modifier
            .height(28.dp)
            .clickable { onStop() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
            Text(
                text = timeString,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
