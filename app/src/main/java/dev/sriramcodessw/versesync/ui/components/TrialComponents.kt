package dev.sriramcodessw.versesync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProFeatureTrialDialog(
    featureName: String,
    remainingTrials: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("$featureName (Pro Feature)") },
        text = {
            Column {
                Text("This is a Pro feature. You have $remainingTrials trial(s) left.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fallback: After trials, this feature will be restricted to the standard version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Use Trial")
            }
        },
        dismissButton = {
            TextButton(onClick = onUpgrade) {
                Text("Upgrade Now")
            }
        },
    )
}

@Composable
fun TrialBadge(remaining: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = "Trial: $remaining",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
