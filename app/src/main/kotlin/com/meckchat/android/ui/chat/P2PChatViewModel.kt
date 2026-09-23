package com.meckchat.android.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meckchat.android.core.Logger
import com.meckchat.android.data.remote.MqttSignalingService
import com.meckchat.android.data.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.DataChannel

data class P2PChatMessage(
    val sender: String, // "You" or "Peer"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val keyword: String = "",
    val status: String = "Disconnected",
    val messages: List<P2PChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false
)

class P2PChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "P2PChatViewModel"
        private const val TIMEOUT_MILLIS = 15000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var signalingService: MqttSignalingService? = null
    private var webRtcManager: WebRtcManager? = null
    private var timeoutJob: Job? = null
    private var signalingJob: Job? = null
    private var dataChannelStateJob: Job? = null
    private var incomingMessagesJob: Job? = null

    /**
     * Initiates the P2P room connection as the caller (offers SDP and creates DataChannel).
     */
    fun startAsCaller(keyword: String) {
        val sanitized = keyword.trim()
        if (sanitized.isEmpty()) return

        disconnect()
        _uiState.update {
            it.copy(
                keyword = sanitized,
                status = "Connecting...",
                isConnecting = true,
                isConnected = false
            )
        }

        startTimeoutTimer()

        viewModelScope.launch(Dispatchers.IO) {
            val signaling = MqttSignalingService()
            signalingService = signaling

            val rtcManager = WebRtcManager(
                context = getApplication<Application>().applicationContext,
                onSendSignal = { signal ->
                    signaling.publishSignal(signal)
                }
            )
            webRtcManager = rtcManager

            observeSignals(signaling, rtcManager)
            observeDataChannel(rtcManager)
            observeIncomingMessages(rtcManager)

            signaling.connectAndSubscribe(
                keyword = sanitized,
                onConnected = {
                    Logger.info(TAG, "Signaling connected. Creating WebRTC PeerConnection as Caller...")
                    rtcManager.createPeerConnection(isCaller = true)
                    rtcManager.createOffer()
                },
                onError = { error ->
                    Logger.error(TAG, "Signaling error: ${error.message}", error)
                    _uiState.update { it.copy(status = "Signaling error: ${error.message}", isConnecting = false) }
                }
            )
        }
    }

    /**
     * Joins the P2P room as the callee (listens for incoming offer, then answers).
     */
    fun startAsCallee(keyword: String) {
        val sanitized = keyword.trim()
        if (sanitized.isEmpty()) return

        disconnect()
        _uiState.update {
            it.copy(
                keyword = sanitized,
                status = "Connecting...",
                isConnecting = true,
                isConnected = false
            )
        }

        startTimeoutTimer()

        viewModelScope.launch(Dispatchers.IO) {
            val signaling = MqttSignalingService()
            signalingService = signaling

            val rtcManager = WebRtcManager(
                context = getApplication<Application>().applicationContext,
                onSendSignal = { signal ->
                    signaling.publishSignal(signal)
                }
            )
            webRtcManager = rtcManager

            observeSignals(signaling, rtcManager)
            observeDataChannel(rtcManager)
            observeIncomingMessages(rtcManager)

            signaling.connectAndSubscribe(
                keyword = sanitized,
                onConnected = {
                    Logger.info(TAG, "Signaling connected. Creating WebRTC PeerConnection as Callee...")
                    rtcManager.createPeerConnection(isCaller = false)
                },
                onError = { error ->
                    Logger.error(TAG, "Signaling error: ${error.message}", error)
                    _uiState.update { it.copy(status = "Signaling error: ${error.message}", isConnecting = false) }
                }
            )
        }
    }

    private fun observeSignals(signaling: MqttSignalingService, rtcManager: WebRtcManager) {
        signalingJob?.cancel()
        signalingJob = viewModelScope.launch(Dispatchers.IO) {
            signaling.incomingSignals.collect { signal ->
                rtcManager.handleSignalingMessage(signal)
            }
        }
    }

    private fun observeDataChannel(rtcManager: WebRtcManager) {
        dataChannelStateJob?.cancel()
        dataChannelStateJob = viewModelScope.launch(Dispatchers.IO) {
            rtcManager.dataChannelState.collect { state ->
                when (state) {
                    DataChannel.State.OPEN -> {
                        timeoutJob?.cancel()
                        _uiState.update {
                            it.copy(
                                status = "Connected (P2P)",
                                isConnected = true,
                                isConnecting = false
                            )
                        }
                    }
                    DataChannel.State.CLOSING, DataChannel.State.CLOSED -> {
                        _uiState.update {
                            it.copy(
                                status = "Disconnected",
                                isConnected = false,
                                isConnecting = false
                            )
                        }
                    }
                    DataChannel.State.CONNECTING -> {
                        _uiState.update {
                            it.copy(
                                status = "Connecting...",
                                isConnected = false,
                                isConnecting = true
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages(rtcManager: WebRtcManager) {
        incomingMessagesJob?.cancel()
        incomingMessagesJob = viewModelScope.launch(Dispatchers.IO) {
            rtcManager.incomingMessages.collect { text ->
                val newMsg = P2PChatMessage(sender = "Peer", text = text)
                _uiState.update {
                    it.copy(messages = it.messages + newMsg)
                }
            }
        }
    }

    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch(Dispatchers.IO) {
            delay(TIMEOUT_MILLIS)
            if (!_uiState.value.isConnected) {
                Logger.warning(TAG, "WebRTC connection timed out after 15s")
                _uiState.update {
                    it.copy(
                        status = "Could not reach peer. Try same Wi-Fi.",
                        isConnecting = false,
                        isConnected = false
                    )
                }
            }
        }
    }

    /**
     * Sends message text through the WebRTC data channel and updates local message list.
     */
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val manager = webRtcManager ?: return
        if (_uiState.value.isConnected) {
            val sent = manager.sendMessage(trimmed)
            if (sent) {
                val newMsg = P2PChatMessage(sender = "You", text = trimmed)
                _uiState.update {
                    it.copy(messages = it.messages + newMsg)
                }
            }
        }
    }

    /**
     * Cleans up signaling and WebRTC peer connection.
     */
    fun disconnect() {
        timeoutJob?.cancel()
        signalingJob?.cancel()
        dataChannelStateJob?.cancel()
        incomingMessagesJob?.cancel()

        signalingService?.disconnect()
        signalingService = null

        webRtcManager?.dispose()
        webRtcManager = null

        _uiState.update {
            it.copy(
                status = "Disconnected",
                isConnected = false,
                isConnecting = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
