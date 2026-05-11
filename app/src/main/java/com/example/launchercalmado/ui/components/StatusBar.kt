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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val color = if (isDarkTheme) Color.White else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solo iconos de conectividad (BT y WiFi)
        BluetoothSection(color = color, context = context)
        Spacer(modifier = Modifier.width(12.dp))
        WifiSection(color = color, context = context)
    }
}

@Composable
fun BluetoothSection(color: Color, context: Context) {
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
        Icon(
            imageVector = if (isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            contentDescription = "Bluetooth",
            tint = if (isBluetoothEnabled) color else color.copy(alpha = 0.4f),
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
        )
    }
}

@Composable
fun WifiSection(color: Color, context: Context) {
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

    Icon(
        imageVector = if (isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
        contentDescription = "WiFi",
        tint = color,
        modifier = Modifier
            .size(20.dp)
            .clickable {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
    )
}
