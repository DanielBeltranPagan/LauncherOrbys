package com.example.launchercalmado.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun StatusBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solo iconos de conectividad (BT y WiFi)
        BluetoothSection(context = context)
        Spacer(modifier = Modifier.width(12.dp))
        WifiSection(context = context)
    }
}

/**
 * Componente base para los iconos de la barra de estado con fondo circular.
 */
@Composable
fun StatusIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color.White, shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (isEnabled) Color.Black else Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun BluetoothSection(context: Context) {
    val bluetoothAdapter = remember { 
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBluetoothEnabled = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    if (bluetoothAdapter != null) {
        StatusIcon(
            imageVector = if (isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            contentDescription = "Bluetooth",
            isEnabled = isBluetoothEnabled,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )
    }
}

@Composable
fun WifiSection(context: Context) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var isWifiEnabled by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isWifiEnabled = true
            }

            override fun onLost(network: Network) {
                isWifiEnabled = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isWifiEnabled = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    StatusIcon(
        imageVector = if (isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
        contentDescription = "WiFi",
        isEnabled = isWifiEnabled,
        onClick = {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    )
}
