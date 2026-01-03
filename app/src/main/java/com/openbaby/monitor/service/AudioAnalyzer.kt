package com.openbaby.monitor.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.openbaby.monitor.ml.CryDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Analyzes audio for cry detection and audio level visualization.
 * Uses a separate AudioRecord stream for analysis (independent of RTSP streaming).
 */
@Singleton
class AudioAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryDetector: CryDetector
) {
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 16000 // Required by YAMNet
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var analyzerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Expose cry detection state from CryDetector
    val cryDetected: StateFlow<Boolean> = cryDetector.cryDetected
    val cryConfidence: StateFlow<Float> = cryDetector.confidence

    fun startAnalyzing(): Boolean {
        if (_isAnalyzing.value) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio permission not granted")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return false
            }

            audioRecord?.startRecording()
            _isAnalyzing.value = true

            // Initialize cry detector
            cryDetector.initialize()

            // Start analysis loop
            analyzerJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)

                while (isActive && _isAnalyzing.value) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (readResult > 0) {
                        // Calculate audio level (RMS)
                        val level = calculateRmsLevel(buffer, readResult)
                        _audioLevel.value = level

                        // Run cry detection
                        cryDetector.processAudio(buffer.copyOf(readResult))
                    }
                }
            }

            Log.d(TAG, "Audio analysis started")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio analysis", e)
            return false
        }
    }

    fun stopAnalyzing() {
        _isAnalyzing.value = false
        analyzerJob?.cancel()
        analyzerJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        cryDetector.release()
        _audioLevel.value = 0f

        Log.d(TAG, "Audio analysis stopped")
    }

    private fun calculateRmsLevel(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / size)

        // Convert to 0-1 range (normalized by max short value)
        return (rms / Short.MAX_VALUE).coerceIn(0.0, 1.0).toFloat()
    }
}
