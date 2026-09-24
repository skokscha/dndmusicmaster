package com.example.dndsound.core.repo

import com.example.dndsound.core.model.Scene
import com.example.dndsound.core.model.SceneSnapshot
import kotlinx.coroutines.flow.Flow

/** Saved scene presets (the chips row under the environment grid). */
interface SceneRepository {
    val scenes: Flow<List<Scene>>

    suspend fun save(name: String, snapshot: SceneSnapshot): Scene

    suspend fun delete(sceneId: String)

    /** Serializes scenes to JSON (settings: export/import). */
    suspend fun exportJson(): String

    /** Imports scenes from JSON produced by [exportJson]; merges by id. */
    suspend fun importJson(json: String)
}
