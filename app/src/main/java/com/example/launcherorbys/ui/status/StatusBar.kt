package com.example.launcherorbys.ui.status

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Barra de estado superior del Launcher.
 * Muestra iconos de conectividad (WiFi y Bluetooth) alineados a la derecha.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    onBluetoothRequest: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Empuja el contenido hacia la derecha
        Spacer(modifier = Modifier.weight(1f))

        // Secciones modulares de estado
        UsbSection(context = context)
        BluetoothSection(context = context, onRequestPermission = onBluetoothRequest)
        WifiSection(context = context)
    }
}
