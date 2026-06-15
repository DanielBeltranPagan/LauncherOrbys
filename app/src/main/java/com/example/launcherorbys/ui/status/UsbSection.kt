package com.example.launcherorbys.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import com.example.launcherorbys.managers.AppLauncher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*

/**
 * Gestiona el estado y la visualización del icono de USB en la barra de estado.
 *
 * Escucha eventos de conexión de dispositivos USB y montaje de medios para mostrar el icono
 * cuando hay una unidad de almacenamiento externa conectada. Al pulsar, abre el explorador de archivos.
 *
 * @param contexto El contexto de la aplicación.
 */
@Composable
fun UsbSection(contexto: Context) {
    val gestorUsb = remember { contexto.getSystemService(Context.USB_SERVICE) as UsbManager }

    fun tieneUsbExterno(): Boolean {
        return try {
            val tieneAlmacenamientoFisico = gestorUsb.deviceList.values.any { dispositivo ->
                dispositivo.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
                        (0 until dispositivo.interfaceCount).any { i ->
                            dispositivo.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                        }
            }

            if (!tieneAlmacenamientoFisico) return false

            val gestorAlmacenamiento = contexto.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            gestorAlmacenamiento.storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        } catch (_: Exception) {
            false
        }
    }

    var estaUsbConectado by remember { mutableStateOf(tieneUsbExterno()) }

    DisposableEffect(contexto) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                estaUsbConectado = tieneUsbExterno()
            }
        }

        val filtroUsb = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        val filtroMultimedia = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }

        contexto.registerReceiver(receptor, filtroUsb)
        contexto.registerReceiver(receptor, filtroMultimedia)

        onDispose {
            try {
                contexto.unregisterReceiver(receptor)
            } catch (_: Exception) {}
        }
    }

    StatusIcon(
        imageVector = Icons.Default.Usb,
        contentDescription = "USB",
        isVisible = estaUsbConectado,
        onClick = {
            if (estaUsbConectado) {
                AppLauncher(contexto).abrirAppArchivos()
            } else {
                Toast.makeText(contexto, "No hay USB conectado", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
