package com.openbaby.monitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openbaby.monitor.ui.theme.StatusActive
import com.openbaby.monitor.ui.theme.StatusAlert
import com.openbaby.monitor.ui.theme.StatusInactive

enum class MonitorStatus {
    INACTIVE,
    STREAMING,
    ALERT
}

@Composable
fun StatusIndicator(
    status: MonitorStatus,
    connectedViewers: Int,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            MonitorStatus.INACTIVE -> StatusInactive
            MonitorStatus.STREAMING -> StatusActive
            MonitorStatus.ALERT -> StatusAlert
        },
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(if (status == MonitorStatus.STREAMING) pulseAlpha else 1f)
                .background(statusColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = when (status) {
                MonitorStatus.INACTIVE -> "Inactive"
                MonitorStatus.STREAMING -> if (connectedViewers > 0) {
                    "$connectedViewers viewer${if (connectedViewers > 1) "s" else ""}"
                } else {
                    "Streaming"
                }
                MonitorStatus.ALERT -> "Alert!"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}
