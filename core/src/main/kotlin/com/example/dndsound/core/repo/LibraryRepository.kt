package com.example.dndsound.core.repo

import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.SoundFile
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.model.WheelPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The indexed user library, rebuilt incrementally on every scan. */
data class Library(
    val tracks: List<Track> = emptyList(),
    val environments: List<Environment> = emptyList(),
    val oneShots: List<OneShot> = emptyList(),
    /** Weather type -> loop candidates played while that weather is active. */
    val weatherLoops: Map<Weather, List<SoundFile>> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && environments.isEmpty() &&
            oneShots.isEmpty() && weatherLoops.isEmpty()
}

enum class FavoriteKind { TRACK, ENVIRONMENT, ONE_SHOT, SCENE }

/** Lifecycle of a library scan, surfaced to the UI. */
sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState

    /** Scan finished; fileCount counts indexed audio files. */
    data class Ready(val fileCount: Int, val warningCount: Int) : ScanState

    /** The picked folder no longer has a persisted read permission. */
    data object PermissionLost : ScanState

    data class Failed(val message: String?) : ScanState
}

/** Access to the indexed library; implementations live in :app (Room + SAF). */
interface LibraryRepository {
    val library: Flow<Library>

    val scanState: StateFlow<ScanState>

    /** Re-scans the library folder incrementally (by lastModified + size). */
    suspend fun rescan()

    /** Persists a user-moved track position; survives re-scans. */
    suspend fun setTrackPosition(trackId: String, point: WheelPoint)

    suspend fun setFavorite(kind: FavoriteKind, id: String, favorite: Boolean)

    /** Ids of the given kind currently marked as favorite. */
    suspend fun favorites(kind: FavoriteKind): Set<String>
}
