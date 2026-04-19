package com.movierecommender.app.ui.screens.firestick

import android.app.SearchManager
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
import androidx.compose.material.icons.filled.Edit
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
import com.movierecommender.app.data.remote.ProviderContentResolverRegistry
import com.movierecommender.app.data.remote.StreamingAppRegistry
import kotlinx.coroutines.launch

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
    onImportProviderLink: (suspend (option: WatchOption, rawContentIdOrUrl: String) -> List<WatchOption>?)? = null,
    onDismiss: () -> Unit,
    onWatchTrailer: (() -> Unit)? = null,
    onTorrentSelected: (magnetUrl: String) -> Unit,
    onBrowseEpisodes: (() -> Unit)? = null // For TV shows — open episode picker with torrent
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val firstOptionFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var displayedOptions by remember { mutableStateOf(options) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInput by remember { mutableStateOf("") }
    var selectedImportProviderId by remember { mutableStateOf<Int?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(options) {
        displayedOptions = options
    }

    val importableOptions = remember(displayedOptions) {
        displayedOptions.filter {
            it.type != WatchOptionType.TORRENT &&
                it.providerId != null &&
                ProviderContentResolverRegistry.getResolver(it.providerId) != null
        }.sortedBy { it.displayPriority }
    }

    LaunchedEffect(showImportDialog, importableOptions) {
        if (showImportDialog && importableOptions.isNotEmpty()) {
            if (selectedImportProviderId == null || importableOptions.none { it.providerId == selectedImportProviderId }) {
                selectedImportProviderId = importableOptions.first().providerId
            }
        }
    }

    // Auto-focus first option when loaded (also re-trigger when trailer arrives)
    LaunchedEffect(displayedOptions, isLoading, onWatchTrailer) {
        if (!isLoading && displayedOptions.isNotEmpty()) {
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
                    if (onImportProviderLink != null && importableOptions.isNotEmpty()) {
                        val importInteraction = remember { MutableInteractionSource() }
                        val importFocused by importInteraction.collectIsFocusedAsState()
                        OutlinedButton(
                            onClick = {
                                importInput = ""
                                selectedImportProviderId = importableOptions.firstOrNull()?.providerId
                                showImportDialog = true
                            },
                            interactionSource = importInteraction,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .focusable(interactionSource = importInteraction),
                            border = BorderStroke(
                                width = if (importFocused) 2.dp else 1.dp,
                                color = if (importFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exact Link")
                        }
                    }
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
                } else if (displayedOptions.isEmpty()) {
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
                        val grouped = displayedOptions.groupBy { it.type }
                        val typeOrder = listOf(
                            WatchOptionType.FREE,
                            WatchOptionType.SUBSCRIPTION,
                            WatchOptionType.ADS,
                            WatchOptionType.RENT,
                            WatchOptionType.BUY,
                            WatchOptionType.TORRENT
                        )

                        var isFirstOption = true

                        // Watch Trailer as the first option
                        if (onWatchTrailer != null) {
                            item(key = "watch_trailer") {
                                Text(
                                    text = "TRAILER",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            item(key = "watch_trailer_row") {
                                val focusReq = if (isFirstOption) {
                                    isFirstOption = false
                                    firstOptionFocusRequester
                                } else {
                                    remember { FocusRequester() }
                                }
                                TrailerOptionRow(
                                    focusRequester = focusReq,
                                    onClick = onWatchTrailer
                                )
                            }
                        }

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
                                            launchStreamingApp(context, option, title)
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

    if (showImportDialog && onImportProviderLink != null) {
        val selectedOption = importableOptions.firstOrNull { it.providerId == selectedImportProviderId }
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) showImportDialog = false
            },
            title = { Text("Import Exact Provider Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose a supported provider and paste its exact content URL or provider ID.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        importableOptions.forEach { option ->
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            OutlinedButton(
                                onClick = { selectedImportProviderId = option.providerId },
                                interactionSource = interaction,
                                border = BorderStroke(
                                    width = if (selectedImportProviderId == option.providerId || focused) 2.dp else 1.dp,
                                    color = if (selectedImportProviderId == option.providerId || focused) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(option.name)
                            }
                        }
                    }
                    selectedOption?.let { option ->
                        Text(
                            text = providerImportHint(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it },
                        label = { Text("Provider URL or ID") },
                        singleLine = true,
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting && selectedOption != null && importInput.isNotBlank(),
                    onClick = {
                        val option = selectedOption ?: return@TextButton
                        coroutineScope.launch {
                            isImporting = true
                            val updatedOptions = try {
                                onImportProviderLink(option, importInput.trim())
                            } catch (e: Exception) {
                                android.util.Log.e("WatchOptions", "Failed to import provider link", e)
                                null
                            }
                            if (updatedOptions != null) {
                                displayedOptions = updatedOptions
                                showImportDialog = false
                                importInput = ""
                                Toast.makeText(context, "Saved exact link for ${option.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not parse or save that provider link", Toast.LENGTH_LONG).show()
                            }
                            isImporting = false
                        }
                    }
                ) {
                    Text(if (isImporting) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { showImportDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun providerImportHint(option: WatchOption): String {
    return when (ProviderContentResolverRegistry.getResolver(option.providerId ?: -1)?.providerKey) {
        "netflix" -> "Netflix: paste a title URL like https://www.netflix.com/title/80057281 or just the numeric title ID."
        "prime_video" -> "Prime Video: paste a detail URL like https://www.amazon.com/gp/video/detail/B08... or the 10-character ASIN."
        "hulu" -> "Hulu: paste the exact hulu.com movie/series URL or the path portion after hulu.com/."
        "youtube" -> "YouTube: paste a watch URL like https://www.youtube.com/watch?v=... or the 11-character video ID."
        else -> "Paste the provider's exact content URL or provider-native content ID."
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
 * Row for "Watch Trailer" option — always shown first if available.
 */
@Composable
private fun TrailerOptionRow(
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
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Trailer",
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Watch Trailer",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
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
 * On Fire TV, tries deep link (ACTION_VIEW) first to open search results,
 * then falls back to launching the app's main activity.
 *
 * Fallback order: deep link → getLaunchIntent → Leanback launcher → "not installed" toast.
 */
private fun launchStreamingApp(context: Context, option: WatchOption, movieTitle: String) {
    val TAG = "WatchOptions"
    android.util.Log.w(TAG, "=== Launch: ${option.name} ===")
    android.util.Log.w(TAG, "  providerId=${option.providerId}")
    android.util.Log.w(TAG, "  hasExactProviderLink=${option.hasExactProviderLink}")
    android.util.Log.w(TAG, "  packageName=${option.packageName}")
    android.util.Log.w(TAG, "  deepLinkUrl=${option.deepLinkUrl}")
    android.util.Log.w(TAG, "  movieTitle=$movieTitle")

    val pkg = option.packageName
    val providerId = option.providerId
    if (pkg == null) {
        Toast.makeText(context, "${option.name} is not available on this device.", Toast.LENGTH_SHORT).show()
        return
    }

    // 1. Try ACTION_SEARCH only if a provider is explicitly verified to support it.
    if (providerId != null && StreamingAppRegistry.supportsInAppSearch(providerId)) {
        try {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, movieTitle)
            }
            android.util.Log.w(TAG, "  Step 1: ACTION_SEARCH for $pkg")
            context.startActivity(searchIntent)
            return
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w(TAG, "  Step 1 FAILED (ActivityNotFound): ACTION_SEARCH for $pkg")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "  Step 1 FAILED: ACTION_SEARCH for $pkg", e)
        }
    }

    // 2A. Try native custom-scheme deep link (bypasses Silk entirely on Fire TV).
    if (providerId != null) {
        StreamingAppRegistry.buildNativeDeepLink(providerId, movieTitle)?.let { nativeLink ->
            try {
                val nativeUri = Uri.parse(nativeLink)
                val nativeIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
                    setPackage(pkg)
                }
                android.util.Log.w(TAG, "  Step 2A: native deep link → $nativeLink")
                context.startActivity(nativeIntent)
                Toast.makeText(context, "Opening ${option.name}…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: ActivityNotFoundException) {
                android.util.Log.w(TAG, "  Step 2A FAILED (ActivityNotFound): $nativeLink")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "  Step 2A FAILED: $nativeLink", e)
            }
        }
    }

    // 2B. Try deep link URL scoped to the target app package via setPackage().
    // This prevents Fire TV from routing HTTPS URLs to Silk browser.
    option.deepLinkUrl?.let { deepLink ->
        try {
            val uri = Uri.parse(deepLink)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
            }
            val resolved = context.packageManager.resolveActivity(intent, 0)
            android.util.Log.w(
                TAG,
                "  Step 2B: ACTION_VIEW + setPackage($pkg) → resolved=${resolved?.activityInfo?.name ?: "null"} for $deepLink"
            )
            if (resolved != null) {
                context.startActivity(intent)
                Toast.makeText(context, "Opening ${option.name}…", Toast.LENGTH_SHORT).show()
                return
            }
            Unit
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w(TAG, "  Step 2B FAILED (ActivityNotFound): $deepLink")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "  Step 2B FAILED: $deepLink", e)
        }
    }

    // 3. Try standard getLaunchIntentForPackage (MAIN + LAUNCHER)
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        android.util.Log.w(TAG, "  Step 3: getLaunchIntent for $pkg → ${launchIntent != null}")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            Toast.makeText(context, fallbackLaunchMessage(option, movieTitle), Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        android.util.Log.w(TAG, "  Step 3 FAILED for $pkg", e)
    }

    // 4. Fire TV fallback: query for MAIN + LEANBACK_LAUNCHER
    try {
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(pkg)
        }
        val resolveInfo = context.packageManager.resolveActivity(leanbackIntent, 0)
        android.util.Log.w(TAG, "  Step 4: LEANBACK_LAUNCHER for $pkg → resolveInfo=${resolveInfo?.activityInfo?.name}")
        if (resolveInfo != null) {
            leanbackIntent.apply {
                setClassName(pkg, resolveInfo.activityInfo.name)
            }
            context.startActivity(leanbackIntent)
            Toast.makeText(context, fallbackLaunchMessage(option, movieTitle), Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        android.util.Log.w(TAG, "  Step 4 FAILED for $pkg", e)
    }

    // 5. queryIntentActivities with MATCH_ALL — catches apps invisible to resolveActivity
    try {
        val queryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(pkg)
        }
        @Suppress("DEPRECATION")
        val activities = context.packageManager.queryIntentActivities(queryIntent, android.content.pm.PackageManager.MATCH_ALL)
        android.util.Log.w(TAG, "  Step 5: queryIntentActivities MATCH_ALL for $pkg → ${activities.size} results")
        if (activities.isNotEmpty()) {
            val actInfo = activities[0].activityInfo
            android.util.Log.w(TAG, "  Step 5: launching ${actInfo.packageName}/${actInfo.name}")
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName(actInfo.packageName, actInfo.name)
            }
            context.startActivity(intent)
            Toast.makeText(context, fallbackLaunchMessage(option, movieTitle), Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        android.util.Log.w(TAG, "  Step 5 FAILED for $pkg", e)
    }

    // 6. Last resort — check if package exists at all via getPackageInfo
    try {
        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(pkg, 0)
        android.util.Log.w(TAG, "  Step 6: Package $pkg EXISTS (version=${pkgInfo.versionName})")
        // Package is installed but none of the above launch methods worked.
        // Try plain MAIN intent without any category.
        val plainIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(pkg)
        }
        @Suppress("DEPRECATION")
        val plainActivities = context.packageManager.queryIntentActivities(plainIntent, android.content.pm.PackageManager.MATCH_ALL)
        android.util.Log.w(TAG, "  Step 6: plain MAIN query → ${plainActivities.size} results")
        if (plainActivities.isNotEmpty()) {
            val actInfo = plainActivities[0].activityInfo
            android.util.Log.w(TAG, "  Step 6: launching ${actInfo.packageName}/${actInfo.name}")
            plainIntent.setClassName(actInfo.packageName, actInfo.name)
            context.startActivity(plainIntent)
            Toast.makeText(context, fallbackLaunchMessage(option, movieTitle), Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        android.util.Log.w(TAG, "  Step 6: Package $pkg NOT FOUND on device")
    } catch (e: Exception) {
        android.util.Log.w(TAG, "  Step 6 FAILED for $pkg", e)
    }

    // 7. Browser fallback for providers where we have a search URL. This is last
    // on Fire TV because it often opens Silk instead of the target app.
    if (providerId != null && StreamingAppRegistry.shouldUseBrowserFallback(providerId)) {
        option.deepLinkUrl?.let { deepLink ->
            try {
                val uri = Uri.parse(deepLink)
                if (uri.scheme == "https" || uri.scheme == "http") {
                    android.util.Log.w(TAG, "  Step 7: browser fallback → $deepLink")
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    }
                    context.startActivity(browserIntent)
                    Toast.makeText(context, "Opening ${option.name} in Silk as a fallback.", Toast.LENGTH_LONG).show()
                    return
                }
                Unit
            } catch (e: Exception) {
                android.util.Log.w(TAG, "  Step 7 FAILED for browser fallback", e)
            }
        }
    }

    // 8. App is not installed or not launchable
    android.util.Log.w(TAG, "  Step 8: All launch methods exhausted for $pkg")
    Toast.makeText(context, "${option.name} is not installed.\nInstall it from the Fire TV app store.", Toast.LENGTH_LONG).show()
}

private fun fallbackLaunchMessage(option: WatchOption, movieTitle: String): String {
    return if (option.hasExactProviderLink) {
        "Opening ${option.name}…\nExact title launch may depend on the app."
    } else {
        "Opening ${option.name}…\nSearch for \"$movieTitle\" manually in the app."
    }
}
