package com.openbaby.monitor.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

@Singleton
class MdnsPublisher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MdnsPublisher"
        private const val SERVICE_TYPE = "_vigil._tcp.local."
    }

    private var jmdns: JmDNS? = null
    private var wifiLock: WifiManager.MulticastLock? = null
    private var serviceInfo: ServiceInfo? = null

    suspend fun startPublishing(port: Int) = withContext(Dispatchers.IO) {
        try {
            // Acquire multicast lock
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createMulticastLock("vigil_mdns").apply {
                setReferenceCounted(true)
                acquire()
            }

            // Get device IP address
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            val ipBytes = byteArrayOf(
                (ipInt and 0xff).toByte(),
                (ipInt shr 8 and 0xff).toByte(),
                (ipInt shr 16 and 0xff).toByte(),
                (ipInt shr 24 and 0xff).toByte()
            )
            val address = InetAddress.getByAddress(ipBytes)

            // Create JmDNS instance
            jmdns = JmDNS.create(address, "Vigil")

            // Create service info
            val deviceName = "Vigil-${Build.MODEL.replace(" ", "-")}"
            serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                deviceName,
                port,
                0,
                0,
                mapOf(
                    "path" to "/live",
                    "model" to Build.MODEL,
                    "version" to "0.1.0"
                )
            )

            // Register service
            jmdns?.registerService(serviceInfo)
            Log.d(TAG, "mDNS service published: $deviceName on port $port")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mDNS publishing", e)
        }
    }

    suspend fun stopPublishing() = withContext(Dispatchers.IO) {
        try {
            serviceInfo?.let { jmdns?.unregisterService(it) }
            jmdns?.close()
            jmdns = null
            wifiLock?.release()
            wifiLock = null
            Log.d(TAG, "mDNS service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mDNS publishing", e)
        }
    }
}
