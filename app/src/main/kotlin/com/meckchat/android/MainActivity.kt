package com.meckchat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.meckchat.android.core.AppConfig
import com.meckchat.android.model.Device
import com.meckchat.android.network.MqttSignalingManager
import com.meckchat.android.ui.screens.ChatScreen
import com.meckchat.android.ui.screens.HomeScreen
import com.meckchat.android.ui.theme.MeckChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signalingManager = MqttSignalingManager.instance

        // Ensure MQTT connection is active
        if (!signalingManager.isConnected()) {
            signalingManager.connect()
        }

        setContent {
            MeckChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedPeer by remember { mutableStateOf<Device?>(null) }
                    val messagesMap by signalingManager.messagesMap.collectAsState()
                    val myDeviceId = AppConfig.instance.deviceId

                    if (selectedPeer != null) {
                        BackHandler {
                            selectedPeer = null
                        }
                        val peer = selectedPeer!!
                        val messages = messagesMap[peer.deviceId] ?: emptyList()

                        ChatScreen(
                            peerDevice = peer,
                            messages = messages,
                            myDeviceId = myDeviceId,
                            onSendMessage = { text ->
                                signalingManager.sendChatMessage(peer.deviceId, text)
                            },
                            onBack = {
                                selectedPeer = null
                            }
                        )
                    } else {
                        HomeScreen(
                            signalingManager = signalingManager,
                            onDeviceSelected = { device ->
                                selectedPeer = device
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure connection is active when returning to foreground
        if (!MqttSignalingManager.instance.isConnected()) {
            MqttSignalingManager.instance.connect()
        }
    }
}
