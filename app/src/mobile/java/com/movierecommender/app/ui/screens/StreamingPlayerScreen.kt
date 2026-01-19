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
    var shouldStopStream by remember { mutableStateOf(true) }
    var lastProgress by remember { mutableStateOf(0f) }
    var lastProgressTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stallRetries by remember { mutableIntStateOf(0) }
    
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
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            val latestProgress = torrentService?.downloadProgress?.value ?: 0f
                            val stillDownloading = latestProgress < 100f
                            if (stillDownloading) {
                                val resumePosition = exoPlayer?.currentPosition ?: 0L
                                setMediaItem(mediaItem, resumePosition)
                                prepare()
                                playWhenReady = true
                            }
                        }
                    }
                })
            }
            isPlayerReady = true
        }
    }
    
    // Update playback position for intelligent cache management
    LaunchedEffect(isPlayerReady) {
        while (isPlayerReady) {
            exoPlayer?.let { player ->
                torrentService?.updatePlaybackPosition(player.currentPosition)
            }
            delay(500)
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
