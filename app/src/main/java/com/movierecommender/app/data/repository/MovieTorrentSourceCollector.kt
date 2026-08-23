package com.movierecommender.app.data.repository

import com.movierecommender.app.data.remote.TorrentInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Starts every source in a lookup tier and publishes useful results as each source finishes.
 * There is deliberately no feature-level timeout: slow sources continue until their own request
 * completes, while faster sources can already populate the Watch Options UI.
 */
internal object MovieTorrentSourceCollector {

    suspend fun collectAll(
        sources: List<suspend () -> TorrentInfo?>,
        onResult: suspend (TorrentInfo) -> Unit = {}
    ): List<TorrentInfo> = supervisorScope {
        if (sources.isEmpty()) return@supervisorScope emptyList()

        val completions = Channel<TorrentInfo?>(capacity = sources.size)
        sources.forEach { source ->
            launch(Dispatchers.IO) {
                val result = try {
                    source()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                completions.send(result)
            }
        }

        buildList {
            repeat(sources.size) {
                completions.receive()?.let { result ->
                    add(result)
                    onResult(result)
                }
            }
        }.also { completions.close() }
    }
}