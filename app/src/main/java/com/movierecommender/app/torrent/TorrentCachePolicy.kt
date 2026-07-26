package com.movierecommender.app.torrent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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
    private const val EXPIRY_REQUEST_CODE = 9102

    fun cacheDirectory(context: Context): File =
        File(context.applicationContext.cacheDir, CACHE_DIRECTORY_NAME)

    /** Clears cache left by a reboot or an expired retention window. */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val currentBootCount = readBootCount(appContext)
        val storedBootCount = prefs.getInt(KEY_BOOT_COUNT, Int.MIN_VALUE)
        val retainUntil = prefs.getLong(KEY_RETAIN_UNTIL, 0L)
        val rebooted = storedBootCount != Int.MIN_VALUE && storedBootCount != currentBootCount
        val expired = retainUntil > 0L && System.currentTimeMillis() >= retainUntil

        when {
            rebooted -> clearCacheAndPlayback(appContext, "device reboot detected")
            expired -> clearCacheAndPlayback(appContext, "60-minute retention expired")
            else -> prefs.edit().putInt(KEY_BOOT_COUNT, currentBootCount).apply()
        }
    }

    fun markActive(context: Context, magnetHash: Int) {
        val appContext = context.applicationContext
        cancelExpiry(appContext)
        appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVE_MAGNET_HASH, magnetHash)
            .putLong(KEY_RETAIN_UNTIL, 0L)
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .apply()
    }

    fun retainForResume(context: Context, magnetHash: Int): Long {
        val appContext = context.applicationContext
        val retainUntil = System.currentTimeMillis() + RETENTION_MS
        appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVE_MAGNET_HASH, magnetHash)
            .putLong(KEY_RETAIN_UNTIL, retainUntil)
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .apply()
        scheduleExpiry(appContext)
        return retainUntil
    }

    fun isExpired(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val retainUntil = context.applicationContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RETAIN_UNTIL, 0L)
        return retainUntil > 0L && nowMs >= retainUntil
    }

    fun clearCacheAndPlayback(context: Context, reason: String) {
        val appContext = context.applicationContext
        cancelExpiry(appContext)
        val sessionPrefs = appContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val activeHash = sessionPrefs.getInt(KEY_ACTIVE_MAGNET_HASH, Int.MIN_VALUE)

        val cacheDir = cacheDirectory(appContext)
        if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
            Log.w(TAG, "Some torrent cache files could not be deleted: $reason")
        }

        if (activeHash != Int.MIN_VALUE) {
            appContext.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("position_$activeHash")
                .remove("duration_$activeHash")
                .apply()
        }

        sessionPrefs.edit()
            .clear()
            .putInt(KEY_BOOT_COUNT, readBootCount(appContext))
            .commit()
        Log.i(TAG, "Torrent cache cleared: $reason")
    }

    fun cacheSizeBytes(context: Context): Long {
        val cacheDir = cacheDirectory(context)
        if (!cacheDir.exists()) return 0L
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

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
        if (!TorrentCachePolicy.isExpired(context)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TorrentCachePolicy.clearCacheAndPlayback(context, "60-minute inactivity alarm")
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
