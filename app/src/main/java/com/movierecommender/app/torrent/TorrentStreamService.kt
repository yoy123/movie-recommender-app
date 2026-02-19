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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.StatFs
import java.io.File

/**
 * Service for streaming torrent video content.
 * Manages torrent download and provides a local file path for playback.
 */
class TorrentStreamService : Service(), TorrentListener {
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "torrent_streaming"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_CLEAR_CACHE = "com.movierecommender.app.torrent.CLEAR_CACHE"
        
        // Dynamic cache size constants
        private const val MIN_CACHE_SIZE_MB = 100  // Minimum cache to ensure streaming works
        private const val FREE_SPACE_PERCENTAGE = 0.75 // Use 75% of available free space
        private const val RESERVED_SPACE_MB = 500 // Always leave at least 500MB free on device
        
        /**
         * Calculate optimal cache size based on available free space.
         * Uses 50% of free space with a minimum of MIN_CACHE_SIZE_MB.
         * Also ensures at least RESERVED_SPACE_MB remains free on the device.
         */
        fun calculateDynamicCacheSize(cacheDir: File): Int {
            return try {
                val statFs = StatFs(cacheDir.absolutePath)
                val freeSpaceMB = statFs.availableBytes / (1024 * 1024)
                
                // Calculate usable space (free space minus reserved)
                val usableSpaceMB = (freeSpaceMB - RESERVED_SPACE_MB).coerceAtLeast(0)
                
                // Take percentage of usable space
                val calculatedSizeMB = (usableSpaceMB * FREE_SPACE_PERCENTAGE).toInt()
                
                // Ensure minimum size
                val finalSize = calculatedSizeMB.coerceAtLeast(MIN_CACHE_SIZE_MB)
                
                android.util.Log.d("TorrentStreamService", 
                    "Dynamic cache: freeSpace=${freeSpaceMB}MB, usable=${usableSpaceMB}MB, " +
                    "calculated=${calculatedSizeMB}MB, final=${finalSize}MB")
                
                finalSize
            } catch (e: Exception) {
                android.util.Log.w("TorrentStreamService", 
                    "Failed to calculate dynamic cache size, using default", e)
                MIN_CACHE_SIZE_MB // Safe fallback
            }
        }
        
        fun getIntent(context: Context): Intent {
            return Intent(context, TorrentStreamService::class.java)
        }

