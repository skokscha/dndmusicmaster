package com.example.dndsound.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads audio durations with [MediaMetadataRetriever]. SAF URIs are probed
 * lazily, a bounded batch per scan, so the first scan stays responsive;
 * results are persisted in the Room index and never probed again while
 * size + lastModified are unchanged.
 */
class DurationProber(context: Context) {

    private val appContext = context.applicationContext

    /** Returns duration in ms, or 0 when the file cannot be read. */
    suspend fun probe(uri: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, android.net.Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Probes up to [budget] files without a known duration; returns a map of
     * uri -> durationMs for only the successfully probed entries.
     */
    suspend fun probeMissing(
        files: List<com.example.dndsound.core.library.IndexedFile>,
        budget: Int = 32,
    ): Map<String, Long> {
        val missing = files.filter { it.isAudio && (it.durationMs == null || it.durationMs == 0L) }
        val result = mutableMapOf<String, Long>()
        for (file in missing.take(budget)) {
            val duration = probe(file.uri)
            if (duration > 0L) result[file.uri] = duration
        }
        return result
    }
}
