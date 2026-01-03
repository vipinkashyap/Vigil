package com.openbaby.monitor.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openbaby.monitor.MainActivity
import com.openbaby.monitor.MonitorApp
import com.openbaby.monitor.R
import com.openbaby.monitor.data.SettingsRepository
import com.openbaby.monitor.streaming.RtspStreamManager
import com.openbaby.monitor.streaming.StreamConfig
import com.pedro.library.view.OpenGlView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_START = "com.openbaby.monitor.action.START"
        const val ACTION_STOP = "com.openbaby.monitor.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, MonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var rtspStreamManager: RtspStreamManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private var wakeLock: PowerManager.WakeLock? = null
    private var openGlView: OpenGlView? = null
    private var isStreaming = false

    inner class LocalBinder : Binder() {
        fun getService(): MonitorService = this@MonitorService

        fun attachPreview(view: OpenGlView) {
            openGlView = view
            if (isStreaming) {
                startStreamingWithView(view)
            }
        }

        fun detachPreview() {
            openGlView = null
        }

        fun isCurrentlyStreaming(): Boolean = isStreaming
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isStreaming) {
            Log.d(TAG, "Already streaming")
            return
        }

        Log.d(TAG, "Starting monitoring service")
        isStreaming = true

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MonitorApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(MonitorApp.NOTIFICATION_ID, notification)
        }

        // Start streaming if we have a view attached, otherwise wait for view
        openGlView?.let { startStreamingWithView(it) }
    }

    private fun startStreamingWithView(view: OpenGlView) {
        serviceScope.launch {
            try {
                val settings = settingsRepository.streamSettings.first()
                val config = StreamConfig.fromQuality(settings.quality, settings.framerate).copy(
                    port = settings.port,
                    audioBitrate = settings.audioBitrate
                )

                if (rtspStreamManager.initialize(view, config)) {
                    if (rtspStreamManager.prepareStream(config)) {
                        rtspStreamManager.startStream()
                        updateNotification()
                        Log.d(TAG, "Streaming started successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
            }
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring service")
        isStreaming = false
        rtspStreamManager.stopStream()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val ipAddress = getDeviceIpAddress()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MonitorApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.monitoring_active))
            .setContentText("rtsp://$ipAddress:8554/")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_monitoring),
                stopIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(MonitorApp.NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vigil::MonitorWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun getDeviceIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                networkInterface.inetAddresses?.toList()?.forEach { address ->
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address", e)
        }
        return "0.0.0.0"
    }

    override fun onDestroy() {
        super.onDestroy()
        isStreaming = false
        rtspStreamManager.release()
        releaseWakeLock()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
