package com.example.launcherorbys.ui.status

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.launcherorbys.ui.theme.Dimens

/**
 * Barra de estado superior personalizada del Launcher.
 *
 * Este componente es el encargado de mostrar la información de conectividad y estado del hardware
 * de forma no intrusiva. Se alinea automáticamente a la parte superior de la pantalla y ajusta
 * su relleno basándose en la posición de la barra de navegación del sistema.
 *
 * Contiene secciones para:
 * - [UsbSection]: Estado de pendrives y almacenamiento externo.
 * - [BluetoothSection]: Estado del adaptador y dispositivos conectados.
 * - [WifiSection]: Conexión a redes inalámbricas.
 *
 * @param modifier Modificador de Compose para personalizar el diseño, dimensiones o alineación.
 * @param alSolicitarBluetooth Función de callback invocado cuando el usuario interactúa con la sección de Bluetooth sin tener los permisos necesarios.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    alSolicitarBluetooth: () -> Unit
) {
    val contexto = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.StatusBarPaddingTop)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        UsbSection(contexto = contexto)
        BluetoothSection(contexto = contexto, alSolicitarPermiso = alSolicitarBluetooth)
        WifiSection(contexto = contexto)
    }
}
