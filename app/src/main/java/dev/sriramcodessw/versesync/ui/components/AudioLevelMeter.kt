package dev.sriramcodessw.versesync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioLevelMeter(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    // Smooth the level transitions
    val smoothedLevel by animateFloatAsState(targetValue = amplitude.coerceIn(0f, 1f))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(smoothedLevel)
                .fillMaxHeight()
                .background(
                    when {
                        smoothedLevel > 0.8f -> Color.Red
                        smoothedLevel > 0.5f -> Color.Yellow
                        else -> Color.Green
                    }
                )
        )
    }
}
