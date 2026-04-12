package com.movierecommender.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.movierecommender.app.torrent.TorrentStreamService
import com.movierecommender.app.torrent.TorrentStreamState
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun StreamingPlayerScreen(
    movieTitle: String,
    magnetUrl: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var torrentService by remember { mutableStateOf<TorrentStreamService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var shouldStopStream by remember { mutableStateOf(true) }
    var lastProgress by remember { mutableStateOf(0f) }
    var lastProgressTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stallRetries by remember { mutableIntStateOf(0) }
    
    // Rebuffering state: when player outruns the torrent download
    var isWaitingForData by remember { mutableStateOf(false) }
    var dataWaitTrigger by remember { mutableIntStateOf(0) }
    var pendingResumePosition by remember { mutableLongStateOf(0L) }
    var lastSaveTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Load saved playback position
    val savedPosition = remember(magnetUrl) {
        val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
        val key = "position_${magnetUrl.hashCode()}"
        prefs.getLong(key, 0L)
    }
    
    val streamState = torrentService?.streamState?.collectAsState()?.value ?: TorrentStreamState.Idle
    val downloadProgress = torrentService?.downloadProgress?.collectAsState()?.value ?: 0f
        // Restart stream if buffering stalls with no progress for too long
        LaunchedEffect(streamState, downloadProgress) {
            if (streamState is TorrentStreamState.Buffering || streamState is TorrentStreamState.Connecting) {
                val now = System.currentTimeMillis()
                if (downloadProgress > lastProgress + 0.1f) {
                    lastProgress = downloadProgress
                    lastProgressTime = now
                    stallRetries = 0
                } else if (now - lastProgressTime > 20000 && stallRetries < 2) {
                    stallRetries += 1
                    lastProgressTime = now
                    torrentService?.stopStream()
                    torrentService?.startStream(magnetUrl)
                }
            }
        }
    val downloadSpeed = torrentService?.downloadSpeed?.collectAsState()?.value ?: 0
    val seeds = torrentService?.seeds?.collectAsState()?.value ?: 0
    
    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as TorrentStreamService.TorrentBinder
                torrentService = serviceBinder.getService()
                isBound = true
                
                // Start streaming when service is connected
                torrentService?.startStream(magnetUrl)
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                torrentService = null
                isBound = false
            }
        }
    }
    
    // Bind to service on composition
    DisposableEffect(Unit) {
        val intent = TorrentStreamService.getIntent(context)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        onDispose {
            // Save current playback position
            exoPlayer?.let { player ->
                val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
                val key = "position_${magnetUrl.hashCode()}"
                prefs.edit().putLong(key, player.currentPosition).apply()
            }
            if (shouldStopStream) {
                torrentService?.stopStream()
            }
            if (isBound) {
                context.unbindService(serviceConnection)
            }
            exoPlayer?.release()
        }
    }
    
    // Track last known good position for error recovery
    var lastGoodPosition by remember { mutableLongStateOf(savedPosition) }
    
    // Initialize ExoPlayer when stream is ready
    LaunchedEffect(streamState) {
        if (streamState is TorrentStreamState.Ready && exoPlayer == null) {
            val videoPath = streamState.videoPath
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri("file://$videoPath")
                // Resume from saved position if available
                if (savedPosition > 0L) {
                    setMediaItem(mediaItem, savedPosition)
                } else {
                    setMediaItem(mediaItem)
                }
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        // Update last good position using the player's actual position
                        if (playing) {
                            val playerPos = this@apply.currentPosition
                            if (playerPos > 0L) {
                                lastGoodPosition = playerPos
                            }
                        }
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                // Ensure playWhenReady stays true during buffering
                                // This handles seeks into unbuffered regions
                                if (!playWhenReady) {
                                    playWhenReady = true
                                }
                            }
                            Player.STATE_READY -> {
                                // Resume playback when ready after buffering
                                if (playWhenReady && !isPlaying) {
                                    play()
                                }
                                // Update last good position using the player's actual position
                                val playerPos = this@apply.currentPosition
                                if (playerPos > 0L) {
                                    lastGoodPosition = playerPos
                                }
                            }
                            Player.STATE_ENDED -> {
                                // When ExoPlayer outruns the torrent download, it hits EOF → STATE_ENDED.
                                // DO NOT re-prepare immediately. Defer to wait-for-data mechanism.
                                // CRITICAL: Use lastGoodPosition, NOT currentPosition.
                                // At STATE_ENDED, currentPosition is at/near the EOF boundary
                                // which would cause immediate re-failure on resume.
                                val latestProgress = torrentService?.downloadProgress?.value ?: 0f
                                val stillDownloading = latestProgress < 100f
                                if (stillDownloading) {
                                    val safePos = (lastGoodPosition - 5000L).coerceAtLeast(0L)
                                    android.util.Log.d("StreamingPlayer", "STATE_ENDED while downloading (${latestProgress}%), deferring resume from $safePos (lastGood=$lastGoodPosition)")
                                    pendingResumePosition = safePos
                                    isWaitingForData = true
                                    dataWaitTrigger++
                                }
                            }
                            else -> {}
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Torrent file may have incomplete data causing codec/source errors.
                        // Use lastGoodPosition with safety margin — NOT player.currentPosition
                        android.util.Log.e("StreamingPlayer", "Player error: ${error.message}", error)
                        
                        val safePos = (lastGoodPosition - 5000L).coerceAtLeast(0L)
                        android.util.Log.d("StreamingPlayer", "Player error, deferring resume from $safePos (lastGood=$lastGoodPosition)")
                        pendingResumePosition = safePos
                        isWaitingForData = true
                        dataWaitTrigger++
                    }
                })
            }
            isPlayerReady = true
        }
    }
    
    // Update playback position and lastGoodPosition for intelligent cache management
    LaunchedEffect(isPlayerReady) {
        while (isPlayerReady) {
            exoPlayer?.let { player ->
                duration = player.duration.coerceAtLeast(0L)
                // Continuously track last good position during active playback
                if (player.isPlaying && player.currentPosition > 0L) {
                    lastGoodPosition = player.currentPosition
                    // Periodically save position to SharedPreferences for crash recovery
                    val now = System.currentTimeMillis()
                    if (now - lastSaveTime > 10000L) {
                        val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
                        val posKey = "position_${magnetUrl.hashCode()}"
                        prefs.edit().putLong(posKey, player.currentPosition).apply()
                        lastSaveTime = now
                    }
                }
                torrentService?.updatePlaybackPosition(player.currentPosition)
                // Feed duration to torrent service for byte offset calculations
                if (duration > 0L) {
                    torrentService?.updatePlaybackDuration(duration)
                }
            }
            delay(500)
        }
    }
    
    // Wait for more torrent data before re-preparing player.
    // Uses piece-level checking (hasBytesAtTime) to wait for the SPECIFIC data
    // ExoPlayer needs, not just overall download progress. Also uses exponential backoff.
    LaunchedEffect(dataWaitTrigger) {
        if (dataWaitTrigger == 0) return@LaunchedEffect
        
        val resumePos = pendingResumePosition
        android.util.Log.d("StreamingPlayer", "Waiting for data at position $resumePos ms (trigger #$dataWaitTrigger)")
        
        // Save position immediately in case of crash/kill
        val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
        val posKey = "position_${magnetUrl.hashCode()}"
        if (resumePos > 0L) {
            prefs.edit().putLong(posKey, resumePos).apply()
        }
        
        // Tell the torrent to prioritize data around our resume position
        torrentService?.updatePlaybackDuration(duration)
        torrentService?.updatePlaybackPosition(resumePos)
        
        // Exponential backoff: wait longer on repeated rebuffers at the same area
        val backoffMultiplier = dataWaitTrigger.coerceAtMost(5)
        val minWaitSeconds = 5 * backoffMultiplier
        val maxWaitSeconds = 60
        
        // Wait until the torrent has downloaded the specific bytes we need.
        // Check: bytes at resumePos and resumePos + 60 seconds ahead.
        var elapsed = 0
        val aheadMs = 60000L * backoffMultiplier.coerceAtMost(3)
        while (elapsed < maxWaitSeconds) {
            delay(2000)
            elapsed += 2
            
            val hasResumeBytes = torrentService?.hasBytesAtTime(resumePos) ?: false
            val hasAheadBytes = torrentService?.hasBytesAtTime(resumePos + aheadMs) ?: false
            val currentProg = torrentService?.downloadProgress?.value ?: 0f
            android.util.Log.d("StreamingPlayer", "Rebuffering... hasResumeBytes=$hasResumeBytes, hasAheadBytes=$hasAheadBytes, progress=$currentProg% (${elapsed}s elapsed, backoff=${backoffMultiplier}x)")
            
            if (elapsed >= minWaitSeconds && hasResumeBytes && hasAheadBytes) {
                break
            }
            if (currentProg >= 100f) {
                break
            }
        }
        
        // Re-prepare player from the (now more complete) file
        val videoPath = torrentService?.getVideoFilePath()
        if (videoPath != null && exoPlayer != null) {
            exoPlayer?.let { player ->
                val newMediaItem = MediaItem.fromUri("file://$videoPath")
                android.util.Log.d("StreamingPlayer", "Re-preparing player at position $resumePos after waiting ${elapsed}s (backoff=${backoffMultiplier}x)")
                player.setMediaItem(newMediaItem, resumePos)
                player.prepare()
                player.playWhenReady = true
            }
        }
        isWaitingForData = false
    }
    
    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlayerReady) exoPlayer?.play()
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer?.release()
                    exoPlayer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movieTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        shouldStopStream = false
                        torrentService?.pauseDownloadIfPossiblePublic()
                        exoPlayer?.release()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            when (streamState) {
                is TorrentStreamState.Idle, is TorrentStreamState.Connecting -> {
                    LoadingOverlay(
                        title = "Connecting...",
                        subtitle = "Finding peers"
                    )
                }
                
                is TorrentStreamState.Buffering -> {
                    LoadingOverlay(
                        title = "Buffering...",
                        subtitle = "Progress: ${streamState.progress.toInt()}%",
                        progress = streamState.progress / 100f,
                        seeds = seeds,
                        speed = downloadSpeed
                    )
                }
                
                is TorrentStreamState.Streaming, is TorrentStreamState.Ready -> {
                    // Show video player
                    if (exoPlayer != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    // Keep screen on during playback to prevent screensaver
                                    keepScreenOn = true
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Show streaming stats overlay
                        if (streamState is TorrentStreamState.Streaming) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        "Seeds: $seeds",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "${downloadSpeed / 1024} KB/s",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "${downloadProgress.toInt()}%",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        
                        // Rebuffering overlay when waiting for more torrent data
                        if (isWaitingForData) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(64.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 5.dp
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "Buffering more content...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Downloaded: ${downloadProgress.toInt()}%",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    if (seeds > 0 || downloadSpeed > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Seeds: $seeds • ${downloadSpeed / 1024} KB/s",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LoadingOverlay(
                            title = "Preparing player...",
                            subtitle = "Almost ready"
                        )
                    }
                }
                
                is TorrentStreamState.Error -> {
                    ErrorOverlay(
                        message = streamState.message,
                        onRetry = { torrentService?.startStream(magnetUrl) },
                        onBack = onBackClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(
    title: String,
    subtitle: String,
    progress: Float? = null,
    seeds: Int = 0,
    speed: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        if (seeds > 0 || speed > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (seeds > 0) {
                    Text(
                        text = "Seeds: $seeds",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp
                    )
                }
                if (speed > 0) {
                    Text(
                        text = "${speed / 1024} KB/s",
                        color = Color(0xFF2196F3),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌",
            fontSize = 64.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Streaming Error",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Go Back")
            }
            
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
