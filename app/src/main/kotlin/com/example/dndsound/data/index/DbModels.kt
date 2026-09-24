package com.example.dndsound.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache of one scanned audio file inside the user's library folder.
 * `path` is root-relative with '/' separators and doubles as the entity id,
 * so re-scans naturally upsert changed files and delete vanished ones.
 */
@Entity(tableName = "indexed_files")
data class IndexedFileEntity(
    @PrimaryKey val path: String,
    val uri: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val durationMs: Long,
    /** Raw meta.json content, kept so rebuilds from cache keep the overrides. */
    val metaText: String? = null,
)

/**
 * User-tuned values that must survive re-scans (file identity = path prefix
 * before the variant suffix). The audio files themselves live in the user's
 * folder — the app stores no content, only this index.
 */
@Entity(tableName = "track_overrides")
data class TrackOverrideEntity(
    @PrimaryKey val trackId: String,
    val x: Float,
    val y: Float,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val kind: String,
)
