package com.movierecommender.app.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.system.Os
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that downloads the active torrent into a single resumable cache and exposes
 * the local video file to Media3. Playback starts only after a conservative no-stall buffer gate.
 */
class TorrentStreamService : Service(), TorrentListener {

    companion object {
        private const val TAG = "TorrentStreamService"
        private const val NOTIFICATION_CHANNEL_ID = "torrent_streaming"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_CLEAR_CACHE = "com.movierecommender.app.torrent.CLEAR_CACHE"
        private const val MIN_SPEED_SAMPLES = 10
        private const val SPEED_SAMPLE_WINDOW = 30
        private const val NO_PROGRESS_TIMEOUT_MS = 180_000L
        private const val PRIORITY_LOOKAHEAD_MS = 15L * 60L * 1000L
        private const val BYTES_PER_MB = 1024L * 1024L

        /**
         * Returns the total size the current session cache may reach while preserving 500 MB of
         * genuinely available storage. Existing cache bytes are included in the total allowance.
         */
        fun calculateDynamicCacheSize(cacheDir: File): Int {
            return try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val statFs = StatFs(cacheDir.absolutePath)
                val freeSpaceMb = statFs.availableBytes / BYTES_PER_MB
                val existingCacheMb = cacheDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() } / BYTES_PER_MB
                val limit = TorrentBufferPolicy.calculateCacheLimitMb(freeSpaceMb, existingCacheMb)
                android.util.Log.d(
                    TAG,
                    "Cache budget: free=${freeSpaceMb}MB existing=${existingCacheMb}MB " +
                        "reserve=${TorrentBufferPolicy.RESERVED_DEVICE_SPACE_MB}MB limit=${limit}MB"
                )
                limit
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to calculate torrent cache budget", e)
                0
            }
        }

        fun getIntent(context: Context): Intent = Intent(context, TorrentStreamService::class.java)

        fun getClearCacheIntent(context: Context): Intent =
            Intent(context, TorrentStreamService::class.java).apply { action = ACTION_CLEAR_CACHE }

        fun requestImmediateCacheClear(context: Context) {
            val appContext = context.applicationContext
            val intent = getClearCacheIntent(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private val binder = TorrentBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retentionJob: Job? = null
    private var torrentStream: TorrentStream? = null
    private var currentTorrent: Torrent? = null
    private var currentMagnetUrl: String? = null
    private var currentMagnetHash: Int? = null
    private var requestedPlaybackPosition = 0L
    private var currentPlaybackPosition = 0L
    private var currentPlaybackDuration = 0L
    private var cacheDir: File? = null
    private var isDownloadPaused = false
    private var lastCacheCheckMs = 0L
    private var lastNotificationMs = 0L
    private var lastPriorityUpdateMs = 0L
    private var dynamicMaxCacheSizeMB = 0

    private val speedSamples = ArrayDeque<Int>()

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
        TorrentCachePolicy.initialize(this)
        createNotificationChannel()
        initTorrentStream()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Preparing torrent session..."))

        if (intent?.action == ACTION_CLEAR_CACHE) {
            serviceScope.launch {
                stopStream()
                TorrentCachePolicy.clearCacheAndPlayback(
                    applicationContext,
                    "app closed or explicit cleanup"
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Called when the user removes/exits the app task. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            stopStream()
            TorrentCachePolicy.clearCacheAndPlayback(applicationContext, "app task closed")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        retentionJob?.cancel()
        torrentStream?.removeListener(this)
        torrentStream?.stopStream()
        torrentStream = null
        currentTorrent = null
        serviceScope.cancel()
        // Do not delete here: Android may destroy a service for resource pressure. Explicit task
        // removal, reboot, timeout, and clear actions own deletion so accidental backout can resume.
        super.onDestroy()
    }

    /**
     * Starts or resumes a torrent. Cached pieces for the same magnet are reused by libtorrent.
     */
    fun startStream(
        magnetUrl: String,
        resumePositionMs: Long = 0L,
        knownDurationMs: Long = 0L
    ) {
        val preparedMagnetUrl = TorrentMagnetUtils.enrichMagnetUrl(magnetUrl)
        // Playback preferences are keyed by the original URL supplied by the player.
        val magnetHash = magnetUrl.hashCode()
        retentionJob?.cancel()
        TorrentCachePolicy.markActive(applicationContext, magnetHash)
        requestedPlaybackPosition = resumePositionMs.coerceAtLeast(0L)
        currentPlaybackPosition = requestedPlaybackPosition
        if (knownDurationMs > 0L) currentPlaybackDuration = knownDurationMs

        if (currentMagnetUrl == preparedMagnetUrl && currentTorrent != null) {
            resumeDownloadInternal()
            prioritizeFirstMissingPiece(requestedPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
            if (_streamState.value is TorrentStreamState.Ready ||
                _streamState.value is TorrentStreamState.Streaming
            ) {
                currentTorrent?.videoFile?.absolutePath?.let {
                    _streamState.value = TorrentStreamState.Ready(it)
                }
            }
            updateNotification("Resuming cached playback...")
            return
        }

        if (currentMagnetUrl != null && currentMagnetUrl != preparedMagnetUrl) {
            stopStream()
            TorrentCachePolicy.clearCacheAndPlayback(applicationContext, "switching torrents")
            ensureCacheDirectory()
            dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
            TorrentCachePolicy.markActive(applicationContext, magnetHash)
        }

        currentMagnetUrl = preparedMagnetUrl
        currentMagnetHash = magnetHash
        _streamState.value = TorrentStreamState.Connecting
        _downloadProgress.value = 0f
        speedSamples.clear()
        torrentStream?.startStream(preparedMagnetUrl)
    }

    /**
     * Leaves the torrent downloading in the foreground for up to 60 minutes so an accidental
     * player backout can resume immediately. The timeout is persisted and backed by an alarm.
     */
    fun retainCurrentStreamForResume(positionMs: Long, durationMs: Long) {
        val hash = currentMagnetHash ?: return
        currentPlaybackPosition = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L) currentPlaybackDuration = durationMs
        val retainUntil = TorrentCachePolicy.retainForResume(applicationContext, hash)
        updateNotification("Cached for resume for 60 minutes")

        retentionJob?.cancel()
        retentionJob = serviceScope.launch {
            val delayMs = (retainUntil - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(delayMs)
            if (TorrentCachePolicy.isExpired(applicationContext)) {
                stopStream()
                TorrentCachePolicy.clearCacheAndPlayback(
                    applicationContext,
                    "60 minutes without returning to playback"
                )
                stopSelf()
            }
        }
    }

    /** Stops network activity but deliberately leaves cache ownership to TorrentCachePolicy. */
    fun stopStream() {
        currentTorrent = null
        currentMagnetUrl = null
        currentMagnetHash = null
        torrentStream?.stopStream()
        isDownloadPaused = false
        _streamState.value = TorrentStreamState.Idle
        _downloadProgress.value = 0f
        _downloadSpeed.value = 0
        _seeds.value = 0
        speedSamples.clear()
    }

    fun getVideoFilePath(): String? = currentTorrent?.videoFile?.absolutePath

    fun updatePlaybackDuration(durationMs: Long) {
        if (durationMs > 0L) currentPlaybackDuration = durationMs
    }

    /**
     * Tracks playback and continually prioritizes the first missing piece ahead of the player,
     * rather than jumping to an arbitrary point and leaving holes in the playable region.
     */
    fun updatePlaybackPosition(positionMs: Long) {
        currentPlaybackPosition = positionMs.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        if (now - lastPriorityUpdateMs >= 2_000L) {
            lastPriorityUpdateMs = now
            prioritizeFirstMissingPiece(currentPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
        }
        serviceScope.launch { manageCacheSize() }
    }

    fun hasBytesAt(byteOffset: Long): Boolean {
        val torrent = currentTorrent ?: return false
        val fileSize = torrent.videoFile?.length() ?: return false
        if (fileSize <= 0L) return false
        return runCatching {
            torrent.hasBytes(byteOffset.coerceIn(0L, fileSize - 1L))
        }.getOrDefault(false)
    }

    fun hasBytesAtTime(positionMs: Long): Boolean {
        val durationMs = currentPlaybackDuration
        val fileSize = currentTorrent?.videoFile?.length() ?: return false
        if (durationMs <= 0L || fileSize <= 0L) return false
        val byteOffset = timeToByteOffset(positionMs, durationMs, fileSize)
        return hasBytesAt(byteOffset)
    }

    fun pauseDownloadIfPossiblePublic(): Boolean = pauseDownloadInternal()

    fun resumeDownloadIfPossiblePublic(): Boolean = resumeDownloadInternal()

    private fun pauseDownloadInternal(): Boolean {
        if (isDownloadPaused) return true
        val paused = runCatching {
            currentTorrent?.pause() ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (paused) {
            isDownloadPaused = true
            updateNotification("Torrent download paused")
        }
        return paused
    }

    private fun resumeDownloadInternal(): Boolean {
        val torrent = currentTorrent ?: return false
        val resumed = runCatching {
            torrent.resume()
            true
        }.getOrDefault(false)
        if (resumed) {
            isDownloadPaused = false
            updateNotification("Torrent download resumed")
        }
        return resumed
    }

    private fun initTorrentStream() {
        ensureCacheDirectory()
        dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
        val options = TorrentOptions.Builder()
            .saveLocation(requireNotNull(cacheDir))
            .removeFilesAfterStop(false)
            .maxConnections(200)
            .maxDownloadSpeed(0)
            .maxUploadSpeed(0)
            .prepareSize(15L * BYTES_PER_MB)
            .build()
        torrentStream = TorrentStream.init(options)
        torrentStream?.addListener(this)
    }

    private fun ensureCacheDirectory() {
        val directory = TorrentCachePolicy.cacheDirectory(applicationContext)
        if (!directory.exists()) directory.mkdirs()
        cacheDir = directory
    }

    private fun manageCacheSize() {
        val now = System.currentTimeMillis()
        if (now - lastCacheCheckMs < 5_000L) return
        lastCacheCheckMs = now
        val directory = cacheDir ?: return
        val cacheSizeMb = getCacheSizeInMb(directory)
        _cacheUsageMB.value = cacheSizeMb
        if (dynamicMaxCacheSizeMB > 0 && cacheSizeMb > dynamicMaxCacheSizeMB && !isDownloadPaused) {
            pauseDownloadInternal()
            _streamState.value = TorrentStreamState.Error(
                "The Fire TV storage reserve was reached. Free storage or choose a smaller source."
            )
        }
    }

    private fun getCacheSizeInMb(directory: File): Int =
        (directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } / BYTES_PER_MB).toInt()

    private fun timeToByteOffset(positionMs: Long, durationMs: Long, fileSize: Long): Long {
        if (durationMs <= 0L || fileSize <= 0L) return 0L
        return (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs.toDouble() * fileSize)
            .toLong()
            .coerceIn(0L, fileSize - 1L)
    }

    private fun contiguousAvailableBytes(startByte: Long, maximumBytes: Long): Long {
        val torrent = currentTorrent ?: return 0L
        val fileSize = torrent.videoFile?.length() ?: return 0L
        if (fileSize <= 0L || maximumBytes <= 0L) return 0L
        val pieceLength = runCatching {
            torrent.torrentHandle.torrentFile().pieceLength().toLong()
        }.getOrDefault(0L)
        if (pieceLength <= 0L) return 0L

        val boundedStart = startByte.coerceIn(0L, fileSize - 1L)
        val boundedMaximum = minOf(maximumBytes, fileSize - boundedStart)
        var available = 0L
        while (available < boundedMaximum) {
            val offset = (boundedStart + available).coerceAtMost(fileSize - 1L)
            if (!runCatching { torrent.hasBytes(offset) }.getOrDefault(false)) break
            available += pieceLength
        }
        return available.coerceAtMost(boundedMaximum)
    }

    private fun prioritizeFirstMissingPiece(positionMs: Long, lookAheadMs: Long) {
        val torrent = currentTorrent ?: return
        val durationMs = currentPlaybackDuration
        val fileSize = torrent.videoFile?.length() ?: return
        if (durationMs <= 0L || fileSize <= 0L) return

        val startByte = timeToByteOffset(positionMs, durationMs, fileSize)
        val endPositionMs = (positionMs + lookAheadMs).coerceAtMost(durationMs)
        val endByte = timeToByteOffset(endPositionMs, durationMs, fileSize)
        val maximumAhead = (endByte - startByte).coerceAtLeast(0L)
        val contiguous = contiguousAvailableBytes(startByte, maximumAhead)
        val firstMissing = (startByte + contiguous).coerceAtMost(fileSize - 1L)
        runCatching { torrent.setInterestedBytes(firstMissing) }
            .onFailure { android.util.Log.w(TAG, "Failed to prioritize torrent piece", it) }
    }

    private fun conservativeDownloadSpeed(): Long {
        val positive = speedSamples.filter { it > 0 }.sorted()
        if (positive.isEmpty()) return 0L
        val index = ((positive.size - 1) * 0.25).toInt()
        return positive[index].toLong()
    }

    private fun readDurationMs(videoPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not read media duration before playback", e)
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun onStreamPrepared(torrent: Torrent?) {
        currentTorrent = torrent
        torrent?.startDownload()
        if (currentPlaybackDuration > 0L && requestedPlaybackPosition > 0L) {
            prioritizeFirstMissingPiece(requestedPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
        }
    }

    override fun onStreamStarted(torrent: Torrent?) {
        _streamState.value = TorrentStreamState.Buffering(0f)
        updateNotification("Downloading startup pieces...")
    }

    override fun onStreamReady(torrent: Torrent?) {
        currentTorrent = torrent
        val videoFile = torrent?.videoFile ?: return
        val videoPath = videoFile.absolutePath
        val fileSize = videoFile.length()
        if (currentPlaybackDuration <= 0L) {
            currentPlaybackDuration = readDurationMs(videoPath)
        }

        dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
        val requiredStorageMb = TorrentBufferPolicy.requiredStorageMb(fileSize)
        if (dynamicMaxCacheSizeMB <= 0 || requiredStorageMb > dynamicMaxCacheSizeMB) {
            pauseDownloadInternal()
            _streamState.value = TorrentStreamState.Error(
                "This source needs ${requiredStorageMb} MB of cache, but only " +
                    "${dynamicMaxCacheSizeMB.coerceAtLeast(0)} MB is safely available. " +
                    "Free storage or choose a smaller source."
            )
            return
        }

        serviceScope.launch { runNoStallPreBuffer(videoPath, fileSize) }
    }

    /**
     * Builds a contiguous playback region before opening Media3. There is no arbitrary three-minute
     * startup cap: a slow swarm waits longer at startup rather than predictably buffering later.
     */
    private suspend fun runNoStallPreBuffer(videoPath: String, fileSize: Long) {
        _streamState.value = TorrentStreamState.PreBuffering(videoPath, 0f)
        updateNotification("Building no-stall playback buffer...")

        val durationMs = currentPlaybackDuration.takeIf { it > 0L } ?: 90L * 60L * 1000L
        val startPositionMs = requestedPlaybackPosition.coerceIn(0L, durationMs)
        val startByte = timeToByteOffset(startPositionMs, durationMs, fileSize)
        val remainingBytes = (fileSize - startByte).coerceAtLeast(0L)
        var lockedTargetBytes = minOf(
            remainingBytes,
            TorrentBufferPolicy.MINIMUM_START_BUFFER_MB * BYTES_PER_MB
        )
        var lastAdvanceAt = System.currentTimeMillis()
        var lastContiguousBytes = 0L
        var lastOverallProgress = _downloadProgress.value

        while (true) {
            if (_downloadProgress.value >= 100f) break

            val positiveSampleCount = speedSamples.count { it > 0 }
            if (positiveSampleCount >= MIN_SPEED_SAMPLES) {
                val required = TorrentBufferPolicy.requiredContiguousBufferBytes(
                    fileSizeBytes = fileSize,
                    durationMs = durationMs,
                    startPositionMs = startPositionMs,
                    measuredDownloadBytesPerSecond = conservativeDownloadSpeed()
                )
                // A later slowdown may increase the requirement, but a brief spike can never reduce it.
                lockedTargetBytes = maxOf(lockedTargetBytes, required).coerceAtMost(remainingBytes)
            }

            val contiguousBytes = contiguousAvailableBytes(startByte, lockedTargetBytes)
            val firstMissingByte = (startByte + contiguousBytes).coerceAtMost(fileSize - 1L)
            currentTorrent?.let { torrent ->
                runCatching { torrent.setInterestedBytes(firstMissingByte) }
            }

            val progress = if (lockedTargetBytes > 0L) {
                (contiguousBytes.toFloat() / lockedTargetBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            _streamState.value = TorrentStreamState.PreBuffering(videoPath, progress)

            val overallProgress = _downloadProgress.value
            if (contiguousBytes > lastContiguousBytes || overallProgress > lastOverallProgress + 0.01f) {
                lastAdvanceAt = System.currentTimeMillis()
                lastContiguousBytes = contiguousBytes
                lastOverallProgress = overallProgress
            }

            android.util.Log.d(
                TAG,
                "NoStallBuffer: target=${lockedTargetBytes / BYTES_PER_MB}MB " +
                    "contiguous=${contiguousBytes / BYTES_PER_MB}MB " +
                    "speed=${conservativeDownloadSpeed() / 1024}KB/s " +
                    "samples=$positiveSampleCount"
            )

            if (positiveSampleCount >= MIN_SPEED_SAMPLES && contiguousBytes >= lockedTargetBytes) break
            if (System.currentTimeMillis() - lastAdvanceAt >= NO_PROGRESS_TIMEOUT_MS) {
                pauseDownloadInternal()
                _streamState.value = TorrentStreamState.Error(
                    "The torrent stopped making progress for three minutes. Try another source."
                )
                return
            }
            delay(1_000L)
        }

        _streamState.value = TorrentStreamState.Ready(videoPath)
        updateNotification("Playing from protected buffer")
    }

    override fun onStreamProgress(torrent: Torrent?, status: StreamStatus?) {
        status ?: return
        _downloadProgress.value = status.progress
        _downloadSpeed.value = status.downloadSpeed
        _seeds.value = status.seeds
        speedSamples.addLast(status.downloadSpeed)
        while (speedSamples.size > SPEED_SAMPLE_WINDOW) speedSamples.removeFirst()
        serviceScope.launch { manageCacheSize() }

        val speedMbps = status.downloadSpeed / BYTES_PER_MB
        updateNotification(
            "Streaming: ${status.progress.toInt()}% (${speedMbps} MB/s) " +
                "Cache: ${_cacheUsageMB.value} MB"
        )
    }

    override fun onStreamStopped() {
        _streamState.value = TorrentStreamState.Idle
        currentTorrent = null
        currentMagnetUrl = null
        currentMagnetHash = null
    }

    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        android.util.Log.e(TAG, "Stream error: ${e?.message}", e)
        _streamState.value = TorrentStreamState.Error(e?.message ?: "Unknown torrent error")
        updateNotification("Torrent error: ${e?.message ?: "unknown"}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Torrent Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Torrent download and resumable playback status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("OpenStream+ playback")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun updateNotification(status: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationMs < 5_000L) return
        lastNotificationMs = now
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(status))
    }
}

sealed class TorrentStreamState {
    object Idle : TorrentStreamState()
    object Connecting : TorrentStreamState()
    data class Buffering(val progress: Float) : TorrentStreamState()
    data class Streaming(val progress: Float) : TorrentStreamState()
    data class PreBuffering(val videoPath: String, val bufferProgress: Float) : TorrentStreamState()
    data class Ready(val videoPath: String) : TorrentStreamState()
    data class Error(val message: String) : TorrentStreamState()
}
