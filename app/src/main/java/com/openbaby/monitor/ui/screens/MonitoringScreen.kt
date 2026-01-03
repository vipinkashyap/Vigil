package com.openbaby.monitor.ui.screens

import android.content.Context
import android.net.wifi.WifiManager
import com.pedro.library.view.OpenGlView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openbaby.monitor.ui.components.AudioMeter
import com.openbaby.monitor.ui.components.CameraPreview
import com.openbaby.monitor.ui.components.MonitorStatus
import com.openbaby.monitor.ui.components.StatusIndicator
import com.openbaby.monitor.ui.theme.StatusAlert
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun MonitoringScreen(
    onStopMonitoring: () -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isStreaming by viewModel.isStreaming.collectAsState()
    val connectedClients by viewModel.connectedClients.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val cryDetected by viewModel.cryDetected.collectAsState()
    val cryConfidence by viewModel.cryConfidence.collectAsState()
    var flashEnabled by remember { mutableStateOf(false) }

    val ipAddress = remember { getDeviceIpAddress(context) }

    // Don't stop streaming on dispose - let the service handle lifecycle
    // User must explicitly tap Stop button to end streaming

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onSurfaceReady = { openGlView ->
                viewModel.startStreaming(openGlView)
            },
            onSurfaceDestroyed = {
                // Switch to background mode (context-based) when surface is destroyed
                // This allows streaming to continue without the preview
                viewModel.onSurfaceDestroyed()
            }
        )

        // Top overlay - Status and stream info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(
                    status = if (isStreaming) MonitorStatus.STREAMING else MonitorStatus.INACTIVE,
                    connectedViewers = connectedClients
                )

                AudioMeter(
                    level = audioLevel,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stream URL
            Text(
                text = "rtsp://$ipAddress:8554/live",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAlert,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Cry detection alert overlay
        if (cryDetected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        StatusAlert.copy(alpha = 0.9f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 3.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ChildCare,
                        contentDescription = "Baby crying",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Baby Crying!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${(cryConfidence * 100).toInt()}% confidence",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch camera button
            IconButton(
                onClick = { viewModel.switchCamera() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White
                )
            }

            // Stop button
            FloatingActionButton(
                onClick = {
                    viewModel.stopStreaming()
                    onStopMonitoring()
                },
                containerColor = StatusAlert,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop monitoring",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            // Flash button
            IconButton(
                onClick = {
                    flashEnabled = viewModel.toggleFlash()
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = "Toggle flash",
                    tint = Color.White
                )
            }
        }
    }
}

private fun getDeviceIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
        // Ignore
    }
    return "0.0.0.0"
}
