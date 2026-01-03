package com.openbaby.monitor.streaming

import com.openbaby.monitor.data.StreamQuality

data class StreamConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val videoBitrate: Int = 2_000_000,
    val audioBitrate: Int = 128_000,
    val audioSampleRate: Int = 44100,
    val stereo: Boolean = true,
    val port: Int = 8554
) {
    companion object {
        fun fromQuality(quality: StreamQuality, fps: Int = 30): StreamConfig {
            return StreamConfig(
                width = quality.width,
                height = quality.height,
                fps = fps,
                videoBitrate = quality.bitrate
            )
        }
    }
}
