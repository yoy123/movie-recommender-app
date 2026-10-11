package com.movierecommender.app.torrent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the lifetime of the single active torrent cache.
 *
 * The cache is retained only after leaving the player and expires after 60 minutes. It is also
 * removed on a device reboot, explicit app exit/task removal, or when a different torrent starts.
 */
object TorrentCachePolicy {
    const val RETENTION_MS = 60L * 60L * 1000L
    const val CACHE_DIRECTORY_NAME = "torrent_stream"
    const val ACTION_EXPIRE_CACHE = "com.movierecommender.app.torrent.EXPIRE_CACHE"

    private const val TAG = "TorrentCachePolicy"
    private const val SESSION_PREFS = "torrent_cache_session"
    private const val PLAYBACK_PREFS = "movie_playback"
    private const val KEY_ACTIVE_MAGNET_HASH = "active_magnet_hash"
    private const val KEY_RETAIN_UNTIL = "retain_until"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_PROCESS_ACTIVE = "process_active"
    private const val EXPIRY_REQUEST_CODE = 9102
    private const val STORAGE_ROOT = "/storage"

    @Volatile
    private var initializedThisProcess = false

    @Volatile
    private var lastCacheSelectionSnapshot: String? = null

    fun cacheDirectory(context: Context): File {
        val appContext = context.applicationContext
        val internalFallback = File(appContext.cacheDir, CACHE_DIRECTORY_NAME)
        val evaluated = cacheDirectories(appContext)
            .map { candidate -> candidate to safeCacheLimitMb(candidate) }

        val selected = evaluated
            .maxByOrNull { (_, limitMb) -> limitMb }
            ?.takeIf { (_, limitMb) -> limitMb >= 0 }
            ?.first
            ?: internalFallback

        maybeLogCacheSelection(selected, evaluated)
        return selected
    }

    fun cacheDirectories(context: Context): List<File> {
        val appContext = context.applicationContext
        val externalFiles = appContext.getExternalFilesDirs(null)
            .filterNotNull()
            .map { File(it, CACHE_DIRECTORY_NAME) }

        val externalCache = appContext.externalCacheDirs
            .filterNotNull()
            .map { File(it, CACHE_DIRECTORY_NAME) }

        val storageRoots = storageRoots(appContext)
        val packageStorageCandidates = storageRoots.flatMap { root ->
            listOf(
                File(root, "Android/data/${appContext.packageName}/files/$CACHE_DIRECTORY_NAME"),
                File(root, "Android/data/${appContext.packageName}/cache/$CACHE_DIRECTORY_NAME")
            )
        }

        return (externalFiles + externalCache + packageStorageCandidates + File(appContext.cacheDir, CACHE_DIRECTORY_NAME))
            .distinctBy { it.absolutePath }
    }

