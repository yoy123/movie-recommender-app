package com.movierecommender.app.ui.dialogs.firestick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movierecommender.app.ui.leanback.LeanbackPanel
import com.movierecommender.app.ui.leanback.LeanbackTextButton

/**
 * One-time privacy consent for sending recommendation inputs to OpenAI.
 * AI is the default recommendation engine; declining data sharing uses TMDB fallback.
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
                text = "Allow AI Data Sharing?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "OpenStream+ uses AI by default to analyze your selections and rerank verified TMDB titles.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Data sent to OpenAI:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "- Selected movie or TV-show titles\n" +
                        "- Selected genre\n" +
                        "- Enabled recommendation preferences\n" +
                        "- A bounded list of candidate titles from TMDB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your name, favorites list, playback history, and account information are not sent. Declining uses the TMDB fallback and displays a diagnostic notice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                LeanbackTextButton(
                    label = "Don't Share",
                    onClick = onDecline,
                    modifier = Modifier.padding(end = 12.dp)
                )
                LeanbackTextButton(
                    label = "Allow AI",
                    onClick = onAccept,
                    emphasized = true
                )
            }
        }
    }
}
