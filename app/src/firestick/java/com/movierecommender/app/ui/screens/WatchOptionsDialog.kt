package com.movierecommender.app.ui.screens.firestick

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.WatchOption
import com.movierecommender.app.data.model.WatchOptionType

/**
 * DPAD-friendly dialog presenting watch options for a movie or TV show.
 * Shows streaming service providers (grouped by type) and a torrent option.
 *
 * When user selects a streaming provider → launches the provider's app via Intent.
 * When user selects torrent → calls onTorrentSelected with the magnet URL.
 */
@Composable
fun WatchOptionsDialog(
    title: String,
    options: List<WatchOption>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onTorrentSelected: (magnetUrl: String) -> Unit,
    onBrowseEpisodes: (() -> Unit)? = null // For TV shows — open episode picker with torrent
) {
    val context = LocalContext.current
    val firstOptionFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Auto-focus first option when loaded
    LaunchedEffect(options, isLoading) {
        if (!isLoading && options.isNotEmpty()) {
            try { firstOptionFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Watch Options",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val closeInteraction = remember { MutableInteractionSource() }
                    val closeFocused by closeInteraction.collectIsFocusedAsState()
                    IconButton(
                        onClick = onDismiss,
                        interactionSource = closeInteraction,
                        modifier = Modifier.focusable(interactionSource = closeInteraction)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = if (closeFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Finding watch options...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (options.isEmpty()) {
                    // No options found
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No watch options found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // Options list grouped by type
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Group options by type for section headers
                        val grouped = options.groupBy { it.type }
                        val typeOrder = listOf(
                            WatchOptionType.FREE,
                            WatchOptionType.SUBSCRIPTION,
                            WatchOptionType.ADS,
                            WatchOptionType.RENT,
                            WatchOptionType.BUY,
                            WatchOptionType.TORRENT
                        )

                        var isFirstOption = true

                        typeOrder.forEach { type ->
                            val groupOptions = grouped[type] ?: return@forEach

                            // Section header
                            item(key = "header_$type") {
                                Text(
                                    text = when (type) {
                                        WatchOptionType.FREE -> "FREE"
                                        WatchOptionType.SUBSCRIPTION -> "STREAMING"
                                        WatchOptionType.ADS -> "FREE WITH ADS"
                                        WatchOptionType.RENT -> "RENT"
                                        WatchOptionType.BUY -> "BUY"
                                        WatchOptionType.TORRENT -> "TORRENT"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(
                                items = groupOptions,
                                key = { "${it.type}_${it.name}_${it.provider}" }
                            ) { option ->
                                val focusReq = if (isFirstOption) {
                                    isFirstOption = false
                                    firstOptionFocusRequester
                                } else {
                                    remember { FocusRequester() }
                                }

                                WatchOptionRow(
                                    option = option,
                                    focusRequester = focusReq,
                                    onClick = {
                                        if (option.type == WatchOptionType.TORRENT) {
                                            option.magnetUrl?.let { onTorrentSelected(it) }
                                        } else {
                                            launchStreamingApp(context, option)
                                        }
                                    }
                                )
                            }
                        }

                        // "Browse Episodes (Torrent)" option for TV shows
                        if (onBrowseEpisodes != null) {
                            item(key = "browse_episodes_torrent") {
                                Text(
                                    text = "TORRENT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                val focusReq = if (isFirstOption) {
                                    isFirstOption = false
                                    firstOptionFocusRequester
                                } else {
                                    remember { FocusRequester() }
                                }
                                BrowseEpisodesTorrentRow(
                                    focusRequester = focusReq,
                                    onClick = onBrowseEpisodes
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single row in the watch options list — shows provider icon, name, type badge.
 * DPAD-focusable with visual focus indicators.
 */
@Composable
private fun WatchOptionRow(
    option: WatchOption,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interaction),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isFocused) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider icon
            if (option.type == WatchOptionType.TORRENT) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = "Torrent",
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(36.dp)
                )
            } else if (option.logoPath != null) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w92${option.logoPath}",
                    contentDescription = option.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = option.name,
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Provider name and details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Torrent extra info
                if (option.type == WatchOptionType.TORRENT) {
                    val details = buildString {
                        option.provider?.let { append(it) }
                        option.seeds?.let {
                            if (isNotEmpty()) append(" • ")
                            append("$it seeds")
                        }
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFocused)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Type badge
            val badgeColor = when (option.type) {
                WatchOptionType.FREE -> Color(0xFF4CAF50)     // Green
                WatchOptionType.SUBSCRIPTION -> Color(0xFF2196F3) // Blue
                WatchOptionType.ADS -> Color(0xFF8BC34A)      // Light green
                WatchOptionType.RENT -> Color(0xFFFF9800)     // Orange
                WatchOptionType.BUY -> Color(0xFFE91E63)      // Pink
                WatchOptionType.TORRENT -> Color(0xFF9C27B0)  // Purple
            }
            Surface(
                color = badgeColor.copy(alpha = if (isFocused) 1f else 0.85f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (option.type) {
                        WatchOptionType.FREE -> "FREE"
                        WatchOptionType.SUBSCRIPTION -> "STREAM"
                        WatchOptionType.ADS -> "FREE"
                        WatchOptionType.RENT -> "RENT"
                        WatchOptionType.BUY -> "BUY"
                        WatchOptionType.TORRENT -> "TORRENT"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Play icon
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Special row for "Browse Episodes (Torrent)" in TV show watch options.
 */
@Composable
private fun BrowseEpisodesTorrentRow(
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interaction),
        color = if (isFocused) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isFocused) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = "Torrent",
                tint = if (isFocused) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Browse Episodes (Torrent)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFocused) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Pick season and episode to stream via torrent",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused)
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Surface(
                color = Color(0xFF9C27B0).copy(alpha = if (isFocused) 1f else 0.85f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "TORRENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = if (isFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Launch a streaming app via Intent for the given watch option.
 * On Fire TV, prioritizes native app launch over web URLs that would open in Silk.
 * Order: custom-scheme deep link → app launch → web deep link → JustWatch → toast.
 */
private fun launchStreamingApp(context: Context, option: WatchOption) {
    val deepLink = option.deepLinkUrl
    val isCustomScheme = deepLink != null &&
        !deepLink.startsWith("http://", ignoreCase = true) &&
        !deepLink.startsWith("https://", ignoreCase = true)

    // 1. Try custom-scheme deep link first (nflx://, hulu://, peacock://, etc.)
    //    These open the actual app's search directly.
    if (isCustomScheme && deepLink != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                option.packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w("WatchOptions", "Custom deep link failed for ${option.name}: $deepLink", e)
        } catch (e: Exception) {
            android.util.Log.w("WatchOptions", "Custom deep link error for ${option.name}", e)
        }
    }

    // 2. Try launching the app directly — this opens the actual Fire TV app
    //    instead of falling through to Silk browser with https:// URLs.
    option.packageName?.let { pkg ->
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "Opening ${option.name}…\nSearch for the title there.", Toast.LENGTH_LONG).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("WatchOptions", "App launch failed for ${option.name} ($pkg)", e)
        }
        Unit
    }

    // 3. Try web deep link with package targeting (last resort before JustWatch)
    if (!isCustomScheme && deepLink != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                option.packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w("WatchOptions", "Web deep link failed for ${option.name}: $deepLink", e)
        } catch (e: Exception) {
            android.util.Log.w("WatchOptions", "Web deep link error for ${option.name}", e)
        }
    }

    // 4. Try JustWatch link in browser
    option.justWatchLink?.let { justWatchUrl ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(justWatchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            android.util.Log.w("WatchOptions", "JustWatch link failed for ${option.name}", e)
        }
    }

    // 5. Fallback
    Toast.makeText(context, "${option.name} app not installed", Toast.LENGTH_SHORT).show()
}
