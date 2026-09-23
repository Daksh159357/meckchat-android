package com.meckchat.android.data.webrtc

import android.content.Context
import com.meckchat.android.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Manages WebRTC PeerConnection and DataChannel for direct P2P chat across NATs.
 * Uses public Google STUN servers only (no private signaling, no TURN).
 */
class WebRtcManager(
    private val context: Context,
    private val onSendSignal: (Map<String, Any>) -> Unit
) {

    companion object {
        private const val TAG = "WebRtcManager"
        private const val DATA_CHANNEL_LABEL = "chat"
        @Volatile private var factoryInitialized = false
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val _dataChannelState = MutableStateFlow(DataChannel.State.CONNECTING)
    val dataChannelState: StateFlow<DataChannel.State> = _dataChannelState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        Logger.info(TAG, "Initializing PeerConnectionFactory with EglBase context")
        eglBase = EglBase.create()

        if (!factoryInitialized) {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)
            factoryInitialized = true
        }

        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    /**
     * Initializes the RTCPeerConnection with Google's public STUN servers.
     * @param isCaller True if this device initiates the offer and creates the DataChannel.
     */
    fun createPeerConnection(isCaller: Boolean) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Logger.info(TAG, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Logger.info(TAG, "onIceConnectionChange: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Logger.info(TAG, "onIceConnectionReceivingChange: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Logger.info(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Logger.info(TAG, "Discovered local ICE candidate: ${candidate.sdpMid}")
                    val candidateMap = mapOf(
                        "type" to "candidate",
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "candidate" to candidate.sdp
                    )
                    onSendSignal(candidateMap)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(dc: DataChannel?) {
                Logger.info(TAG, "Callee received DataChannel: ${dc?.label()}")
                if (dc != null) {
                    dataChannel = dc
                    setupDataChannel(dc)
                }
            }

            override fun onRenegotiationNeeded() {
                Logger.info(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, observer)

        if (isCaller) {
            Logger.info(TAG, "Caller creating DataChannel: $DATA_CHANNEL_LABEL")
            val dcInit = DataChannel.Init().apply {
                ordered = true
            }
            dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
            dataChannel?.let { setupDataChannel(it) }
        }
    }

    private fun setupDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = dc.state()
                Logger.info(TAG, "DataChannel state changed: $state")
                _dataChannelState.value = state
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer != null) {
                    val data = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    Logger.info(TAG, "Received message over DataChannel: $text")
                    scope.launch {
                        _incomingMessages.emit(text)
                    }
                }
            }
        })
    }

    /**
     * Creates an SDP Offer and publishes it through signaling.
     */
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    Logger.info(TAG, "SDP Offer created successfully")
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    val signal = mapOf(
                        "type" to "offer",
                        "sdp" to desc.description
                    )
                    onSendSignal(signal)
                }
            }

            override fun onCreateFailure(error: String?) {
                Logger.error(TAG, "Failed to create SDP offer: $error")
            }
        }, constraints)
    }

    /**
     * Handles signaling payloads (offer, answer, candidate) arriving from MQTT.
     */
    fun handleSignalingMessage(signal: Map<String, Any>) {
        val type = signal["type"] as? String ?: return

        when (type) {
            "offer" -> {
                val sdpStr = signal["sdp"] as? String ?: return
                Logger.info(TAG, "Handling SDP Offer from remote peer")
                val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Logger.info(TAG, "Remote SDP Offer set successfully. Creating answer...")
                        drainPendingCandidates()
                        createAnswer()
                    }

                    override fun onSetFailure(error: String?) {
                        Logger.error(TAG, "Failed to set remote SDP offer: $error")
                    }
                }, remoteDesc)
            }

            "answer" -> {
                val sdpStr = signal["sdp"] as? String ?: return
                Logger.info(TAG, "Handling SDP Answer from remote peer")
                val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Logger.info(TAG, "Remote SDP Answer set successfully")
                        drainPendingCandidates()
                    }

                    override fun onSetFailure(error: String?) {
                        Logger.error(TAG, "Failed to set remote SDP answer: $error")
                    }
                }, remoteDesc)
            }

            "candidate" -> {
                val sdpMid = signal["sdpMid"] as? String ?: ""
                val sdpMLineIndex = (signal["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                val candidateSdp = signal["candidate"] as? String ?: ""

                if (candidateSdp.isNotEmpty()) {
                    val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                    if (peerConnection?.remoteDescription != null) {
                        Logger.info(TAG, "Adding remote ICE candidate: $sdpMid")
                        peerConnection?.addIceCandidate(iceCandidate)
                    } else {
                        Logger.info(TAG, "Buffering remote ICE candidate until remote description is set")
                        synchronized(pendingIceCandidates) {
                            pendingIceCandidates.add(iceCandidate)
                        }
                    }
                }
            }
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    Logger.info(TAG, "SDP Answer created successfully")
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    val signal = mapOf(
                        "type" to "answer",
                        "sdp" to desc.description
                    )
                    onSendSignal(signal)
                }
            }

            override fun onCreateFailure(error: String?) {
                Logger.error(TAG, "Failed to create SDP answer: $error")
            }
        }, constraints)
    }

    private fun drainPendingCandidates() {
        synchronized(pendingIceCandidates) {
            for (candidate in pendingIceCandidates) {
                Logger.info(TAG, "Applying buffered ICE candidate: ${candidate.sdpMid}")
                peerConnection?.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    /**
     * Sends a text message directly through the open WebRTC DataChannel.
     */
    fun sendMessage(text: String): Boolean {
        val dc = dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            Logger.warning(TAG, "DataChannel is not open. Cannot send message.")
            return false
        }

        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
        return dc.send(buffer)
    }

    /**
     * Releases and disposes of all WebRTC resources.
     */
    fun dispose() {
        try {
            Logger.info(TAG, "Disposing WebRtcManager resources")
            dataChannel?.close()
            dataChannel = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            factory?.dispose()
            factory = null

            eglBase?.release()
            eglBase = null

            synchronized(pendingIceCandidates) {
                pendingIceCandidates.clear()
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Error disposing WebRtcManager: ${e.message}", e)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
