package com.movierecommender.app.ui.screens.firestick

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
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
    
    val focusRequester = remember { FocusRequester() }
    
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
                torrentService?.startStream(magnetUrl)
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                torrentService = null
                isBound = false
            }
        }
    }
    
    // Bind to service
    DisposableEffect(Unit) {
        val intent = TorrentStreamService.getIntent(context)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        onDispose {
            // Save current playback position before releasing player
            exoPlayer?.let { player ->
                val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
                val key = "position_${magnetUrl.hashCode()}"
                prefs.edit().putLong(key, player.currentPosition).apply()
                player.release()
            }
            exoPlayer = null

            if (shouldStopStream) {
                torrentService?.stopStream()
            }
            // Only unbind if we're stopping the stream; keep service alive for resume
            if (isBound && shouldStopStream) {
                context.unbindService(serviceConnection)
                isBound = false
            } else if (isBound) {
                // Just unbind without stopping - service continues in background
                context.unbindService(serviceConnection)
                isBound = false
            }
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
                                val latestProgress = torrentService?.downloadProgress?.value ?: downloadProgress
                                val stillDownloading = latestProgress < 100f
                                if (stillDownloading) {
                                    // lastGoodPosition was tracked during active playback (500ms loop)
                                    // Subtract 5s safety margin to avoid re-hitting the exact boundary
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
                        // which may be at/near the corrupt/missing data boundary.
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
    
    // Update position, lastGoodPosition, and cache management periodically
    LaunchedEffect(isPlayerReady) {
        while (isPlayerReady) {
            exoPlayer?.let { player ->
                currentPosition = player.currentPosition
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
                torrentService?.updatePlaybackPosition(currentPosition)
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
        val backoffMultiplier = dataWaitTrigger.coerceAtMost(5) // 1x, 2x, 3x, 4x, 5x
        val minWaitSeconds = 5 * backoffMultiplier
        val maxWaitSeconds = 60
        
        // Wait until the torrent has downloaded the specific bytes we need.
        // Check: bytes at resumePos and resumePos + 60 seconds ahead.
        // This ensures enough contiguous data for sustained playback.
        var elapsed = 0
        val aheadMs = 60000L * backoffMultiplier.coerceAtMost(3) // 60s, 120s, 180s lookahead
        while (elapsed < maxWaitSeconds) {
            delay(2000)
            elapsed += 2
            
            val hasResumeBytes = torrentService?.hasBytesAtTime(resumePos) ?: false
            val hasAheadBytes = torrentService?.hasBytesAtTime(resumePos + aheadMs) ?: false
            val currentProg = torrentService?.downloadProgress?.value ?: 0f
            android.util.Log.d("StreamingPlayer", "Rebuffering... hasResumeBytes=$hasResumeBytes, hasAheadBytes=$hasAheadBytes, progress=$currentProg% (${elapsed}s elapsed, backoff=${backoffMultiplier}x)")
            
            // Wait at least minWaitSeconds, then check if bytes are available
            if (elapsed >= minWaitSeconds && hasResumeBytes && hasAheadBytes) {
                break
            }
            // Also break if download is complete
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
    
    // Auto-hide controls after 5 seconds when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlayerReady && isPlaying) {
            delay(5000)
            showControls = false
        }
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
    
    // Request focus when ready
    LaunchedEffect(isPlayerReady) {
        if (isPlayerReady) {
            focusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPlayerReady) {
                                if (showControls) {
                                    // Toggle play/pause
                                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                } else {
                                    showControls = true
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isPlayerReady) {
                                showControls = true
                                // Seek backward 10 seconds
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isPlayerReady) {
                                showControls = true
                                // Seek forward 10 seconds
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            // Save position before backing out
                            exoPlayer?.let { player ->
                                val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
                                val key = "position_${magnetUrl.hashCode()}"
                                prefs.edit().putLong(key, player.currentPosition).apply()
                            }
                            shouldStopStream = false
                            torrentService?.pauseDownloadIfPossiblePublic()
                            onBackClick()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            exoPlayer?.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            exoPlayer?.pause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            exoPlayer?.let { it.seekTo(it.currentPosition + 30000) }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            exoPlayer?.let { it.seekTo((it.currentPosition - 30000).coerceAtLeast(0)) }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        when (streamState) {
            is TorrentStreamState.Idle, is TorrentStreamState.Connecting -> {
                TVLoadingOverlay(
                    title = "Connecting...",
                    subtitle = "Finding peers for streaming"
                )
            }
            
            is TorrentStreamState.Buffering -> {
                TVLoadingOverlay(
                    title = "Buffering...",
                    subtitle = "Progress: ${streamState.progress.toInt()}%",
                    progress = streamState.progress / 100f,
                    seeds = seeds,
                    speed = downloadSpeed
                )
            }
            
            is TorrentStreamState.Streaming, is TorrentStreamState.Ready -> {
                // Video player (no built-in controls - we use our own)
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // We handle controls via D-pad
                                // Keep screen on during playback to prevent Fire TV screensaver
                                keepScreenOn = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Custom TV controls overlay
                    if (showControls) {
                        TVControlsOverlay(
                            movieTitle = movieTitle,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            downloadProgress = downloadProgress,
                            seeds = seeds,
                            speed = downloadSpeed,
                            onPlayPause = { if (isPlaying) exoPlayer?.pause() else exoPlayer?.play() },
                            onSeekBack = { exoPlayer?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) } },
                            onSeekForward = { exoPlayer?.let { it.seekTo(it.currentPosition + 10000) } },
                            onBack = {
                                // Save position before backing out
                                exoPlayer?.let { player ->
                                    val prefs = context.getSharedPreferences("movie_playback", MODE_PRIVATE)
                                    val key = "position_${magnetUrl.hashCode()}"
                                    prefs.edit().putLong(key, player.currentPosition).apply()
                                }
                                shouldStopStream = false
                                torrentService?.pauseDownloadIfPossiblePublic()
                                onBackClick()
                            }
                        )
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
                                    modifier = Modifier.size(80.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 6.dp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Buffering more content...",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Downloaded: ${downloadProgress.toInt()}%",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                                if (seeds > 0 || downloadSpeed > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Seeds: $seeds • ${downloadSpeed / 1024} KB/s",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    TVLoadingOverlay(
                        title = "Preparing player...",
                        subtitle = "Almost ready"
                    )
                }
            }
            
            is TorrentStreamState.Error -> {
                TVErrorOverlay(
                    message = streamState.message,
                    onRetry = { torrentService?.startStream(magnetUrl) },
                    onBack = onBackClick
                )
            }
        }
    }
}

@Composable
private fun TVControlsOverlay(
    movieTitle: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    downloadProgress: Float,
    seeds: Int,
    speed: Int,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Top bar with title and back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = movieTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            // Streaming stats
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Seeds: $seeds",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
                Text(
                    text = "${speed / 1024} KB/s • ${downloadProgress.toInt()}%",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        // Center playback controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TVControlButton(
                icon = Icons.Default.FastRewind,
                description = "Rewind 10s",
                onClick = onSeekBack
            )
            
            TVControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                description = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                size = 80.dp
            )
            
            TVControlButton(
                icon = Icons.Default.FastForward,
                description = "Forward 10s",
                onClick = onSeekForward
            )
        }
        
        // Bottom progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
        ) {
            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // D-pad hint
            Text(
                text = "◀ Rewind  •  ▶ Forward  •  ⏸ Play/Pause  •  ⬅ Back",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TVControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .focusable(interactionSource = interactionSource),
        interactionSource = interactionSource
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
private fun TVLoadingOverlay(
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
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        
        if (seeds > 0 || speed > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                if (seeds > 0) {
                    Text(
                        text = "🌱 Seeds: $seeds",
                        color = Color(0xFF4CAF50),
                        fontSize = 18.sp
                    )
                }
                if (speed > 0) {
                    Text(
                        text = "⬇ ${speed / 1024} KB/s",
                        color = Color(0xFF2196F3),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TVErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌",
            fontSize = 80.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Streaming Error",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backFocused by backInteraction.collectIsFocusedAsState()
            
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.focusable(interactionSource = backInteraction),
                interactionSource = backInteraction,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (backFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent
                )
            ) {
                Text("Go Back", fontSize = 18.sp)
            }
            
            val retryInteraction = remember { MutableInteractionSource() }
            val retryFocused by retryInteraction.collectIsFocusedAsState()
            
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .focusRequester(retryFocusRequester)
                    .focusable(interactionSource = retryInteraction),
                interactionSource = retryInteraction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (retryFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text("Retry", fontSize = 18.sp)
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
