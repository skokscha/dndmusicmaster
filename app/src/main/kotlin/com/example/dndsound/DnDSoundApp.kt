package com.example.dndsound

import android.app.Application
import android.content.Context
import com.example.dndsound.audio.ExoPlayerHandle
import com.example.dndsound.audio.SoundPoolMixer
import com.example.dndsound.core.ambience.AmbienceEngine
import com.example.dndsound.core.music.MusicEngine
import com.example.dndsound.core.oneshot.OneShotEngine
import com.example.dndsound.core.wheel.TrackSelector
import com.example.dndsound.data.index.AppDatabase
import com.example.dndsound.data.library.DefaultLibraryRepository
import com.example.dndsound.data.library.DurationProber
import com.example.dndsound.data.library.SafScanner
import com.example.dndsound.data.settings.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hand-rolled DI: one container per process, created in [onCreate] before any
 * UI can ask for dependencies (decision logged in docs/DECISIONS.md).
 */
class DnDSoundApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Application-lifetime scope for engines (music survives screen changes). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.build(appContext)
    val settingsRepository = SettingsRepositoryImpl(appContext)
    val scanner = SafScanner(appContext)
    val durationProber = DurationProber(appContext)
    val libraryRepository = DefaultLibraryRepository(
        database = database,
        settings = settingsRepository,
        scanner = scanner,
        prober = durationProber,
    )

    private val trackSelector = TrackSelector()
    val musicEngine = MusicEngine(
        scope = appScope,
        playerFactory = { ExoPlayerHandle(appContext) },
        selectTrack = { targetZone, point, mode, recent ->
            val tracks = libraryRepository.library.first().tracks
            trackSelector.select(targetZone, point, mode, tracks, recent)
        },
        selectNextTrack = { current, recent ->
            val tracks = libraryRepository.library.first().tracks
            trackSelector.next(tracks, current, recent)
        },
    )

    val ambienceEngine = AmbienceEngine(
        scope = appScope,
        playerFactory = { ExoPlayerHandle(appContext) },
        weatherFiles = { weather ->
            libraryRepository.library.first().weatherLoops[weather].orEmpty().map { it.uri }
        },
    )

    val soundPoolMixer = SoundPoolMixer(appContext)

    /** One-shot playback ducks both long-form buses while a sound is playing. */
    val oneShotEngine = OneShotEngine(
        scope = appScope,
        mixer = soundPoolMixer,
        onDuck = { db ->
            musicEngine.setDuck(db)
            ambienceEngine.setDuck(db)
        },
    )

    init {
        appScope.launch {
            settingsRepository.settings.collect { settings ->
                oneShotEngine.setDucking(settings.oneShotDuckingEnabled, settings.duckingDb)
                musicEngine.setBusGains(settings.masterDb, settings.musicBusDb)
                ambienceEngine.setBusGains(settings.masterDb, settings.ambienceBusDb)
                oneShotEngine.setBusGains(settings.masterDb, settings.sfxBusDb)
            }
        }
        appScope.launch {
            libraryRepository.library
                .map { it.oneShots }
                .distinctUntilChanged()
                .collect { oneShotEngine.setGroups(it) }
        }
    }
}
