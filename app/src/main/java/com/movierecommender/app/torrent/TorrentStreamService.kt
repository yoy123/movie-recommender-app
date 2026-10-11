package com.movierecommender.app.torrent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import org.libtorrent4j.Priority
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service that downloads the active torrent into a single resumable cache and exposes
 * the local video file to Media3. Playback starts when the torrent library finishes preparing its
 * initial pieces; Media3 then waits for individual pieces on demand.
 */
class TorrentStreamService : Service(), TorrentListener {

    companion object {
        private const val TAG = "TorrentStreamService"
        private const val NOTIFICATION_CHANNEL_ID = "torrent_streaming"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_CLEAR_CACHE = "com.movierecommender.app.torrent.CLEAR_CACHE"
        private const val METADATA_TIMEOUT_MS = 60_000L
        private const val NO_PROGRESS_TIMEOUT_MS = 180_000L
        private const val PRIORITY_LOOKAHEAD_MS = 15L * 60L * 1000L
        private const val BYTES_PER_MB = 1024L * 1024L

        private fun allocatedFileBytes(file: File): Long = runCatching {
            Os.stat(file.absolutePath).st_blocks * 512L
        }.getOrElse { file.length() }

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
                    .sumOf { allocatedFileBytes(it) } / BYTES_PER_MB
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

        /** Cache budget (MB) this device can safely use right now for torrent streaming. */
        fun safeCacheBudgetMb(context: Context): Int =
            calculateDynamicCacheSize(TorrentCachePolicy.cacheDirectory(context))

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
    private val torrentLifecycleMutex = Mutex()
    private val streamRequestGeneration = AtomicLong(0L)
    private val failureInProgress = AtomicBoolean(false)
    private val attemptedSourceKeys = linkedSetOf<String>()
    private var retentionJob: Job? = null
    private var metadataTimeoutJob: Job? = null
    private var preparationTimeoutJob: Job? = null
    private val startupProgressWatchdog = TorrentProgressWatchdog(NO_PROGRESS_TIMEOUT_MS)
    private var swarmPollJob: Job? = null
    private var torrentStream: TorrentStream? = null
    @Volatile
    private var sourceQueueAnchor: String? = null
    @Volatile
    private var currentTorrent: Torrent? = null
    @Volatile
    private var currentMagnetUrl: String? = null
    private var currentMagnetHash: Int? = null
    @Volatile
    private var currentVideoPath: String? = null
    @Volatile
    private var currentVideoFileSize = 0L
    @Volatile
    private var currentVideoFileOffset = 0L
    @Volatile
    private var currentSourceId = 0L
    private var requestedPlaybackPosition = 0L
    @Volatile
    private var currentPlaybackPosition = 0L
    @Volatile
    private var currentPlaybackDuration = 0L
    private var cacheDir: File? = null
    private var isDownloadPaused = false
    private var lastCacheCheckMs = 0L
    private var lastNotificationMs = 0L
    private var lastPriorityUpdateMs = 0L
    private var dynamicMaxCacheSizeMB = 0

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
        // Native libtorrent init and disk scans happen on the service's IO dispatcher at first use.
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Preparing torrent session..."))

        if (intent?.action == ACTION_CLEAR_CACHE) {
            streamRequestGeneration.incrementAndGet()
            clearSourceSession()
            serviceScope.launch {
                torrentLifecycleMutex.withLock {
                    stopStreamInternal()
                    TorrentCachePolicy.clearCacheAndPlayback(
                        applicationContext,
                        "app closed or explicit cleanup"
                    )
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Called when the user removes/exits the app task. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        streamRequestGeneration.incrementAndGet()
        clearSourceSession()
        serviceScope.launch {
            torrentLifecycleMutex.withLock {
                stopStreamInternal()
                TorrentCachePolicy.clearCacheAndPlayback(applicationContext, "app task closed")
            }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        streamRequestGeneration.incrementAndGet()
        retentionJob?.cancel()
        metadataTimeoutJob?.cancel()
        preparationTimeoutJob?.cancel()
        val streamToStop = torrentStream
        runCatching { streamToStop?.removeListener(this) }
        torrentStream = null
        currentTorrent = null
        clearSourceSession()
        currentVideoPath = null
        currentVideoFileSize = 0L
        serviceScope.cancel()
        // TorrentStream.stopStream() can wait in native libtorrent. Never hold Android's main
        // thread during service teardown; the Firestick ANR trace showed this call blocking there.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { streamToStop?.stopStream() }
                .onFailure { android.util.Log.w(TAG, "Failed to stop torrent during teardown", it) }
        }
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
        val requestGeneration = streamRequestGeneration.incrementAndGet()
        val isNewSource = currentMagnetUrl != preparedMagnetUrl
        android.util.Log.i(
            TAG,
            "Queued torrent request generation=$requestGeneration source=${sourceLabel(preparedMagnetUrl)}"
        )
        if (isNewSource) {
            beginSourceSession(magnetUrl)
            _streamState.value = TorrentStreamState.Connecting
        }
        serviceScope.launch {
            torrentLifecycleMutex.withLock {
                if (requestGeneration != streamRequestGeneration.get()) return@withLock
                startStreamInternal(
                    magnetUrl,
                    preparedMagnetUrl,
                    resumePositionMs,
                    knownDurationMs,
                    requestGeneration
                )
            }
        }
    }

    private suspend fun startStreamInternal(
        magnetUrl: String,
        preparedMagnetUrl: String,
        resumePositionMs: Long,
        knownDurationMs: Long,
        requestGeneration: Long
    ) {
        TorrentCachePolicy.initialize(applicationContext)
        if (torrentStream == null) initTorrentStream()
        // Playback preferences are keyed by the original URL supplied by the player.
        val magnetHash = synchronized(attemptedSourceKeys) {
            sourceQueueAnchor?.hashCode() ?: magnetUrl.hashCode()
        }
        retentionJob?.cancel()
        requestedPlaybackPosition = resumePositionMs.coerceAtLeast(0L)
        currentPlaybackPosition = requestedPlaybackPosition
        if (knownDurationMs > 0L) currentPlaybackDuration = knownDurationMs

        if (currentMagnetUrl == preparedMagnetUrl && currentTorrent != null) {
            // An earlier start request may have scheduled the retention alarm. Cancel both the
            // persisted expiry and its in-process job before reusing downloaded data.
            TorrentCachePolicy.markActive(applicationContext, magnetHash)
            resumeDownloadInternal()
            prioritizeFirstMissingPiece(requestedPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
            when (_streamState.value) {
                is TorrentStreamState.Ready, is TorrentStreamState.Streaming -> {
                    currentVideoPath?.let { path ->
                        _streamState.value = TorrentStreamState.Ready(path, currentSourceId)
                    }
                }
                is TorrentStreamState.Connecting -> scheduleMetadataTimeout(requestGeneration)
                else -> {
                    // A download is allowed to run as long as its pieces keep advancing.
                    if (_streamState.value is TorrentStreamState.Buffering) {
                        currentTorrent?.let { resetPreparationTimeout(it) }
                    }
                }
            }
            updateNotification("Resuming cached playback...")
            return
        }

        // If the magnet matches but metadata never produced a Torrent object, a previous
        // attempt may be wedged. Stop it and actually retry instead of returning early.
        if (currentMagnetUrl != null) {
            stopStreamInternal()
            TorrentCachePolicy.clearCacheAndPlayback(applicationContext, "switching torrents")
            ensureCacheDirectory()
            dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
            if (requestGeneration != streamRequestGeneration.get()) return
        }

        TorrentCachePolicy.markActive(applicationContext, magnetHash)
        currentMagnetUrl = preparedMagnetUrl
        currentMagnetHash = magnetHash
        currentSourceId = requestGeneration
        currentVideoPath = null
        currentVideoFileSize = 0L
        currentVideoFileOffset = 0L
        _streamState.value = TorrentStreamState.Connecting
        _downloadProgress.value = 0f
        failureInProgress.set(false)
        scheduleMetadataTimeout(requestGeneration)
        android.util.Log.i(
            TAG,
            "Starting torrent request generation=$requestGeneration source=${sourceLabel(preparedMagnetUrl)}"
        )
        runCatching { torrentStream?.startStream(preparedMagnetUrl) }
            .onFailure { failPlaybackAndClearCache(it.message ?: "Unable to start torrent") }
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
                streamRequestGeneration.incrementAndGet()
                torrentLifecycleMutex.withLock {
                    stopStreamInternal()
                    TorrentCachePolicy.clearCacheAndPlayback(
                        applicationContext,
                        "60 minutes without returning to playback"
                    )
                }
                stopSelf()
            }
        }
    }

    /** Stops network activity but deliberately leaves cache ownership to TorrentCachePolicy. */
    /** Player-level permanent errors and exhausted rebuffer waits use the same failover queue. */
    fun reportPlaybackFailure(message: String, expectedSourceId: Long) {
        // Old ExoPlayer callbacks must never fail the replacement source.
        if (currentMagnetUrl != null && currentSourceId == expectedSourceId) {
            failPlaybackAndClearCache(message)
        }
    }

    /** Verifies that a libtorrent piece is present, using offsets within the selected file. */
    fun hasDownloadedByte(videoPath: String, offset: Long): Boolean {
        if (currentVideoPath != videoPath || offset < 0L || offset >= currentVideoFileSize) return false
        return hasBytesAt(offset)
    }

    fun getTorrentPieceLength(videoPath: String): Long {
        if (currentVideoPath != videoPath) return 0L
        return runCatching {
            currentTorrent?.torrentHandle?.torrentFile()?.pieceLength()?.toLong() ?: 0L
        }.getOrDefault(0L)
    }

    /** Called by Media3's worker thread when it encounters a missing piece. */
    fun requestTorrentByte(videoPath: String, offset: Long) {
        if (videoPath != currentVideoPath) return
        serviceScope.launch {
            torrentLifecycleMutex.withLock {
                if (videoPath == currentVideoPath && offset >= 0L) {
                    prioritizeByteOffset(offset)
                }
            }
        }
    }

    fun stopStream() {
        streamRequestGeneration.incrementAndGet()
        clearSourceSession()
        serviceScope.launch {
            torrentLifecycleMutex.withLock { stopStreamInternal() }
        }
    }

    private suspend fun stopStreamInternal(resetState: Boolean = true) {
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = null
        preparationTimeoutJob?.cancel()
        preparationTimeoutJob = null
        swarmPollJob?.cancel()
        swarmPollJob = null
        currentTorrent = null
        currentMagnetUrl = null
        currentMagnetHash = null
        currentVideoPath = null
        currentVideoFileSize = 0L
        currentVideoFileOffset = 0L
        currentSourceId = 0L
        isDownloadPaused = false
        if (resetState) _streamState.value = TorrentStreamState.Idle
        _downloadProgress.value = 0f
        _downloadSpeed.value = 0
        _seeds.value = 0
        val stopStartedAt = System.currentTimeMillis()
        android.util.Log.i(TAG, "Stopping native torrent session")
        runCatching { torrentStream?.stopStream() }
            .onFailure { android.util.Log.w(TAG, "Failed to stop torrent", it) }
        android.util.Log.i(
            TAG,
            "Native torrent session stopped in ${System.currentTimeMillis() - stopStartedAt}ms"
        )
    }

    /** Stops the failed source and advances to the next registered candidate when available. */
    private fun failPlaybackAndClearCache(message: String, expectedGeneration: Long? = null) {
        if (expectedGeneration != null && expectedGeneration != streamRequestGeneration.get()) return
        if (!failureInProgress.compareAndSet(false, true)) return
        val recoveryGeneration = streamRequestGeneration.incrementAndGet()
        val fallback = claimNextSource()
        requestedPlaybackPosition = currentPlaybackPosition.coerceAtLeast(0L)
        retentionJob?.cancel()
        metadataTimeoutJob?.cancel()
        preparationTimeoutJob?.cancel()
        swarmPollJob?.cancel()
        // Null the active session synchronously so a racing RETRY is treated as a fresh start.
        currentTorrent = null
        currentMagnetUrl = null
        currentMagnetHash = null
        currentVideoPath = null
        currentVideoFileSize = 0L
        currentVideoFileOffset = 0L
        currentSourceId = 0L
        isDownloadPaused = false
        _downloadProgress.value = 0f
        _downloadSpeed.value = 0
        _seeds.value = 0
        _streamState.value = if (fallback != null) {
            TorrentStreamState.SwitchingSource(
                attempt = fallback.attempt,
                total = fallback.total,
                failedReason = message
            )
        } else {
            val attemptedCount = synchronized(attemptedSourceKeys) {
                attemptedSourceKeys.size
            }
            TorrentStreamState.Error(
                if (attemptedCount > 1) {
                    "All $attemptedCount torrent sources failed. Last error: $message"
                } else {
                    message
                }
            )
        }
        serviceScope.launch {
            torrentLifecycleMutex.withLock {
                stopStreamInternal(resetState = false)
                TorrentCachePolicy.clearCacheAndPlayback(
                    applicationContext,
                    "failed playback: $message",
                    clearPlaybackState = fallback == null
                )
                ensureCacheDirectory()
                dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
                if (
                    fallback != null &&
                    recoveryGeneration == streamRequestGeneration.get()
                ) {
                    android.util.Log.w(
                        TAG,
                        "Source failed ($message); trying ${fallback.attempt}/${fallback.total} " +
                            "provider=${fallback.source.provider ?: "unknown"} " +
                            "quality=${fallback.source.quality ?: "unknown"}"
                    )
                    updateNotification(
                        "Trying torrent source ${fallback.attempt} of ${fallback.total}"
                    )
                    startStreamInternal(
                        magnetUrl = fallback.source.url,
                        preparedMagnetUrl = TorrentMagnetUtils.enrichMagnetUrl(fallback.source.url),
                        resumePositionMs = requestedPlaybackPosition,
                        knownDurationMs = currentPlaybackDuration,
                        requestGeneration = recoveryGeneration
                    )
                }
            }
        }
    }

    private fun beginSourceSession(selectedUrl: String) {
        synchronized(attemptedSourceKeys) {
            sourceQueueAnchor = selectedUrl
            attemptedSourceKeys.clear()
            attemptedSourceKeys += TorrentSourceRegistry.sourceKey(selectedUrl)
        }
        failureInProgress.set(false)
    }

    private fun clearSourceSession() {
        synchronized(attemptedSourceKeys) {
            sourceQueueAnchor = null
            attemptedSourceKeys.clear()
        }
        failureInProgress.set(false)
    }

    private fun claimNextSource(): FallbackDecision? = synchronized(attemptedSourceKeys) {
        val anchor = sourceQueueAnchor ?: return@synchronized null
        val candidates = TorrentSourceRegistry.sourcesFor(anchor)
        val next = candidates.firstOrNull { source ->
            TorrentSourceRegistry.sourceKey(source.url) !in attemptedSourceKeys
        } ?: return@synchronized null
        attemptedSourceKeys += TorrentSourceRegistry.sourceKey(next.url)
        FallbackDecision(
            source = next,
            attempt = attemptedSourceKeys.size,
            total = maxOf(candidates.size, attemptedSourceKeys.size)
        )
    }

    fun getVideoFilePath(): String? = currentVideoPath

    fun getVideoFileSize(): Long = currentVideoFileSize

    fun getActiveSourceId(): Long = currentSourceId

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
            serviceScope.launch {
                torrentLifecycleMutex.withLock {
                    prioritizeFirstMissingPiece(currentPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
                }
            }
        }
        serviceScope.launch { manageCacheSize() }
    }

    private fun hasBytesAt(byteOffset: Long): Boolean {
        val torrent = currentTorrent ?: return false
        if (byteOffset < 0L || byteOffset >= currentVideoFileSize) return false
        return runCatching {
            val handle = torrent.torrentHandle
            val pieceLength = handle.torrentFile().pieceLength().toLong()
            if (pieceLength <= 0L) return@runCatching false
            // Selected video may begin partway through a piece in a season pack.
            // Torrent.hasBytes(byteOffset) omits this file's global offset.
            handle.havePiece(((currentVideoFileOffset + byteOffset) / pieceLength).toInt())
        }.getOrDefault(false)
    }

    suspend fun hasBytesAtTime(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        torrentLifecycleMutex.withLock {
            val durationMs = currentPlaybackDuration
            val fileSize = currentVideoFileSize
            if (durationMs <= 0L || fileSize <= 0L) return@withLock false
            val byteOffset = timeToByteOffset(positionMs, durationMs, fileSize)
            hasBytesAt(byteOffset)
        }
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
            // Library auto-download fires before onStreamPrepared, racing file selection and state tracking.
            .autoDownload(false)
            .build()
        torrentStream = TorrentStream.init(options)
        torrentStream?.addListener(this)
    }

    private fun ensureCacheDirectory() {
        // TorrentStreamOptions.saveLocation is fixed at initialization. Do not silently
        // switch cache roots while its native torrent session still writes to the old root.
        val directory = cacheDir ?: TorrentCachePolicy.cacheDirectory(applicationContext)
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
        val availableMb = runCatching {
            StatFs(directory.absolutePath).availableBytes / BYTES_PER_MB
        }.getOrDefault(0L)
        if (!isDownloadPaused && _downloadProgress.value < 100f &&
            availableMb < TorrentBufferPolicy.RESERVED_DEVICE_SPACE_MB
        ) {
            failPlaybackAndClearCache(
                "The device has less than 500 MB free during download. Choose a smaller source."
            )
            return
        }
        if (dynamicMaxCacheSizeMB > 0 && cacheSizeMb > dynamicMaxCacheSizeMB && !isDownloadPaused) {
            failPlaybackAndClearCache(
                "The Fire TV storage reserve was reached. Free storage or choose a smaller source."
            )
        }
    }

    private fun getCacheSizeInMb(directory: File): Int =
        (directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { allocatedFileBytes(it) } / BYTES_PER_MB).toInt()

    private fun timeToByteOffset(positionMs: Long, durationMs: Long, fileSize: Long): Long {
        if (durationMs <= 0L || fileSize <= 0L) return 0L
        return (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs.toDouble() * fileSize)
            .toLong()
            .coerceIn(0L, fileSize - 1L)
    }

    private fun contiguousAvailableBytes(startByte: Long, maximumBytes: Long): Long {
        val torrent = currentTorrent ?: return 0L
        val fileSize = currentVideoFileSize
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
            if (!hasBytesAt(offset)) break
            val positionInPiece = (currentVideoFileOffset + offset) % pieceLength
            available += pieceLength - positionInPiece
        }
        return available.coerceAtMost(boundedMaximum)
    }

