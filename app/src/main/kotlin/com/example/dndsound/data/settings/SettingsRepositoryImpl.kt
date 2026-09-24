package com.example.dndsound.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dndsound.core.repo.AppSettings
import com.example.dndsound.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.dataStore.data.map(::toAppSettings)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            write(prefs, transform(toAppSettings(prefs)))
        }
    }

    private fun toAppSettings(prefs: Preferences): AppSettings = AppSettings(
        libraryRootUri = prefs[KEY_ROOT_URI],
        musicCrossfadeMs = prefs[KEY_MUSIC_CROSSFADE] ?: 4000,
        ambienceCrossfadeMs = prefs[KEY_AMBIENCE_CROSSFADE] ?: 3000,
        oneShotDuckingEnabled = prefs[KEY_DUCKING_ENABLED] ?: true,
        duckingDb = prefs[KEY_DUCKING_DB] ?: -3.5f,
        hapticFeedbackEnabled = prefs[KEY_HAPTIC] ?: true,
        playAlongsideOtherApps = prefs[KEY_ALONGSIDE] ?: false,
        keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
        pauseOnHeadphonesDisconnected = prefs[KEY_PAUSE_ON_UNPLUG] ?: true,
        languageTag = prefs[KEY_LANGUAGE] ?: "ru",
        theme = prefs[KEY_THEME]?.let { runCatching { AppSettings.Theme.valueOf(it) }.getOrNull() }
            ?: AppSettings.Theme.DARK,
    )

    private fun write(prefs: MutablePreferences, s: AppSettings) {
        val root = s.libraryRootUri
        if (root == null) prefs.remove(KEY_ROOT_URI) else prefs[KEY_ROOT_URI] = root
        prefs[KEY_MUSIC_CROSSFADE] = s.musicCrossfadeMs
        prefs[KEY_AMBIENCE_CROSSFADE] = s.ambienceCrossfadeMs
        prefs[KEY_DUCKING_ENABLED] = s.oneShotDuckingEnabled
        prefs[KEY_DUCKING_DB] = s.duckingDb
        prefs[KEY_HAPTIC] = s.hapticFeedbackEnabled
        prefs[KEY_ALONGSIDE] = s.playAlongsideOtherApps
        prefs[KEY_KEEP_SCREEN_ON] = s.keepScreenOn
        prefs[KEY_PAUSE_ON_UNPLUG] = s.pauseOnHeadphonesDisconnected
        prefs[KEY_LANGUAGE] = s.languageTag
        prefs[KEY_THEME] = s.theme.name
    }

    private companion object {
        val KEY_ROOT_URI = stringPreferencesKey("library_root_uri")
        val KEY_MUSIC_CROSSFADE = intPreferencesKey("music_crossfade_ms")
        val KEY_AMBIENCE_CROSSFADE = intPreferencesKey("ambience_crossfade_ms")
        val KEY_DUCKING_ENABLED = booleanPreferencesKey("oneshot_ducking_enabled")
        val KEY_DUCKING_DB = floatPreferencesKey("ducking_db")
        val KEY_HAPTIC = booleanPreferencesKey("haptic_feedback")
        val KEY_ALONGSIDE = booleanPreferencesKey("play_alongside_other_apps")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_headphones_disconnect")
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
        val KEY_THEME = stringPreferencesKey("theme")
    }
}
