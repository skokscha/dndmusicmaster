package com.example.dndsound.core.repo

import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import kotlinx.coroutines.flow.Flow

/** The indexed user library, rebuilt incrementally on every scan. */
data class Library(
    val tracks: List<Track> = emptyList(),
    val environments: List<Environment> = emptyList(),
    val oneShots: List<OneShot> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && environments.isEmpty() && oneShots.isEmpty()
}

enum class FavoriteKind { TRACK, ENVIRONMENT, ONE_SHOT, SCENE }

/** Access to the indexed library; implementations live in :app (Room + SAF). */
interface LibraryRepository {
    val library: Flow<Library>

    /** Re-scans the library folder incrementally (by lastModified + size). */
    suspend fun rescan()

    /** Persists a user-moved track position; survives re-scans. */
    suspend fun setTrackPosition(trackId: String, point: WheelPoint)

    suspend fun setFavorite(kind: FavoriteKind, id: String, favorite: Boolean)

    /** Ids of the given kind currently marked as favorite. */
    suspend fun favorites(kind: FavoriteKind): Set<String>
}
