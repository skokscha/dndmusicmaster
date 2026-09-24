package com.example.dndsound.core.repo

import kotlinx.coroutines.flow.Flow

/** User-adjustable settings (DataStore-backed in :app). */
data class AppSettings(
    val libraryRootUri: String? = null,
    val musicCrossfadeMs: Int = 4000,
    val ambienceCrossfadeMs: Int = 3000,
    val oneShotDuckingEnabled: Boolean = true,
    val duckingDb: Float = -3.5f,
    val hapticFeedbackEnabled: Boolean = true,
    val playAlongsideOtherApps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val pauseOnHeadphonesDisconnected: Boolean = true,
    val languageTag: String = "ru",
    val theme: Theme = Theme.AMBER,
    /** Mixer bus gains in dB; every engine applies them on top of per-track gains. */
    val masterDb: Float = 0f,
    val musicBusDb: Float = 0f,
    val ambienceBusDb: Float = 0f,
    val sfxBusDb: Float = 0f,
) {
    enum class Theme { DARK, AMBER }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
