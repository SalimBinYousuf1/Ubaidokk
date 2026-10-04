package com.ubaid.hostt.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.files.FileManager
import com.ubaid.hostt.model.ConnectionStatus
import com.ubaid.hostt.model.DeviceStatus
import com.ubaid.hostt.model.LogCategory
import com.ubaid.hostt.service.UbaidAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class WebRtcHostManager(
    private val context: Context,
    private val hostId: String
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val firestore = FirebaseFirestore.getInstance()
    private val fileManager = FileManager()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    private var controlChannel: DataChannel? = null
    private var filesChannel: DataChannel? = null
    private var notificationsChannel: DataChannel? = null
    private var statusChannel: DataChannel? = null

    private var sessionListener: ListenerRegistration? = null
    private var lastHandledOfferSdp: String? = null
    private val processedCandidateSdpSet = HashSet<String>()

    private var projectionIntent: Intent? = null
    private var reconnectJob: Job? = null

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            eglBase = EglBase.create()
            val encoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            ActivityLogRepository.log(
                title = "WebRTC Engine Initialized",
                description = "Hardware video encoder and peer engine ready.",
                category = LogCategory.WEBRTC
            )
        } catch (e: Exception) {
            ActivityLogRepository.log(
                title = "WebRTC Init Error",
                description = e.localizedMessage ?: "Failed to initialize WebRTC",
                category = LogCategory.WEBRTC
            )
        }
    }

    fun setMediaProjectionIntent(intent: Intent) {
        this.projectionIntent = intent
    }

    fun startSignaling() {
        _connectionStatus.value = ConnectionStatus.WAITING_FOR_CONTROLLER
        listenForSignalingEvents()
    }

    private fun listenForSignalingEvents() {
        sessionListener?.remove()
        val docRef = firestore.collection("sessions").document(hostId)

        // Initialize session document if needed
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val initData = hashMapOf(
                    "hostId" to hostId,
                    "status" to "waiting",
                    "updatedAt" to System.currentTimeMillis()
                )
                docRef.set(initData)
            } else {
                docRef.update(
                    "status", "waiting",
                    "updatedAt", System.currentTimeMillis()
                )
            }
        }

        sessionListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                ActivityLogRepository.log(
                    title = "Firestore Signaling Error",
                    description = error.localizedMessage ?: "Listener failed",
                    category = LogCategory.WEBRTC
                )
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                handleSessionSnapshot(snapshot)
            }
        }

        ActivityLogRepository.log(
            title = "Signaling Mailbox Active",
            description = "Listening on Firestore session: $hostId",
            category = LogCategory.WEBRTC
        )
    }

    private fun handleSessionSnapshot(snapshot: DocumentSnapshot) {
        val offerMap = snapshot.get("offer") as? Map<*, *>
        if (offerMap != null) {
            val offerSdp = offerMap["sdp"] as? String
            val offerType = offerMap["type"] as? String
            if (!offerSdp.isNullOrBlank() && offerType.equals("offer", ignoreCase = true)) {
                if (offerSdp != lastHandledOfferSdp) {
                    lastHandledOfferSdp = offerSdp
                    handleRemoteOffer(offerSdp)
                }
            }
        }

        val salimCandidates = snapshot.get("salimCandidates") as? List<*>
        if (salimCandidates != null && peerConnection != null) {
            for (item in salimCandidates) {
                val candMap = item as? Map<*, *> ?: continue
                val sdp = candMap["candidate"] as? String ?: continue
                val sdpMid = candMap["sdpMid"] as? String ?: "0"
                val sdpMLineIndex = (candMap["sdpMLineIndex"] as? Number)?.toInt() ?: 0

                if (!processedCandidateSdpSet.contains(sdp)) {
                    processedCandidateSdpSet.add(sdp)
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun createPeerConnection(): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    sendLocalIceCandidate(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                ActivityLogRepository.log(
                    title = "ICE State: $state",
                    description = "Connection transition",
                    category = LogCategory.WEBRTC
                )
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionStatus.value = ConnectionStatus.WAITING_FOR_CONTROLLER
                        triggerAutoReconnect()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionStatus.value = ConnectionStatus.ERROR
                        triggerAutoReconnect()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(dataChannel: DataChannel?) {
                if (dataChannel != null) {
                    setupDataChannel(dataChannel)
                }
            }

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        val pc = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        peerConnection = pc

        // Attach video track if projection intent is present
        attachScreenCaptureTrack(pc)

        // Create outgoing data channels
        val init = DataChannel.Init()
        controlChannel = pc?.createDataChannel("control", init)?.also { setupDataChannel(it) }
        filesChannel = pc?.createDataChannel("files", init)?.also { setupDataChannel(it) }
        notificationsChannel = pc?.createDataChannel("notifications", init)?.also { setupDataChannel(it) }
        statusChannel = pc?.createDataChannel("status", init)?.also { setupDataChannel(it) }

        return pc
    }

    private fun attachScreenCaptureTrack(pc: PeerConnection?) {
        val intent = projectionIntent ?: return
        val factory = peerConnectionFactory ?: return
        val egl = eglBase ?: return

        try {
            if (screenCapturer == null) {
                screenCapturer = ScreenCapturerAndroid(intent, object : MediaProjection.Callback() {
                    override fun onStop() {
                        ActivityLogRepository.log(
                            title = "Screen Capture Stopped",
                            description = "MediaProjection session ended by system.",
                            category = LogCategory.SYSTEM
                        )
                    }
                })

                surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", egl.eglBaseContext)
                videoSource = factory.createVideoSource(true)
                screenCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

                val displayMetrics = context.resources.displayMetrics
                val width = (displayMetrics.widthPixels / 2).coerceAtLeast(360)
                val height = (displayMetrics.heightPixels / 2).coerceAtLeast(640)
                screenCapturer?.startCapture(width, height, 30)

                videoTrack = factory.createVideoTrack("UBAID_SCREEN_TRACK", videoSource)
                videoTrack?.setEnabled(true)
            }

            videoTrack?.let { track ->
                pc?.addTrack(track, listOf("ubaid_stream"))
                ActivityLogRepository.log(
                    title = "Screen Video Track Added",
                    description = "Mirroring stream attached to WebRTC peer.",
                    category = LogCategory.WEBRTC
                )
            }
        } catch (e: Exception) {
            ActivityLogRepository.log(
                title = "Screen Capture Error",
                description = e.localizedMessage ?: "Failed to start capture",
                category = LogCategory.SYSTEM
            )
        }
    }

    private fun handleRemoteOffer(offerSdp: String) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        ActivityLogRepository.log(
            title = "Offer Received from Salim",
            description = "Negotiating WebRTC session...",
            category = LogCategory.WEBRTC
        )

        closeCurrentPeerConnection()
        val pc = createPeerConnection() ?: return

        val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createAndSendAnswer(pc)
            }

            override fun onSetFailure(error: String?) {
                ActivityLogRepository.log(
                    title = "Failed Setting Remote Offer",
                    description = error ?: "Unknown SDP error",
                    category = LogCategory.WEBRTC
                )
            }

            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

    private fun createAndSendAnswer(pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val answerMap = hashMapOf(
                            "sdp" to desc.description,
                            "type" to "answer"
                        )
                        firestore.collection("sessions").document(hostId).update(
                            "answer", answerMap,
                            "status", "connecting",
                            "ubaidCandidates", emptyList<Map<String, Any>>(),
                            "updatedAt", System.currentTimeMillis()
                        ).addOnSuccessListener {
                            ActivityLogRepository.log(
                                title = "Answer Sent to Salim",
                                description = "SDP handshake dispatched via Firestore.",
                                category = LogCategory.WEBRTC
                            )
                        }
                    }

                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                ActivityLogRepository.log(
                    title = "Failed Creating Answer",
                    description = error ?: "Unknown error",
                    category = LogCategory.WEBRTC
                )
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun sendLocalIceCandidate(candidate: IceCandidate) {
        val candidateMap = hashMapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        firestore.collection("sessions").document(hostId).update(
            "ubaidCandidates", FieldValue.arrayUnion(candidateMap)
        )
    }

    private fun setupDataChannel(channel: DataChannel) {
        val name = channel.label()
        when (name) {
            "control" -> controlChannel = channel
            "files" -> filesChannel = channel
            "notifications" -> notificationsChannel = channel
            "status" -> statusChannel = channel
        }

        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}

            override fun onStateChange() {
                ActivityLogRepository.log(
                    title = "DataChannel: $name",
                    description = "State changed to: ${channel.state()}",
                    category = LogCategory.WEBRTC
                )
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val text = String(data, StandardCharsets.UTF_8)
                handleIncomingDataChannelMessage(name, text)
            }
        })
    }

    private fun handleIncomingDataChannelMessage(channelName: String, messageText: String) {
        try {
            val json = JSONObject(messageText)
            when (channelName) {
                "control" -> handleControlMessage(json)
                "files" -> handleFileMessage(json)
                else -> {}
            }
        } catch (e: Exception) {
            ActivityLogRepository.log(
                title = "Malformed Command",
                description = "Channel: $channelName, Text: ${messageText.take(50)}",
                category = LogCategory.SYSTEM
            )
        }
    }

    private fun handleControlMessage(json: JSONObject) {
        val type = json.optString("type")
        val accessibilityService = UbaidAccessibilityService.instance
        if (accessibilityService == null) {
            ActivityLogRepository.log(
                title = "Accessibility Not Enabled",
                description = "Cannot dispatch $type without Accessibility permission.",
                category = LogCategory.TOUCH
            )
            return
        }

        when (type) {
            "tap" -> {
                val x = json.optDouble("x", 0.5).toFloat()
                val y = json.optDouble("y", 0.5).toFloat()
                accessibilityService.dispatchTap(x, y)
            }
            "swipe" -> {
                val x1 = json.optDouble("x1", 0.5).toFloat()
                val y1 = json.optDouble("y1", 0.5).toFloat()
                val x2 = json.optDouble("x2", 0.5).toFloat()
                val y2 = json.optDouble("y2", 0.5).toFloat()
                val duration = json.optLong("duration", 300L)
                accessibilityService.dispatchSwipe(x1, y1, x2, y2, duration)
            }
            "key" -> {
                val action = json.optString("action")
                accessibilityService.executeGlobalNavAction(action)
            }
            "text" -> {
                val text = json.optString("text")
                accessibilityService.injectTextInput(text)
            }
        }
    }

    private fun handleFileMessage(json: JSONObject) {
        val type = json.optString("type")
        scope.launch(Dispatchers.IO) {
            val response: JSONObject = when (type) {
                "list_dir" -> {
                    val path = json.optString("path", "")
                    fileManager.listDirectory(path)
                }
                "read_file" -> {
                    val path = json.optString("path")
                    val offset = json.optLong("offset", 0L)
                    val chunkSize = json.optInt("chunkSize", 64 * 1024)
                    fileManager.readFileChunk(path, offset, chunkSize).toJson()
                }
                "write_file" -> {
                    val path = json.optString("path")
                    val data = json.optString("data")
                    val append = json.optBoolean("append", false)
                    fileManager.writeFileChunk(path, data, append)
                }
                "delete_file" -> {
                    val path = json.optString("path")
                    fileManager.deleteFile(path)
                }
                else -> {
                    JSONObject().apply {
                        put("error", "Unknown file command: $type")
                    }
                }
            }
            sendFileResponse(response)
        }
    }

    private fun sendFileResponse(json: JSONObject) {
        val channel = filesChannel ?: return
        if (channel.state() == DataChannel.State.OPEN) {
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            channel.send(buffer)
        }
    }

    fun sendNotification(json: JSONObject) {
        val channel = notificationsChannel ?: return
        if (channel.state() == DataChannel.State.OPEN) {
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            channel.send(buffer)
        }
    }

    fun sendDeviceStatus(status: DeviceStatus) {
        val channel = statusChannel ?: return
        if (channel.state() == DataChannel.State.OPEN) {
            val json = JSONObject().apply {
                put("type", "status")
                put("batteryPercent", status.batteryPercent)
                put("isCharging", status.isCharging)
                put("powerSource", status.powerSource)
                put("networkType", status.networkType)
                put("ipAddress", status.ipAddress)
                put("isScreenOn", status.isScreenOn)
                put("uptimeSeconds", status.uptimeSeconds)
                put("timestamp", System.currentTimeMillis())
            }
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            channel.send(buffer)
        }
    }

    private fun triggerAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            ActivityLogRepository.log(
                title = "Reconnection Scheduled",
                description = "Attempting automatic peer recovery in 3s...",
                category = LogCategory.WEBRTC
            )
            delay(3000L)
            lastHandledOfferSdp = null
            processedCandidateSdpSet.clear()
            closeCurrentPeerConnection()
            _connectionStatus.value = ConnectionStatus.WAITING_FOR_CONTROLLER
            firestore.collection("sessions").document(hostId).update(
                "status", "waiting",
                "answer", FieldValue.delete(),
                "offer", FieldValue.delete(),
                "salimCandidates", emptyList<Map<String, Any>>(),
                "ubaidCandidates", emptyList<Map<String, Any>>(),
                "updatedAt", System.currentTimeMillis()
            )
        }
    }

    private fun closeCurrentPeerConnection() {
        try {
            controlChannel?.close()
            filesChannel?.close()
            notificationsChannel?.close()
            statusChannel?.close()
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {}
        peerConnection = null
        controlChannel = null
        filesChannel = null
        notificationsChannel = null
        statusChannel = null
    }

    fun destroy() {
        sessionListener?.remove()
        reconnectJob?.cancel()
        closeCurrentPeerConnection()
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            surfaceTextureHelper?.dispose()
            videoSource?.dispose()
            videoTrack?.dispose()
            peerConnectionFactory?.dispose()
            eglBase?.release()
        } catch (_: Exception) {}
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
