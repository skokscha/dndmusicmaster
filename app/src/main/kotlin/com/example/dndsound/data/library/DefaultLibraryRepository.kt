package com.example.dndsound.data.library

import android.net.Uri
import com.example.dndsound.core.library.IndexedFile
import com.example.dndsound.core.library.LibraryIndexBuilder
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.repo.FavoriteKind
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.LibraryRepository
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.repo.SettingsRepository
import com.example.dndsound.data.index.AppDatabase
import com.example.dndsound.data.index.FavoriteEntity
import com.example.dndsound.data.index.IndexedFileEntity
import com.example.dndsound.data.index.LibraryDao
import com.example.dndsound.data.index.TrackOverrideEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * SAF + Room implementation of [LibraryRepository].
 *
 * The Room index is the single source of truth for the UI: a scan refreshes
 * it (incrementally by size + lastModified), and the [library] flow rebuilds
 * the playable [Library] from the cache — so the app can start even when the
 * folder is on unavailable storage, as long as the index is intact.
 */
class DefaultLibraryRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val scanner: SafScanner,
    private val prober: DurationProber,
) : LibraryRepository {

    private val dao: LibraryDao = database.libraryDao()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    override val library: Flow<Library> = combine(
        dao.observeAll(),
        dao.observeOverrides(),
    ) { files, overrides ->
        buildLibrary(files, overrides)
    }

    /** Remembers the user's folder choice and wipes a stale index from another root. */
    suspend fun selectLibraryRoot(uri: String?) {
        val previous = settings.settings.first().libraryRootUri
        if (previous != uri) dao.clearAll()
        settings.update { it.copy(libraryRootUri = uri) }
        _scanState.value = ScanState.Idle
    }

    override suspend fun rescan() {
        val rootUri = settings.settings.first().libraryRootUri
        if (rootUri == null) {
            _scanState.value = ScanState.Idle
            return
        }
        val treeUri = Uri.parse(rootUri)
        if (!scanner.hasAccess(treeUri)) {
            _scanState.value = ScanState.PermissionLost
            return
        }
        _scanState.value = ScanState.Scanning
        try {
            val cached = dao.getAll().associateBy { it.path }
            val scanned = scanner.scan(treeUri) { path -> cached[path]?.takeIf { it.durationMs > 0 }?.durationMs }

            val probed = prober.probeMissing(scanned)
            val withDurations = scanned.map { file ->
                probed[file.uri]?.let { d -> file.copy(durationMs = d) } ?: file
            }

            val entities = withDurations.map { file ->
                IndexedFileEntity(
                    path = file.relativePath,
                    uri = file.uri,
                    sizeBytes = file.sizeBytes,
                    lastModifiedMs = file.lastModifiedMs,
                    durationMs = file.durationMs ?: 0L,
                    metaText = file.metaText,
                )
            }
            val removed = cached.keys - entities.mapTo(mutableSetOf()) { it.path }
            dao.replaceIndex(removed.toList(), entities)

            val audioCount = withDurations.count { it.isAudio }
            val warnings = LibraryIndexBuilder.build(withDurations).warnings.size
            _scanState.value = ScanState.Ready(audioCount, warnings)
        } catch (e: SafScanner.LibraryAccessException) {
            _scanState.value = ScanState.PermissionLost
        } catch (e: Exception) {
            _scanState.value = ScanState.Failed(e.message)
        }
    }

    override suspend fun setTrackPosition(trackId: String, point: WheelPoint) {
        dao.insertOverride(TrackOverrideEntity(trackId, point.x, point.y))
    }

    override suspend fun setFavorite(kind: FavoriteKind, id: String, favorite: Boolean) {
        if (favorite) dao.insertFavorite(FavoriteEntity(id, kind.name))
        else dao.deleteFavorite(id, kind.name)
    }

    override suspend fun favorites(kind: FavoriteKind): Set<String> =
        dao.favoritesOfKind(kind.name).mapTo(mutableSetOf()) { it.id }

    private fun buildLibrary(
        files: List<IndexedFileEntity>,
        overrides: List<TrackOverrideEntity>,
    ): Library {
        val indexed = files.map { f ->
            IndexedFile(
                relativePath = f.path,
                uri = f.uri,
                sizeBytes = f.sizeBytes,
                lastModifiedMs = f.lastModifiedMs,
                durationMs = f.durationMs.takeIf { it > 0 },
                metaText = f.metaText,
            )
        }
        val built = LibraryIndexBuilder.build(indexed)
        if (overrides.isEmpty()) return built.library
        val moved = overrides.associateBy { it.trackId }
        return built.library.copy(
            tracks = built.library.tracks.map { track ->
                moved[track.id]?.let { track.copy(position = WheelPoint(it.x, it.y).clamped()) } ?: track
            },
        )
    }
}
