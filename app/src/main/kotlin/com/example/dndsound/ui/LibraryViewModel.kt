package com.example.dndsound.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dndsound.core.repo.AppSettings
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.repo.SettingsRepository
import com.example.dndsound.data.library.DefaultLibraryRepository
import com.example.dndsound.data.library.SafScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: DefaultLibraryRepository,
    private val scanner: SafScanner,
) : ViewModel() {

    private val appContext = context.applicationContext

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val library: StateFlow<Library> = libraryRepository.library
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Library())

    val scanState: StateFlow<ScanState> = libraryRepository.scanState

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
