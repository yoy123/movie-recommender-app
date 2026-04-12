package com.movierecommender.app.ui.dialogs.firestick

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movierecommender.app.ui.leanback.LeanbackPanel
import com.movierecommender.app.ui.leanback.LeanbackTextButton

/**
 * GDPR/CCPA compliant consent dialog for LLM-based recommendations.
 * Fire TV variant with D-pad friendly layout.
 * 
 * Displays when users first attempt to generate recommendations.
 * Explains that movie selection data will be sent to OpenAI for personalized recommendations.
 * Users can accept for AI recommendations or decline for TMDB-only fallback.
 */
@Composable
fun LlmConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        LeanbackPanel(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .wrapContentHeight()
        ) {
            Text(
                text = "AI-Powered Recommendations",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This app can use AI to provide personalized movie recommendations based on your selections.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "What data is shared:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = "- Titles of movies you select (1-5 movies)\n" +
                           "- Your genre preference\n" +
                           "- Your recommendation preferences (indie/mainstream, tone, etc.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This data is sent to OpenAI's servers for processing. No personal identifying information is collected or stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "If you decline, you'll still get recommendations using our standard algorithm (TMDB-based), just without AI personalization.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Use D-pad to select your choice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LeanbackTextButton(
                    label = "Use Standard",
                    onClick = onDecline,
                    modifier = Modifier.padding(end = 12.dp)
                )
                LeanbackTextButton(
                    label = "Accept AI",
                    onClick = onAccept,
                    emphasized = true
                )
            }
        }
    }
}
