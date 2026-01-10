package com.movierecommender.app.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Service for streaming torrent video content.
 * Manages torrent download and provides a local file path for playback.
 */
class TorrentStreamService : Service(), TorrentListener {
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "torrent_streaming"
        private const val NOTIFICATION_ID = 9001
        
        fun getIntent(context: Context): Intent {
            return Intent(context, TorrentStreamService::class.java)
        }
    }
    
    private val binder = TorrentBinder()
    private var torrentStream: TorrentStream? = null
    private var currentTorrent: Torrent? = null
    
    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _downloadSpeed = MutableStateFlow(0)
    val downloadSpeed: StateFlow<Int> = _downloadSpeed.asStateFlow()
    
    private val _seeds = MutableStateFlow(0)
    val seeds: StateFlow<Int> = _seeds.asStateFlow()
    
    inner class TorrentBinder : Binder() {
        fun getService(): TorrentStreamService = this@TorrentStreamService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTorrentStream()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Preparing stream..."))
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        stopStream()
        torrentStream?.stopStream()
        torrentStream = null
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Torrent Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows torrent streaming progress"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Movie Streaming")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun initTorrentStream() {
        val saveLocation = File(cacheDir, "torrent_stream")
        if (!saveLocation.exists()) {
            saveLocation.mkdirs()
        }
        
        val options = TorrentOptions.Builder()
            .saveLocation(saveLocation)
            .removeFilesAfterStop(true)
            .maxConnections(200)
            .maxDownloadSpeed(0) // Unlimited
            .maxUploadSpeed(0)   // Unlimited
            .build()
        
        torrentStream = TorrentStream.init(options)
        torrentStream?.addListener(this)
    }
    
    /**
     * Start streaming a torrent from a magnet URL.
     */
    fun startStream(magnetUrl: String) {
        android.util.Log.d("TorrentStreamService", "Starting stream: $magnetUrl")
        _streamState.value = TorrentStreamState.Connecting
        _downloadProgress.value = 0f
        
        torrentStream?.startStream(magnetUrl)
    }
    
    /**
     * Stop the current stream.
     */
    fun stopStream() {
        currentTorrent = null
        torrentStream?.stopStream()
        _streamState.value = TorrentStreamState.Idle
        _downloadProgress.value = 0f
        _downloadSpeed.value = 0
        _seeds.value = 0
    }
    
    /**
     * Get the video file path for playback (if ready).
     */
    fun getVideoFilePath(): String? {
        return currentTorrent?.videoFile?.absolutePath
    }
    
    // TorrentListener callbacks
    
    override fun onStreamPrepared(torrent: Torrent?) {
        android.util.Log.d("TorrentStreamService", "Stream prepared")
        currentTorrent = torrent
        torrent?.startDownload()
    }
    
    override fun onStreamStarted(torrent: Torrent?) {
        android.util.Log.d("TorrentStreamService", "Stream started")
        _streamState.value = TorrentStreamState.Buffering(0f)
        updateNotification("Buffering...")
    }
    
    override fun onStreamReady(torrent: Torrent?) {
        android.util.Log.d("TorrentStreamService", "Stream ready for playback")
        val videoPath = torrent?.videoFile?.absolutePath
        if (videoPath != null) {
            _streamState.value = TorrentStreamState.Ready(videoPath)
            updateNotification("Playing...")
        }
    }
    
    override fun onStreamProgress(torrent: Torrent?, status: StreamStatus?) {
        status?.let {
            val progress = it.progress
            _downloadProgress.value = progress
            _downloadSpeed.value = it.downloadSpeed
            _seeds.value = it.seeds
            
            val currentState = _streamState.value
            if (currentState is TorrentStreamState.Buffering || currentState is TorrentStreamState.Streaming) {
                if (progress < 5f) {
                    _streamState.value = TorrentStreamState.Buffering(progress)
                } else {
                    _streamState.value = TorrentStreamState.Streaming(progress)
                }
            }
            
            val speedMbps = it.downloadSpeed / 1024 / 1024
            updateNotification("Streaming: ${progress.toInt()}% (${speedMbps}MB/s)")
        }
    }
    
    override fun onStreamStopped() {
        android.util.Log.d("TorrentStreamService", "Stream stopped")
        _streamState.value = TorrentStreamState.Idle
        currentTorrent = null
    }
    
    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        android.util.Log.e("TorrentStreamService", "Stream error: ${e?.message}")
        _streamState.value = TorrentStreamState.Error(e?.message ?: "Unknown error")
        updateNotification("Error: ${e?.message}")
    }
}

/**
 * Represents the current state of torrent streaming.
 */
sealed class TorrentStreamState {
    object Idle : TorrentStreamState()
    object Connecting : TorrentStreamState()
    data class Buffering(val progress: Float) : TorrentStreamState()
    data class Streaming(val progress: Float) : TorrentStreamState()
    data class Ready(val videoPath: String) : TorrentStreamState()
    data class Error(val message: String) : TorrentStreamState()
}
