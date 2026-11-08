package dev.sriramcodessw.versesync.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log2

@Composable
fun PitchVisualizer(
    pitch: Float,
    modifier: Modifier = Modifier,
) {
    val pitchHistory = remember { mutableStateListOf<Float>() }
    val maxHistorySize = 50

    // Update history when pitch changes
    LaunchedEffect(pitch) {
        pitchHistory.add(pitch)
        if (pitchHistory.size > maxHistorySize) {
            pitchHistory.removeAt(0)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Stability indicator / Needle
            StabilityNeedle(pitch = pitch)

            Spacer(modifier = Modifier.height(8.dp))

            // Scrolling Waveform
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val width = size.width
                val height = size.height
                val stepX = width / maxHistorySize

                val path = Path()
                var started = false

                pitchHistory.forEachIndexed { index, p ->
                    if (p > 0) {
                        // Map frequency to Y coordinate (logarithmic scale)
                        // Range approx 80Hz to 1000Hz for human voice
                        val normalizedPitch = (log2(p / 80f) / log2(1000f / 80f)).coerceIn(0f, 1f)
                        val x = index * stepX
                        val y = height - (normalizedPitch * height)

                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF4CAF50), // Vibrant Green
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        // Note name overlay if pitch detected
        if (pitch > 0) {
            Text(
                text = "${pitch.toInt()} Hz",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }
}

@Composable
fun StabilityNeedle(pitch: Float) {
    // A simple visual to show if the user is holding a note
    val animatedAlpha = remember { Animatable(0f) }

    LaunchedEffect(pitch > 0) {
        if (pitch > 0) {
            animatedAlpha.animateTo(1f, animationSpec = tween(200))
        } else {
            animatedAlpha.animateTo(0.3f, animationSpec = tween(500))
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (pitch > 0) "Vocal Signal Detected" else "Waiting for Voice...",
            style = MaterialTheme.typography.labelSmall,
            color = if (pitch > 0) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.padding(end = 8.dp),
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (pitch > 0) Color(0xFF4CAF50) else Color.Gray),
        )
    }
}
