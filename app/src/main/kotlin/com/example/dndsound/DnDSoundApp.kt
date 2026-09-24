package com.example.dndsound

import android.app.Application
import android.content.Context
import com.example.dndsound.data.index.AppDatabase
import com.example.dndsound.data.library.DefaultLibraryRepository
import com.example.dndsound.data.library.DurationProber
import com.example.dndsound.data.library.SafScanner
import com.example.dndsound.data.settings.SettingsRepositoryImpl

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
}
