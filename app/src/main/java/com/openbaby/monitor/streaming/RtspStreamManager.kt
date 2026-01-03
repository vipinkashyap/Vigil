package com.openbaby.monitor.streaming

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtspStreamManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RtspStreamManager"
    }

    private var rtspServer: RtspServerCamera2? = null
    private var currentConfig: StreamConfig? = null
    private var isInitializedWithPreview = false
    private var currentOpenGlView: OpenGlView? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    private val _streamUrl = MutableStateFlow("")
    val streamUrl: StateFlow<String> = _streamUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            Log.d(TAG, "Connection started: $url")
        }

        override fun onConnectionSuccess() {
            Log.d(TAG, "Connection success")
            _isStreaming.value = true
            _errorMessage.value = null
        }

        override fun onConnectionFailed(reason: String) {
            Log.e(TAG, "Connection failed: $reason")
            _isStreaming.value = false
            _errorMessage.value = reason
        }

        override fun onNewBitrate(bitrate: Long) {
            Log.v(TAG, "New bitrate: $bitrate")
        }

        override fun onDisconnect() {
            Log.d(TAG, "Disconnected")
            _isStreaming.value = false
        }

        override fun onAuthError() {
            Log.e(TAG, "Auth error")
            _errorMessage.value = "Authentication error"
        }

        override fun onAuthSuccess() {
            Log.d(TAG, "Auth success")
        }
    }

    fun initialize(openGlView: OpenGlView, config: StreamConfig): Boolean {
        // If already streaming with same view, just return
        if (_isStreaming.value && rtspServer != null && currentOpenGlView == openGlView) {
            Log.d(TAG, "Already streaming with same view, skipping initialization")
            return true
        }

        // If streaming but view changed, attach new view
        if (_isStreaming.value && rtspServer != null) {
            Log.d(TAG, "Stream running, attaching new preview view")
            attachPreview(openGlView)
            return true
        }

        try {
            rtspServer = RtspServerCamera2(openGlView, connectChecker, config.port)
            currentConfig = config
            currentOpenGlView = openGlView
            isInitializedWithPreview = true
            _streamUrl.value = "rtsp://0.0.0.0:${config.port}/"
            Log.d(TAG, "RTSP Server initialized with preview on port ${config.port}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RTSP server", e)
            _errorMessage.value = "Failed to initialize: ${e.message}"
            return false
        }
    }

    fun initializeWithContext(config: StreamConfig): Boolean {
        // If already streaming, don't reinitialize
        if (_isStreaming.value && rtspServer != null) {
            Log.d(TAG, "Already streaming, skipping initialization")
            return true
        }

        try {
            rtspServer = RtspServerCamera2(context, connectChecker, config.port)
            currentConfig = config
            currentOpenGlView = null
            isInitializedWithPreview = false
            _streamUrl.value = "rtsp://0.0.0.0:${config.port}/"
            Log.d(TAG, "RTSP Server initialized without preview on port ${config.port}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RTSP server", e)
            _errorMessage.value = "Failed to initialize: ${e.message}"
            return false
        }
    }

    fun attachPreview(openGlView: OpenGlView) {
        currentOpenGlView = openGlView
        rtspServer?.replaceView(openGlView)
        Log.d(TAG, "Preview view attached")
    }

    fun detachPreview() {
        currentOpenGlView = null
        rtspServer?.replaceView(context)
        Log.d(TAG, "Preview view detached, switched to context mode")
    }

    fun prepareStream(config: StreamConfig): Boolean {
        val server = rtspServer ?: return false

        // If already streaming, don't re-prepare
        if (_isStreaming.value) {
            Log.d(TAG, "Already streaming, skipping prepare")
            return true
        }

        try {
            // prepareVideo(width, height, bitrate)
            val videoPrepared = server.prepareVideo(
                config.width,
                config.height,
                config.videoBitrate
            )

            // prepareAudio(bitrate, sampleRate, isStereo)
            val audioPrepared = server.prepareAudio(
                config.audioBitrate,
                config.audioSampleRate,
                config.stereo
            )

            if (!videoPrepared) {
                _errorMessage.value = "Failed to prepare video"
                Log.e(TAG, "Failed to prepare video")
                return false
            }

            if (!audioPrepared) {
                _errorMessage.value = "Failed to prepare audio"
                Log.e(TAG, "Failed to prepare audio")
                return false
            }

            Log.d(TAG, "Stream prepared: ${config.width}x${config.height}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare stream", e)
            _errorMessage.value = "Failed to prepare: ${e.message}"
            return false
        }
    }

    fun startStream(): Boolean {
        val server = rtspServer ?: return false

        // If already streaming, don't restart
        if (_isStreaming.value) {
            Log.d(TAG, "Already streaming")
            return true
        }

        try {
            server.startStream()
            _isStreaming.value = true
            Log.d(TAG, "Stream started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start stream", e)
            _errorMessage.value = "Failed to start: ${e.message}"
            return false
        }
    }

    fun stopStream() {
        try {
            rtspServer?.stopStream()
            _isStreaming.value = false
            _connectedClients.value = 0
            Log.d(TAG, "Stream stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping stream", e)
        }
    }

    fun switchCamera() {
        rtspServer?.switchCamera()
    }

    fun toggleFlash(): Boolean {
        val server = rtspServer ?: return false
        return try {
            if (server.isLanternEnabled) {
                server.disableLantern()
                false
            } else {
                server.enableLantern()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flash", e)
            false
        }
    }

    fun release() {
        stopStream()
        rtspServer = null
        currentConfig = null
    }

    fun isBackCamera(): Boolean {
        return rtspServer?.cameraFacing == CameraHelper.Facing.BACK
    }

    fun getNumClients(): Int {
        return rtspServer?.getStreamClient()?.getNumClients() ?: 0
    }

    fun isCurrentlyStreaming(): Boolean = _isStreaming.value
}
