package com.example.launcherorbys.ui.status

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.launcherorbys.ui.theme.Dimens

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
            .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.StatusBarPaddingTop)
    ) {
        // Empuja el contenido hacia la derecha
        Spacer(modifier = Modifier.weight(1f))

        // Secciones modulares de estado
        UsbSection(context = context)
        BluetoothSection(context = context, onRequestPermission = onBluetoothRequest)
        WifiSection(context = context)
    }
}
