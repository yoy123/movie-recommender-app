package com.movierecommender.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.movierecommender.app.data.model.WatchOption
import com.movierecommender.app.data.model.WatchOptionType

/**
 * Touch-friendly dialog presenting watch options for a movie or TV show (mobile flavor).
 */
@Composable
fun WatchOptionsDialog(
    title: String,
    options: List<WatchOption>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onTorrentSelected: (magnetUrl: String) -> Unit,
    onBrowseEpisodes: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Watch Options",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Finding watch options...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                } else if (options.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No watch options found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val grouped = options.groupBy { it.type }
                        val typeOrder = listOf(
                            WatchOptionType.FREE, WatchOptionType.SUBSCRIPTION, WatchOptionType.ADS,
                            WatchOptionType.RENT, WatchOptionType.BUY, WatchOptionType.TORRENT
                        )

                        typeOrder.forEach { type ->
                            val groupOptions = grouped[type] ?: return@forEach
                            item(key = "header_$type") {
                                Text(
                                    when (type) {
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
                            items(groupOptions, key = { "${it.type}_${it.name}_${it.provider}" }) { option ->
                                MobileWatchOptionRow(
                                    option = option,
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

                        if (onBrowseEpisodes != null) {
                            item(key = "browse_episodes_torrent") {
                                Text(
                                    "TORRENT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onBrowseEpisodes() },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Cloud, "Torrent", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Browse Episodes (Torrent)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                            Text("Pick season & episode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        }
                                        Surface(color = Color(0xFF9C27B0), shape = MaterialTheme.shapes.small) {
                                            Text("TORRENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                        Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileWatchOptionRow(option: WatchOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (option.type == WatchOptionType.TORRENT) {
                Icon(Icons.Filled.Cloud, "Torrent", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp))
            } else if (option.logoPath != null) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w92${option.logoPath}",
                    contentDescription = option.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(Icons.Filled.Movie, option.name, modifier = Modifier.size(36.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(option.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (option.type == WatchOptionType.TORRENT) {
                    val details = buildString {
                        option.provider?.let { append(it) }
                        option.seeds?.let { if (isNotEmpty()) append(" • "); append("$it seeds") }
                    }
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            val badgeColor = when (option.type) {
                WatchOptionType.FREE -> Color(0xFF4CAF50)
                WatchOptionType.SUBSCRIPTION -> Color(0xFF2196F3)
                WatchOptionType.ADS -> Color(0xFF8BC34A)
                WatchOptionType.RENT -> Color(0xFFFF9800)
                WatchOptionType.BUY -> Color(0xFFE91E63)
                WatchOptionType.TORRENT -> Color(0xFF9C27B0)
            }
            Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                Text(
                    when (option.type) {
                        WatchOptionType.FREE -> "FREE"
                        WatchOptionType.SUBSCRIPTION -> "STREAM"
                        WatchOptionType.ADS -> "FREE"
                        WatchOptionType.RENT -> "RENT"
                        WatchOptionType.BUY -> "BUY"
                        WatchOptionType.TORRENT -> "TORRENT"
                    },
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

private fun launchStreamingApp(context: Context, option: WatchOption) {
    option.deepLinkUrl?.let { deepLink ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                option.packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) { }
        catch (_: Exception) { }
    }
    option.packageName?.let { pkg ->
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "Opening ${option.name}...", Toast.LENGTH_LONG).show()
                return
            }
        } catch (_: Exception) { }
    }
    option.justWatchLink?.let { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return
        } catch (_: Exception) { }
    }
    Toast.makeText(context, "${option.name} app not installed", Toast.LENGTH_SHORT).show()
}
