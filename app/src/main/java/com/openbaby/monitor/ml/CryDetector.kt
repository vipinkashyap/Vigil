package com.openbaby.monitor.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cry detection using TensorFlow Lite with YAMNet model.
 *
 * YAMNet is trained on AudioSet and can classify 521 audio classes,
 * including "Baby crying, infant crying" (class index 20).
 */
@Singleton
class CryDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CryDetector"
        private const val MODEL_FILE = "yamnet.tflite"

        // YAMNet class indices for baby-related sounds
        // Class 20: "Crying, sobbing"
        // Class 23: "Baby cry, infant cry"
        // Class 24: "Whimper"
        private const val CRY_CLASS_INDEX = 23 // "Baby cry, infant cry" - more specific
        private const val CRYING_SOBBING_INDEX = 20 // "Crying, sobbing" - general
        private const val WHIMPER_INDEX = 24 // "Whimper"

        private const val CONFIDENCE_THRESHOLD = 0.15f // Lower threshold for sensitivity
        private const val COOLDOWN_MS = 10_000L // 10 seconds between alerts

        // YAMNet expects 16kHz audio, 0.975 second windows (15600 samples)
        const val SAMPLE_RATE = 16000
        const val WINDOW_SAMPLES = 15600
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var lastAlertTime = 0L

    // Buffer to accumulate audio samples
    private val audioBuffer = FloatArray(WINDOW_SAMPLES)
    private var bufferPosition = 0

    private val _cryDetected = MutableStateFlow(false)
    val cryDetected: StateFlow<Boolean> = _cryDetected.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun initialize(): Boolean {
        if (isInitialized) return true

        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Log.d(TAG, "YAMNet model loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize cry detector", e)
            return false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Process audio buffer and run inference.
     *
     * @param audioBuffer PCM audio samples (16-bit, 16kHz, mono)
     * @return true if baby cry detected with sufficient confidence
     */
    fun processAudio(samples: ShortArray): Boolean {
        if (!isInitialized || interpreter == null) return false

        // Convert shorts to floats and normalize to [-1, 1]
        for (sample in samples) {
            if (bufferPosition < WINDOW_SAMPLES) {
                audioBuffer[bufferPosition] = sample.toFloat() / 32768f
                bufferPosition++
            }
        }

        // Calculate audio level from this chunk
        var sum = 0.0
        for (sample in samples) {
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / samples.size)
        val level = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
        _audioLevel.value = level

        // Only run inference when we have a full window
        if (bufferPosition >= WINDOW_SAMPLES) {
            runInference()
            // Shift buffer by half for overlapping windows
            System.arraycopy(audioBuffer, WINDOW_SAMPLES / 2, audioBuffer, 0, WINDOW_SAMPLES / 2)
            bufferPosition = WINDOW_SAMPLES / 2
        }

        return _cryDetected.value
    }

    private fun runInference() {
        val interpreter = this.interpreter ?: return

        try {
            // Prepare input: [1, 15600] float array
            val inputBuffer = ByteBuffer.allocateDirect(WINDOW_SAMPLES * 4).apply {
                order(ByteOrder.nativeOrder())
                for (sample in audioBuffer) {
                    putFloat(sample)
                }
                rewind()
            }

            // Get output tensor shape from model
            val outputTensor = interpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            Log.v(TAG, "Output shape: ${outputShape.contentToString()}")

            // YAMNet lite model outputs scores directly: [1, 521]
            val outputScores = Array(1) { FloatArray(outputShape[1]) }

            interpreter.run(inputBuffer, outputScores)

            // Check for baby/child crying - use max of multiple relevant classes
            val scores = outputScores[0]
            val babyCryScore = if (CRY_CLASS_INDEX < outputShape[1]) scores[CRY_CLASS_INDEX] else 0f
            val cryingSobbingScore = if (CRYING_SOBBING_INDEX < outputShape[1]) scores[CRYING_SOBBING_INDEX] else 0f
            val whimperScore = if (WHIMPER_INDEX < outputShape[1]) scores[WHIMPER_INDEX] else 0f

            // Use the maximum of all cry-related scores
            val maxCryScore = maxOf(babyCryScore, cryingSobbingScore, whimperScore)

            // Log top 5 classes for debugging
            val topIndices = scores.indices.sortedByDescending { scores[it] }.take(5)
            Log.d(TAG, "Top 5: ${topIndices.map { "$it: ${"%.2f".format(scores[it])}" }}")
            Log.d(TAG, "Cry scores - baby:${"%.2f".format(babyCryScore)} crying:${"%.2f".format(cryingSobbingScore)} whimper:${"%.2f".format(whimperScore)} max:${"%.2f".format(maxCryScore)}")

            if (maxCryScore >= CONFIDENCE_THRESHOLD) {
                onCryDetected(maxCryScore)
            } else if (_cryDetected.value) {
                // Reset if cry not detected anymore
                val now = System.currentTimeMillis()
                if (now - lastAlertTime > COOLDOWN_MS) {
                    resetAlert()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
        }
    }

    /**
     * Called when cry is detected to update state and handle cooldown.
     */
    private fun onCryDetected(confidence: Float) {
        val now = System.currentTimeMillis()

        // Check cooldown
        if (_cryDetected.value && now - lastAlertTime < COOLDOWN_MS) {
            // Already in alert state and in cooldown, just update confidence
            _confidence.value = confidence
            return
        }

        _cryDetected.value = true
        _confidence.value = confidence
        lastAlertTime = now

        Log.d(TAG, "🍼 Baby cry detected! Confidence: ${"%.1f".format(confidence * 100)}%")
    }

    fun resetAlert() {
        _cryDetected.value = false
        _confidence.value = 0f
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        bufferPosition = 0
    }
}
