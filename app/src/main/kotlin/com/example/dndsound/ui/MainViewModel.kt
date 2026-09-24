package com.example.dndsound.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dndsound.core.ambience.AmbienceEngine
import com.example.dndsound.core.ambience.AmbienceState
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.music.MusicEngine
import com.example.dndsound.core.music.MusicState
import com.example.dndsound.core.repo.AppSettings
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.repo.SettingsRepository
import com.example.dndsound.data.library.DefaultLibraryRepository
import com.example.dndsound.data.library.SafScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single screen ViewModel for stages 3-4: library onboarding/debug plus the
 * music wheel. Stage 7 splits it per panel as the UI grows.
 */
class MainViewModel(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: DefaultLibraryRepository,
    private val scanner: SafScanner,
    val musicEngine: MusicEngine,
    val ambienceEngine: AmbienceEngine,
) : ViewModel() {

    private val appContext = context.applicationContext

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val library: StateFlow<Library> = libraryRepository.library
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Library())

    val scanState: StateFlow<ScanState> = libraryRepository.scanState

    val music: StateFlow<MusicState> = musicEngine.state

    val ambience: StateFlow<AmbienceState> = ambienceEngine.state

    /** Live drag position shown on the wheel before the debounced commit. */
    private val _liveMarker = MutableStateFlow<WheelPoint?>(null)
    val liveMarker: StateFlow<WheelPoint?> = _liveMarker.asStateFlow()

    fun onWheelDrag(point: WheelPoint) {
        _liveMarker.value = point
        musicEngine.setWheelTarget(point, music.value.mode)
    }

    fun setMode(mode: MusicMode) {
        _liveMarker.value = null
        musicEngine.setMode(mode)
    }

    fun nextTrack() = musicEngine.next()

    fun pause() = musicEngine.pause()

    fun resume() = musicEngine.resume()

    // ------------------------------------------------------------ ambience

    fun selectEnvironment(environment: Environment?, timeOfDay: TimeOfDay) {
        ambienceEngine.setEnvironment(environment, timeOfDay)
    }

    fun setTimeOfDay(timeOfDay: TimeOfDay) = ambienceEngine.setTimeOfDay(timeOfDay)

    fun setLayerEnabled(layerId: String, enabled: Boolean) =
        ambienceEngine.setLayerEnabled(layerId, enabled)

    fun setLayerGain(layerId: String, gainDb: Float) =
        ambienceEngine.setLayerGain(layerId, gainDb)

    fun setWeather(weather: Weather) = ambienceEngine.setWeather(weather)

    fun pauseAmbience() = ambienceEngine.pause()

    fun resumeAmbience() = ambienceEngine.resume()

    // ------------------------------------------------------------- library

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            persistPermission(uri)
            libraryRepository.selectLibraryRoot(uri.toString())
            libraryRepository.rescan()
        }
    }

    fun rescan() {
        viewModelScope.launch { libraryRepository.rescan() }
    }

    fun createFolderStructure() {
        viewModelScope.launch {
            val root = settings.value.libraryRootUri ?: return@launch
            scanner.createFolderStructure(Uri.parse(root))
            libraryRepository.rescan()
        }
    }

    /** Forgets the folder: releases the persisted permission and clears the index. */
    fun disconnectFolder() {
        viewModelScope.launch {
            settings.value.libraryRootUri?.let { releasePermission(Uri.parse(it)) }
            libraryRepository.selectLibraryRoot(null)
        }
    }

    private fun persistPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Provider did not grant persistable access; scanning will fail
            // visibly instead of silently pretending everything is fine.
        }
    }

    private fun releasePermission(uri: Uri) {
        try {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Nothing persisted — nothing to release.
        }
    }
}
