package com.meckchat.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meckchat.android.core.AppConfig
import com.meckchat.android.model.Device
import com.meckchat.android.model.P2PConnectionState
import com.meckchat.android.network.ConnectionState
import com.meckchat.android.network.MqttSignalingManager
import com.meckchat.android.network.P2PTransportManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    signalingManager: MqttSignalingManager = MqttSignalingManager.instance,
    onDeviceSelected: (Device) -> Unit = {}
) {
    val connectionState by signalingManager.connectionState.collectAsState()
    val errorMessage by signalingManager.errorMessage.collectAsState()
    val discoveredDevices by signalingManager.discoveredDevices.collectAsState()
    val peerStates by P2PTransportManager.instance.peerStates.collectAsState()
    val myDevice = signalingManager.getCurrentDevice()
    val offlineCount = discoveredDevices.count { !it.isOnline }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MeckChat Android") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (statusColor, statusText) = when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50) to "MQTT Connected"
                                ConnectionState.CONNECTING -> Color(0xFFFF9800) to "MQTT Connecting..."
                                ConnectionState.RECONNECTING -> Color(0xFFFF9800) to "MQTT Reconnecting..."
                                ConnectionState.ERROR -> Color(0xFFE53935) to "MQTT Error"
                                ConnectionState.DISCONNECTED -> Color(0xFF757575) to "MQTT Disconnected"
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                                    signalingManager.connect()
                                } else {
                                    signalingManager.broadcastDiscovery()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh / Discovery",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val buttonText = when (connectionState) {
                                ConnectionState.DISCONNECTED -> "Connect"
                                ConnectionState.ERROR -> "Retry"
                                else -> "Discover"
                            }
                            Text(buttonText)
                        }
                    }

                    if (errorMessage != null && (connectionState == ConnectionState.ERROR || connectionState == ConnectionState.RECONNECTING)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: $errorMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device ID: ${myDevice.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Broker: ${AppConfig.instance.mqttBrokerHost}:${AppConfig.instance.mqttBrokerPort} (TLS Signaling)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "P2P Server: TCP :7788 (Active)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Discovered Devices (${discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (offlineCount > 0) {
                    TextButton(
                        onClick = { signalingManager.clearOfflineDevices() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Offline",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear Offline ($offlineCount)",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No devices discovered yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Listening on meckchat/v1/discovery...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDevices, key = { it.deviceId }) { device ->
                        val p2pState = peerStates[device.deviceId] ?: P2PConnectionState.DISCOVERING
                        DeviceItemCard(
                            device = device,
                            p2pState = p2pState,
                            onClick = { onDeviceSelected(device) },
                            onRemove = { signalingManager.removeDevice(device.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: Device,
    p2pState: P2PConnectionState = P2PConnectionState.DISCOVERING,
    onClick: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val endpointStr = if (device.endpoints.isNotEmpty()) {
        device.endpoints.joinToString(", ") { "${it.host}:${it.port}" }
    } else {
        "Direct TCP :7788"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.deviceId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Platform: ${device.platform.uppercase()} • $endpointStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    val (badgeText, badgeBg, badgeTextColor) = when {
                        !device.isOnline -> Triple("OFFLINE", Color(0xFFFFEBEE), Color(0xFFC62828))
                        p2pState == P2PConnectionState.P2P_CONNECTED -> Triple("P2P CONNECTED", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                        p2pState == P2PConnectionState.P2P_CONNECTING -> Triple("P2P CONNECTING", Color(0xFFFFF3E0), Color(0xFFEF6C00))
                        else -> Triple("DISCOVERED", Color(0xFFE3F2FD), Color(0xFF1565C0))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (device.isOnline) "Tap to Chat" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                if (!device.isOnline) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Offline Device",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
