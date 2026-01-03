package com.openbaby.monitor.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbaby.monitor.data.SettingsRepository
import com.openbaby.monitor.service.AudioAnalyzer
import com.openbaby.monitor.service.MonitorService
import com.openbaby.monitor.streaming.RtspStreamManager
import com.openbaby.monitor.streaming.StreamConfig
import com.pedro.library.view.OpenGlView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    application: Application,
    private val rtspStreamManager: RtspStreamManager,
    private val settingsRepository: SettingsRepository,
    private val audioAnalyzer: AudioAnalyzer
) : AndroidViewModel(application) {

    val isStreaming: StateFlow<Boolean> = rtspStreamManager.isStreaming

    val connectedClients: StateFlow<Int> = rtspStreamManager.connectedClients

    val errorMessage: StateFlow<String?> = rtspStreamManager.errorMessage

    // Audio analysis
    val audioLevel: StateFlow<Float> = audioAnalyzer.audioLevel
    val cryDetected: StateFlow<Boolean> = audioAnalyzer.cryDetected
    val cryConfidence: StateFlow<Float> = audioAnalyzer.cryConfidence

    private var serviceStarted = false

    fun startStreaming(openGlView: OpenGlView?) {
        viewModelScope.launch {
            // If already streaming, just attach the preview view if provided
            if (rtspStreamManager.isCurrentlyStreaming()) {
                openGlView?.let { rtspStreamManager.attachPreview(it) }
                return@launch
            }

            // Start foreground service first
            if (!serviceStarted) {
                MonitorService.startService(getApplication())
                serviceStarted = true
            }

            val settings = settingsRepository.streamSettings.first()
            val config = StreamConfig.fromQuality(settings.quality, settings.framerate).copy(
                port = settings.port,
                audioBitrate = settings.audioBitrate
            )

            // Use OpenGlView-based initialization if view is available (shows preview)
            // Otherwise use context-based (background only, no preview)
            val initialized = if (openGlView != null) {
                rtspStreamManager.initialize(openGlView, config)
            } else {
                rtspStreamManager.initializeWithContext(config)
            }

            if (initialized) {
                if (rtspStreamManager.prepareStream(config)) {
                    rtspStreamManager.startStream()
                    // Start audio analysis for cry detection
                    audioAnalyzer.startAnalyzing()
                }
            }
        }
    }

    fun onSurfaceDestroyed() {
        // Switch to context-based streaming (no preview) when surface is destroyed
        // This allows streaming to continue in background
        rtspStreamManager.detachPreview()
    }

    fun stopStreaming() {
        audioAnalyzer.stopAnalyzing()
        rtspStreamManager.stopStream()
        rtspStreamManager.release()
        if (serviceStarted) {
            MonitorService.stopService(getApplication())
            serviceStarted = false
        }
    }

    fun switchCamera() {
        rtspStreamManager.switchCamera()
    }

    fun toggleFlash(): Boolean {
        return rtspStreamManager.toggleFlash()
    }

    override fun onCleared() {
        super.onCleared()
        // Don't stop streaming when ViewModel is cleared - let service handle it
        // This allows streaming to continue when app is in background
    }
}
