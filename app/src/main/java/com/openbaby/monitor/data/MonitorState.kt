package com.openbaby.monitor.data

data class MonitorState(
    val isStreaming: Boolean = false,
    val streamUrl: String = "",
    val connectedViewers: Int = 0,
    val audioLevel: Float = 0f,
    val cryDetected: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val errorMessage: String? = null
)

enum class StreamQuality(val width: Int, val height: Int, val bitrate: Int, val displayName: String) {
    LOW(640, 480, 1_000_000, "480p"),
    MEDIUM(1280, 720, 2_000_000, "720p"),
    HIGH(1920, 1080, 4_000_000, "1080p")
}

data class StreamSettings(
    val quality: StreamQuality = StreamQuality.MEDIUM,
    val framerate: Int = 30,
    val audioBitrate: Int = 128_000,
    val port: Int = 8554
)