    private fun prioritizeByteOffset(byteOffset: Long) {
        val torrent = currentTorrent ?: return
        if (byteOffset < 0 || byteOffset >= currentVideoFileSize) return
        runCatching {
            val handle = torrent.torrentHandle
            val info = handle.torrentFile()
            val pieceLength = info.pieceLength().toLong()
            if (pieceLength <= 0L) return@runCatching
            val first = ((currentVideoFileOffset + byteOffset) / pieceLength).toInt()
            // Request several physically adjacent pieces without guessing selected-file offsets.
            for (index in first until minOf(first + 5, info.numPieces())) {
                if (!handle.havePiece(index)) {
                    handle.piecePriority(index, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(index, 1_000)
                }
            }
        }.onFailure { android.util.Log.w(TAG, "Could not prioritize torrent piece", it) }
    }

    private fun prioritizeFirstMissingPiece(positionMs: Long, lookAheadMs: Long) {
        if (currentTorrent == null) return
        val durationMs = currentPlaybackDuration
        val fileSize = currentVideoFileSize
        if (durationMs <= 0L || fileSize <= 0L) return

        val startByte = timeToByteOffset(positionMs, durationMs, fileSize)
        val endPositionMs = (positionMs + lookAheadMs).coerceAtMost(durationMs)
        val endByte = timeToByteOffset(endPositionMs, durationMs, fileSize)
        val maximumAhead = (endByte - startByte).coerceAtLeast(0L)
        val contiguous = contiguousAvailableBytes(startByte, maximumAhead)
        val firstMissing = (startByte + contiguous).coerceAtMost(fileSize - 1L)
        prioritizeByteOffset(firstMissing)
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

    private fun scheduleMetadataTimeout(requestGeneration: Long) {
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = serviceScope.launch {
            delay(METADATA_TIMEOUT_MS)
            if (
                requestGeneration == streamRequestGeneration.get() &&
                _streamState.value is TorrentStreamState.Connecting
            ) {
                failPlaybackAndClearCache(
                    "Torrent metadata did not load within 60 seconds.",
                    expectedGeneration = requestGeneration
                )
            }
        }
    }

    private fun resetPreparationTimeout(torrent: Torrent) {
        // A fresh piece, rather than elapsed startup time, controls when a fallback is allowed.
        // Use the monotonic system clock so user/device clock changes cannot trip the watchdog.
        startupProgressWatchdog.onActivity(SystemClock.elapsedRealtime())
        if (preparationTimeoutJob?.isActive == true) return
        preparationTimeoutJob = serviceScope.launch {
            while (torrent === currentTorrent && _streamState.value is TorrentStreamState.Buffering) {
                delay(2_000L)
                if (torrent === currentTorrent &&
                    _streamState.value is TorrentStreamState.Buffering &&
                    startupProgressWatchdog.isStalled(SystemClock.elapsedRealtime())
                ) {
                    failPlaybackAndClearCache(
                        "The torrent stopped downloading its startup pieces for three minutes. " +
                            "Try another source."
                    )
                    break
                }
            }
        }
    }

    private fun sourceLabel(url: String): String = runCatching {
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "magnet" -> "magnet"
            "http", "https" -> "${uri.scheme}://${uri.host ?: "unknown-host"}"
            "file" -> "local-file"
            else -> uri.scheme ?: "unknown"
        }
    }.getOrDefault("unknown")

    override fun onStreamPrepared(torrent: Torrent?) {
        val preparedTorrent = torrent ?: return
        serviceScope.launch {
            torrentLifecycleMutex.withLock {
                if (torrentStream?.currentTorrent !== preparedTorrent || currentMagnetUrl == null) {
                    return@withLock
                }
                metadataTimeoutJob?.cancel()
                metadataTimeoutJob = null
                currentTorrent = preparedTorrent
                android.util.Log.i(TAG, "Torrent metadata prepared")
                val selectedFileIndex = currentMagnetUrl
                    ?.let(TorrentMagnetUtils::selectedFileIndex)
                val files = preparedTorrent.torrentHandle.torrentFile().files()
                val candidates = (0 until files.numFiles()).map { index ->
                    files.fileName(index) to files.fileSize(index)
                }
                val chosenIndex = TorrentFilePolicy.select(candidates, selectedFileIndex)
                if (chosenIndex == null) {
                    failPlaybackAndClearCache(
                        "Torrent metadata has no playable video at the selected file index."
                    )
                    return@withLock
                }
                preparedTorrent.setSelectedFileIndex(chosenIndex)
                val videoFile = preparedTorrent.videoFile
                val metadataFileSize = files.fileSize(chosenIndex)
                // Use torrent metadata, not the current (possibly sparse) on-disk length.
                currentVideoPath = videoFile.absolutePath
                currentVideoFileSize = metadataFileSize
                currentVideoFileOffset = files.fileOffset(chosenIndex)
                dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
                val requiredStorageMb = TorrentBufferPolicy.requiredStorageMb(metadataFileSize)
                if (dynamicMaxCacheSizeMB <= 0 || requiredStorageMb > dynamicMaxCacheSizeMB) {
                    failPlaybackAndClearCache(
                        "This source needs $requiredStorageMb MB, but only " +
                            "$dynamicMaxCacheSizeMB MB of safe cache is available."
                    )
                    return@withLock
                }
                runCatching { preparedTorrent.startDownload() }
                    .onFailure { failPlaybackAndClearCache(it.message ?: "Torrent download failed") }
                if (currentPlaybackDuration > 0L && requestedPlaybackPosition > 0L) {
                    prioritizeFirstMissingPiece(requestedPlaybackPosition, PRIORITY_LOOKAHEAD_MS)
                }
            }
        }
    }

    override fun onStreamStarted(torrent: Torrent?) {
        val startedTorrent = torrent ?: return
        if (startedTorrent !== currentTorrent && startedTorrent !== torrentStream?.currentTorrent) return
        _streamState.value = TorrentStreamState.Buffering(0f)
        resetPreparationTimeout(startedTorrent)
        startSwarmPolling(startedTorrent)
        android.util.Log.i(TAG, "Downloading initial torrent pieces")
        updateNotification("Downloading startup pieces...")
    }

    // The library reports nothing until a whole block completes, so read libtorrent status directly.
    private fun startSwarmPolling(torrent: Torrent) {
        swarmPollJob?.cancel()
        swarmPollJob = serviceScope.launch {
            var lastDone = -1L
            var tick = 0
            while (torrent === currentTorrent && _streamState.value is TorrentStreamState.Buffering) {
                val status = runCatching { torrent.torrentHandle.status() }.getOrNull()
                if (status != null) {
                    val done = status.totalWantedDone()
                    _seeds.value = status.numSeeds()
                    _downloadSpeed.value = status.downloadPayloadRate()
                    if (done > lastDone) {
                        if (lastDone >= 0L) resetPreparationTimeout(torrent)
                        lastDone = done
                    }
                    if (tick % 5 == 0) {
                        android.util.Log.i(
                            TAG,
                            "Startup swarm: peers=${status.numPeers()} seeds=${status.numSeeds()} " +
                                "speed=${status.downloadPayloadRate() / 1024}KB/s " +
                                "done=${done / BYTES_PER_MB}MB"
                        )
                    }
                }
                tick++
                delay(2_000L)
            }
        }
    }

    override fun onStreamReady(torrent: Torrent?) {
        val readyTorrent = torrent ?: return
        serviceScope.launch { handleStreamReady(readyTorrent) }
    }

    private suspend fun handleStreamReady(readyTorrent: Torrent) {
        val video = torrentLifecycleMutex.withLock {
            if (readyTorrent !== currentTorrent) return@withLock null
            // An obsolete ready callback must not disable the new source's idle watchdog.
            preparationTimeoutJob?.cancel()
            preparationTimeoutJob = null
            val videoFile = readyTorrent.videoFile
            val videoPath = videoFile.absolutePath
            val fileSize = currentVideoFileSize.takeIf { it > 0L } ?: videoFile.length()
            currentVideoPath = videoPath
            currentVideoFileSize = fileSize
            if (fileSize <= 0L) {
                failPlaybackAndClearCache("The torrent video file has no readable size.")
                return@withLock null
            }
            if (currentPlaybackDuration <= 0L) {
                currentPlaybackDuration = readDurationMs(videoPath)
            }

            dynamicMaxCacheSizeMB = calculateDynamicCacheSize(requireNotNull(cacheDir))
            val requiredStorageMb = TorrentBufferPolicy.requiredStorageMb(fileSize)
            if (dynamicMaxCacheSizeMB <= 0 || requiredStorageMb > dynamicMaxCacheSizeMB) {
                failPlaybackAndClearCache(
                    "This source needs ${requiredStorageMb} MB of cache, but only " +
                        "${dynamicMaxCacheSizeMB.coerceAtLeast(0)} MB is safely available. " +
                        "Free storage or choose a smaller source."
                )
                return@withLock null
            }
            videoPath to fileSize
        } ?: return

        // The native library has already completed its startup preparation (15 MiB).
        // Do not make the viewer wait for a second 64–256 MiB gate. The piece-aware Media3
        // DataSource handles individual missing pieces and seeks as playback proceeds.
        if (readyTorrent !== currentTorrent) return
        _streamState.value = TorrentStreamState.Ready(video.first, currentSourceId)
        android.util.Log.i(TAG, "Initial torrent pieces ready; handing video to Media3")
        updateNotification("Initial pieces ready; starting playback")
    }

    override fun onStreamProgress(torrent: Torrent?, status: StreamStatus?) {
        val progressTorrent = torrent ?: return
        status ?: return
        if (progressTorrent !== currentTorrent) return
        val previousOverallProgress = _downloadProgress.value
        _downloadProgress.value = status.progress
        _downloadSpeed.value = status.downloadSpeed
        _seeds.value = status.seeds
        if (_streamState.value is TorrentStreamState.Buffering) {
            val previousProgress = (_streamState.value as TorrentStreamState.Buffering).progress
            if (status.bufferProgress > previousProgress || status.progress > previousOverallProgress) {
                resetPreparationTimeout(progressTorrent)
            }
            _streamState.value = TorrentStreamState.Buffering(
                status.bufferProgress.toFloat().coerceIn(0f, 100f)
            )
        }
        serviceScope.launch { manageCacheSize() }

        val speedMbps = status.downloadSpeed / BYTES_PER_MB
        updateNotification(
            "Streaming: ${status.progress.toInt()}% (${speedMbps} MB/s) " +
                "Cache: ${_cacheUsageMB.value} MB"
        )
    }

    override fun onStreamStopped() {
        // stopStreamInternal owns state cleanup. TorrentStream dispatches this callback on the
        // main thread after stopStream() returns, so mutating state here can erase a newer session.
        android.util.Log.d(TAG, "Torrent library reported stream stopped")
    }

    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        // Null-torrent library errors cannot be matched to a source generation.
        // Let its metadata/startup watchdog handle the failure instead of
        // accidentally terminating a newer session after an asynchronous stop.
        if (torrent == null || torrent !== currentTorrent) return
        android.util.Log.e(TAG, "Stream error: ${e?.message}", e)
        updateNotification("Torrent error: ${e?.message ?: "unknown"}")
        failPlaybackAndClearCache(e?.message ?: "Unknown torrent error")
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
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        lastNotificationMs = now
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(status))
    }
}

sealed class TorrentStreamState {
    object Idle : TorrentStreamState()
    object Connecting : TorrentStreamState()
    data class Buffering(val progress: Float) : TorrentStreamState()
    data class SwitchingSource(
        val attempt: Int,
        val total: Int,
        val failedReason: String
    ) : TorrentStreamState()
    data class Streaming(val progress: Float) : TorrentStreamState()
    data class Ready(val videoPath: String, val sourceId: Long = 0L) : TorrentStreamState()
    data class Error(val message: String) : TorrentStreamState()
}

private data class FallbackDecision(
    val source: TorrentPlaybackSource,
    val attempt: Int,
    val total: Int
)
