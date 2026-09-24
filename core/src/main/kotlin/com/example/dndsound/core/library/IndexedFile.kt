package com.example.dndsound.core.library

/**
 * One entry produced by the folder scan, before any convention logic runs.
 * The SAF scanner fills the path and the metadata; durations come from the
 * Room cache or MediaMetadataRetriever; metaText is set only for meta.json
 * files whose content was read.
 */
data class IndexedFile(
    /** Root-relative, '/'-separated: "music/happy/theme_a.ogg". */
    val relativePath: String,
    val uri: String,
    val sizeBytes: Long = 0L,
    val lastModifiedMs: Long = 0L,
    /** Audio files only; null means unknown (or not an audio file). */
    val durationMs: Long? = null,
    /** Raw meta.json content; non-null only when this file IS a meta.json. */
    val metaText: String? = null,
) {
    val name: String get() = relativePath.substringAfterLast('/')

    /** Parent directory, root-relative; "" for files in the library root. */
    val directory: String get() =
        if ('/' in relativePath) relativePath.substringBeforeLast('/') else ""

    val isAudio: Boolean get() = LibraryRules.isAudioFile(name)
}
