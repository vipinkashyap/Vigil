package com.openbaby.monitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openbaby.monitor.ui.theme.StatusActive
import com.openbaby.monitor.ui.theme.StatusAlert
import com.openbaby.monitor.ui.theme.StatusWarning

@Composable
fun AudioMeter(
    level: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    barCount: Int = 10
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(100),
        label = "audioLevel"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val threshold = (index + 1) / barCount.toFloat()
            val isActive = animatedLevel >= threshold
            val barHeight = 8.dp + (index * 2).dp

            val barColor = when {
                index < barCount * 0.6 -> StatusActive
                index < barCount * 0.8 -> StatusWarning
                else -> StatusAlert
            }

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) barColor else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
    }
}