        fun getClearCacheIntent(context: Context): Intent {
            return Intent(context, TorrentStreamService::class.java).apply {
                action = ACTION_CLEAR_CACHE
            }
        }
    }
    
    private val binder = TorrentBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var torrentStream: TorrentStream? = null
    private var currentTorrent: Torrent? = null
    private var currentMagnetUrl: String? = null
    private var currentPlaybackPosition = 0L
    private var cacheDir: File? = null
    private var isCleaningCache = false
    private var isDownloadPaused = false
    private var lastCacheCheckMs = 0L
    private var lastNotificationMs = 0L
    private var lastCleanupMs = 0L
    private var dynamicMaxCacheSizeMB: Int = MIN_CACHE_SIZE_MB // Recalculated on init
    
    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _downloadSpeed = MutableStateFlow(0)
    val downloadSpeed: StateFlow<Int> = _downloadSpeed.asStateFlow()
    
    private val _seeds = MutableStateFlow(0)
    val seeds: StateFlow<Int> = _seeds.asStateFlow()
    
    private val _cacheUsageMB = MutableStateFlow(0)
    val cacheUsageMB: StateFlow<Int> = _cacheUsageMB.asStateFlow()
    
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
        if (intent?.action == ACTION_CLEAR_CACHE) {
            stopStream()
            clearCacheDirectory()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification("Preparing stream..."))
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        stopStream()
        torrentStream?.stopStream()
        torrentStream = null
        // Clean up cache when service is destroyed (app exits)
        clearCacheDirectory()
        super.onDestroy()
    }
    
    /**
     * Update playback position for intelligent cache management.
     * Older chunks are deleted as playback progresses.
     * File operations run on IO dispatcher to prevent UI freeze.
     */
    fun updatePlaybackPosition(positionMs: Long) {
        currentPlaybackPosition = positionMs
        val now = System.currentTimeMillis()
        // Throttle all file operations to every 10 seconds to prevent freeze
        if (currentPlaybackPosition > 0L && now - lastCleanupMs > 10000) {
            lastCleanupMs = now
            serviceScope.launch {
                cleanupOldChunks()
                manageCacheSize()
                if (isDownloadPaused) {
                    maybeResumeDownload()
                }
            }
        }
    }
    
    /**
     * Clear the entire torrent cache directory.
     */
    private fun clearCacheDirectory() {
        cacheDir?.let { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
                android.util.Log.d("TorrentStreamService", "Cache cleared")
            }
        }
    }
    
    /**
     * Monitor cache size and manage disk space.
     * Pauses downloading if cache is full, resumes when space is available.
     * Uses dynamically calculated cache limit based on available free space.
     */
    private fun manageCacheSize() {
        val now = System.currentTimeMillis()
        if (now - lastCacheCheckMs < 5000) return
        lastCacheCheckMs = now
        cacheDir?.let { dir ->
            val cacheSizeMB = getCacheSizeInMB(dir)
            _cacheUsageMB.value = cacheSizeMB
            
            // If cache is over dynamic limit, clean old chunks more aggressively
            if (cacheSizeMB > dynamicMaxCacheSizeMB) {
                pauseDownloadIfPossible()
                cleanupOldChunks(aggressive = true)
            } else if (isDownloadPaused) {
                maybeResumeDownload()
            }
        }
    }
    
    /**
     * Delete chunks older than current playback position to free cache.
     */
    private fun cleanupOldChunks(aggressive: Boolean = false) {
        if (isCleaningCache) return
        cacheDir?.let { dir ->
            isCleaningCache = true
            try {
                val files = dir.listFiles() ?: emptyArray()
                val videoPath = currentTorrent?.videoFile?.absolutePath
                files.forEach { file ->
                    if (file.isFile) {
                        if (videoPath != null && file.absolutePath == videoPath) return@forEach
                        if (file.name.endsWith(".torrent", ignoreCase = true)) return@forEach
                        val fileAgeMs = System.currentTimeMillis() - file.lastModified()
                        val oneMinuteMs = 60 * 1000

                        // Delete files if:
                        // 1. File is older than 1 minute AND playback has passed it, OR
                        // 2. In aggressive mode and cache is full
                        if ((fileAgeMs > oneMinuteMs && currentPlaybackPosition > 0) || aggressive) {
                            if (file.delete()) {
                                android.util.Log.d("TorrentStreamService", "Deleted cache file: ${file.name}")
                            }
                        }
                    }
                }
            } finally {
                isCleaningCache = false
            }
        }
    }

    private fun pauseDownloadIfPossible() {
        pauseDownloadInternal()
    }

    private fun maybeResumeDownload() {
        val cacheSizeMB = cacheDir?.let { getCacheSizeInMB(it) } ?: return
        if (cacheSizeMB <= dynamicMaxCacheSizeMB) {
            resumeDownloadInternal()
        }
    }

    fun pauseDownloadIfPossiblePublic(): Boolean {
        return pauseDownloadInternal()
    }

    fun resumeDownloadIfPossiblePublic(): Boolean {
        return resumeDownloadInternal()
    }

    private fun pauseDownloadInternal(): Boolean {
        if (isDownloadPaused) return true
        val paused = runCatching {
            val method = torrentStream?.javaClass?.getMethod("pauseStream")
            method?.invoke(torrentStream)
            true
        }.getOrDefault(false)

        if (paused) {
            isDownloadPaused = true
            updateNotification("Cache full — pausing download")
        }
        return paused
    }

    private fun resumeDownloadInternal(): Boolean {
        val resumed = runCatching {
            val method = torrentStream?.javaClass?.getMethod("resumeStream")
            method?.invoke(torrentStream)
            true
        }.getOrDefault(false)

        if (resumed) {
            isDownloadPaused = false
            updateNotification("Resumed download")
        }
        return resumed
    }
    
    /**
     * Get total cache directory size in MB.
     */
    private fun getCacheSizeInMB(dir: File): Int {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return (size / (1024 * 1024)).toInt()
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
        val now = System.currentTimeMillis()
        if (now - lastNotificationMs < 10000) return
        lastNotificationMs = now
        val notification = createNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun initTorrentStream() {
        val saveLocation = File(applicationContext.cacheDir, "torrent_stream")
        this.cacheDir = saveLocation
        if (!saveLocation.exists()) {
            saveLocation.mkdirs()
        }
        
        // Calculate dynamic cache size based on available free space
        dynamicMaxCacheSizeMB = calculateDynamicCacheSize(saveLocation)
        android.util.Log.i("TorrentStreamService", "Torrent cache size set to ${dynamicMaxCacheSizeMB}MB")
        
        val options = TorrentOptions.Builder()
            .saveLocation(saveLocation)
            .removeFilesAfterStop(false) // Keep files for intelligent cache management
            .maxConnections(200)
            .maxDownloadSpeed(0) // Unlimited
            .maxUploadSpeed(0)   // Unlimited
            .build()
        
        torrentStream = TorrentStream.init(options)
        torrentStream?.addListener(this)
    }
    
    /**
     * Start streaming a torrent from a magnet URL.
     * If switching to a different movie, clears the previous cache first.
     */
    fun startStream(magnetUrl: String) {
        android.util.Log.d("TorrentStreamService", "Starting stream: $magnetUrl")
        if (currentMagnetUrl == magnetUrl && currentTorrent != null) {
            val resumed = resumeDownloadInternal()
            if (resumed) return
        }

        if (currentMagnetUrl != null && currentMagnetUrl != magnetUrl) {
            // Switching to a different movie - clear cache from previous movie
            android.util.Log.d("TorrentStreamService", "Switching movies - clearing previous cache")
            stopStream()
            clearCacheDirectory()
        }

        currentMagnetUrl = magnetUrl
        _streamState.value = TorrentStreamState.Connecting
        _downloadProgress.value = 0f

        torrentStream?.startStream(magnetUrl)
    }
    
    /**
     * Stop the current stream.
     */
    fun stopStream() {
        currentTorrent = null
        currentMagnetUrl = null
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
            val cacheMB = _cacheUsageMB.value
            updateNotification("Streaming: ${progress.toInt()}% (${speedMbps}MB/s) Cache: ${cacheMB}MB")
        }
    }
    
    override fun onStreamStopped() {
        android.util.Log.d("TorrentStreamService", "Stream stopped")
        _streamState.value = TorrentStreamState.Idle
        currentTorrent = null
        currentMagnetUrl = null
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