    /**
     * Clears cache left by a reboot, an expired retention window, or a previous process that ended
     * without the normal task-removal callback (for example Force stop or process termination).
     */
    @Synchronized
    fun initialize(context: Context) {
        if (initializedThisProcess) return
        initializedThisProcess = true

        val appContext = context.applicationContext
        cacheDirectory(appContext)
        val prefs = appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val currentBootCount = readBootCount(appContext)
        val storedBootCount = prefs.getInt(KEY_BOOT_COUNT, Int.MIN_VALUE)
        val retainUntil = prefs.getLong(KEY_RETAIN_UNTIL, 0L)
        val rebooted = storedBootCount != Int.MIN_VALUE && storedBootCount != currentBootCount
        val expired = retainUntil > 0L && System.currentTimeMillis() >= retainUntil
        val previousProcessEnded = prefs.getBoolean(KEY_PROCESS_ACTIVE, false)

        when {
            rebooted -> clearCacheAndPlayback(appContext, "device reboot detected")
            expired -> clearCacheAndPlayback(appContext, "60-minute retention expired")
            previousProcessEnded -> clearCacheAndPlayback(appContext, "previous app process ended")
        }

        appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BOOT_COUNT, currentBootCount)
            .putBoolean(KEY_PROCESS_ACTIVE, true)
            .apply()
    }

    @Synchronized
    fun markActive(context: Context, magnetHash: Int) {
        val appContext = context.applicationContext
        cancelExpiry(appContext)
        appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVE_MAGNET_HASH, magnetHash)
            .putLong(KEY_RETAIN_UNTIL, 0L)
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .putBoolean(KEY_PROCESS_ACTIVE, true)
            .apply()
    }

    @Synchronized
    fun retainForResume(context: Context, magnetHash: Int): Long {
        val appContext = context.applicationContext
        val retainUntil = System.currentTimeMillis() + RETENTION_MS
        appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVE_MAGNET_HASH, magnetHash)
            .putLong(KEY_RETAIN_UNTIL, retainUntil)
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .putBoolean(KEY_PROCESS_ACTIVE, true)
            .apply()
        scheduleExpiry(appContext)
        return retainUntil
    }

    @Synchronized
    fun isExpired(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val retainUntil = context.applicationContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RETAIN_UNTIL, 0L)
        return retainUntil > 0L && nowMs >= retainUntil
    }

    /** Check and delete atomically with markActive so an old alarm cannot erase resumed playback. */
    @Synchronized
    fun clearExpiredCache(context: Context) {
        if (isExpired(context)) {
            clearCacheAndPlayback(context, "60-minute inactivity alarm")
        }
    }

    @Synchronized
    fun clearCacheAndPlayback(
        context: Context,
        reason: String,
        clearPlaybackState: Boolean = true
    ) {
        val appContext = context.applicationContext
        cancelExpiry(appContext)
        val sessionPrefs = appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val activeHash = sessionPrefs.getInt(KEY_ACTIVE_MAGNET_HASH, Int.MIN_VALUE)

        cacheDirectories(appContext).forEach { cacheDir ->
            if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
                Log.w(TAG, "Some torrent cache files could not be deleted: $reason")
            }
        }

        if (clearPlaybackState && activeHash != Int.MIN_VALUE) {
            appContext.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("position_$activeHash")
                .remove("duration_$activeHash")
                .apply()
        }

        sessionPrefs.edit()
            .clear()
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .putBoolean(KEY_PROCESS_ACTIVE, false)
            .commit()
        Log.i(TAG, "Torrent cache cleared: $reason")
    }

    fun cacheSizeBytes(context: Context): Long {
        return cacheDirectories(context).sumOf { cacheDir ->
            if (!cacheDir.exists()) {
                0L
            } else {
                cacheDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { file ->
                        runCatching { Os.stat(file.absolutePath).st_blocks * 512L }
                            .getOrElse { file.length() }
                    }
            }
            }
    }


    private fun safeCacheLimitMb(directory: File): Int {
        return runCatching {
            if (!directory.exists()) directory.mkdirs()
            if (!directory.canWrite()) return@runCatching -1
            val statFs = StatFs(directory.absolutePath)
            val freeSpaceMb = statFs.availableBytes / BYTES_PER_MB
            val existingCacheMb = allocatedBytes(directory) / BYTES_PER_MB
            TorrentBufferPolicy.calculateCacheLimitMb(freeSpaceMb, existingCacheMb)
        }.getOrDefault(-1)
    }

    private fun allocatedBytes(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { file ->
                runCatching { Os.stat(file.absolutePath).st_blocks * 512L }
                    .getOrElse { file.length() }
            }
    }

    private fun storageRoots(context: Context): List<File> {
        val volumeRoots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.getSystemService(StorageManager::class.java)
                    ?.storageVolumes
                    ?.mapNotNull { volume -> volume.directory }
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val fallbackRoots = runCatching {
            File(STORAGE_ROOT).listFiles()
                ?.filter { file -> file.isDirectory && file.name != "self" }
                .orEmpty()
        }.getOrDefault(emptyList())

        return (volumeRoots + fallbackRoots)
            .distinctBy { it.absolutePath }
    }

    private fun maybeLogCacheSelection(selected: File, evaluated: List<Pair<File, Int>>) {
        val snapshot = evaluated
            .sortedByDescending { (_, limitMb) -> limitMb }
            .joinToString(separator = " | ") { (path, limitMb) ->
                "${path.absolutePath}=${limitMb}MB"
            }
        if (snapshot == lastCacheSelectionSnapshot) return

        lastCacheSelectionSnapshot = snapshot
        Log.i(TAG, "Torrent cache selected: ${selected.absolutePath}")
        Log.i(TAG, "Torrent cache candidates: $snapshot")
    }

    private const val BYTES_PER_MB = 1024L * 1024L
    private fun scheduleExpiry(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + RETENTION_MS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            expiryPendingIntent(context)
        )
    }

    private fun cancelExpiry(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(expiryPendingIntent(context))
    }

    private fun expiryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TorrentCacheExpiryReceiver::class.java).apply {
            action = ACTION_EXPIRE_CACHE
        }
        return PendingIntent.getBroadcast(
            context,
            EXPIRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun readBootCount(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrElse {
            // Fallback derived from the estimated wall-clock boot time.
            ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L).toInt()
        }
    }
}

class TorrentCacheExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TorrentCachePolicy.ACTION_EXPIRE_CACHE) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TorrentCachePolicy.clearExpiredCache(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class TorrentCacheBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TorrentCachePolicy.clearCacheAndPlayback(context, "Fire TV/device restarted")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
