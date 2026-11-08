package dev.sriramcodessw.versesync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class TourStep(
    val title: String,
    val description: String,
    val anchor: Offset = Offset.Zero,
    val size: IntSize = IntSize.Zero,
)

@Composable
fun TourOverlay(
    steps: List<TourStep>,
    currentStepIndex: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    if (currentStepIndex >= steps.size) return

    val step = steps[currentStepIndex]

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(enabled = true, onClick = {}), // Intercept clicks
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spotlightPath =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect =
                                Rect(
                                    offset = step.anchor,
                                    size = Size(step.size.width.toFloat(), step.size.height.toFloat()),
                                ),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        ),
                    )
                }

            // Draw overlay with a hole
            clipPath(spotlightPath, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.7f))
            }
        }

        // Tooltip logic
        Box(
            modifier =
                Modifier
                    .padding(16.dp)
                    .align(Alignment.Center),
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(step.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSkip) { Text("Skip Tour") }
                        Button(onClick = onNext) {
                            Text(if (currentStepIndex == steps.size - 1) "Got it!" else "Next")
                        }
                    }
                }
            }
        }
    }
}
