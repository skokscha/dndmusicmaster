package com.example.dndsound.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM indexed_files")
    fun observeAll(): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM track_overrides")
    fun observeOverrides(): Flow<List<TrackOverrideEntity>>

    @Query("SELECT * FROM favorites")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM indexed_files")
    suspend fun getAll(): List<IndexedFileEntity>

    @Query("SELECT durationMs FROM indexed_files WHERE path = :path")
    suspend fun durationOf(path: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(files: List<IndexedFileEntity>)

    @Query("DELETE FROM indexed_files WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("DELETE FROM indexed_files")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM indexed_files")
    suspend fun count(): Int

    @Query("SELECT * FROM track_overrides")
    suspend fun trackOverrides(): List<TrackOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: TrackOverrideEntity)

    @Query("SELECT * FROM favorites WHERE kind = :kind")
    suspend fun favoritesOfKind(kind: String): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND kind = :kind")
    suspend fun deleteFavorite(id: String, kind: String)

    /** Swap the file index in one transaction; overrides/favorites stay. */
    @Transaction
    suspend fun replaceIndex(oldPaths: List<String>, upserts: List<IndexedFileEntity>) {
        deleteByPaths(oldPaths)
        upsertAll(upserts)
    }
}
