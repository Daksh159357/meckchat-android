package com.meckchat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.meckchat.android.network.MqttSignalingManager
import com.meckchat.android.ui.screens.HomeScreen
import com.meckchat.android.ui.theme.MeckChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure MQTT connection is active
        if (!MqttSignalingManager.instance.isConnected()) {
            MqttSignalingManager.instance.connect()
        }

        setContent {
            MeckChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
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
