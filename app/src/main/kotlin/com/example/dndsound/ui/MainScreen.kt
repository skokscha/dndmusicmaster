package com.example.dndsound.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dndsound.R
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.wheel.WheelMath
import com.example.dndsound.ui.wheel.MoodWheel

/**
 * Stage 4 screen: mood wheel with playback controls plus the stage 3 library
 * debug listing behind a section toggle. The full three-panel layout replaces
 * this in stage 7.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val music by viewModel.music.collectAsStateWithLifecycle()
    val liveMarker by viewModel.liveMarker.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onFolderPicked(uri) }

    if (settings.libraryRootUri == null) {
        OnboardingContent(onPickFolder = { folderPicker.launch(null) })
        return
    }

    var section by rememberSaveable { mutableStateOf(Section.WHEEL) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = section == Section.WHEEL,
                onClick = { section = Section.WHEEL },
                label = { Text(stringResource(R.string.section_wheel)) },
            )
            FilterChip(
                selected = section == Section.LIBRARY,
                onClick = { section = Section.LIBRARY },
                label = { Text(stringResource(R.string.section_library)) },
            )
        }
        when (section) {
            Section.WHEEL -> WheelSection(
                music = music,
                liveMarker = liveMarker,
                onWheelDrag = viewModel::onWheelDrag,
                onModeChange = viewModel::setMode,
                onNext = viewModel::nextTrack,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
            )

            Section.LIBRARY -> LibraryContent(
                library = library,
                scanState = scanState,
                onRescan = viewModel::rescan,
                onChangeFolder = { folderPicker.launch(null) },
                onCreateStructure = viewModel::createFolderStructure,
                onDisconnect = viewModel::disconnectFolder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class Section { WHEEL, LIBRARY }

@Composable
private fun WheelSection(
    music: com.example.dndsound.core.music.MusicState,
    liveMarker: com.example.dndsound.core.model.WheelPoint?,
    onWheelDrag: (com.example.dndsound.core.model.WheelPoint) -> Unit,
    onModeChange: (MusicMode) -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = music.mode == MusicMode.EXPLORATION,
                onClick = { onModeChange(MusicMode.EXPLORATION) },
                label = { Text(stringResource(R.string.mode_exploration)) },
            )
            FilterChip(
                selected = music.mode == MusicMode.BATTLE,
                onClick = { onModeChange(MusicMode.BATTLE) },
                label = { Text(stringResource(R.string.mode_battle)) },
            )
        }
        MoodWheel(
            marker = liveMarker ?: music.anchor,
            mode = music.mode,
            onPointChange = onWheelDrag,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(top = 8.dp),
        )
        TrackStatusLine(music)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlinedButton(onClick = onNext) {
                Text(stringResource(R.string.music_next))
            }
            if (music.playing) {
                Button(onClick = onPause) {
                    Text(stringResource(R.string.music_pause))
                }
            } else {
                Button(onClick = onResume) {
                    Text(stringResource(R.string.music_play))
                }
            }
        }
    }
}

@Composable
private fun TrackStatusLine(music: com.example.dndsound.core.music.MusicState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
        val track = music.currentTrack
        when {
            track != null -> {
                Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val zoneRes: Int? = music.anchor?.let { anchor ->
                    if (anchor.isCalm()) {
                        R.string.mood_calm
                    } else {
                        moodLabelRes(WheelMath.nearestMood(anchor.angleDeg))
                    }
                }
                val details = buildList {
                    zoneRes?.let { add(stringResource(it)) }
                    if (music.crossfading) add(stringResource(R.string.music_crossfading))
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            music.playing -> Text(stringResource(R.string.music_waiting))
            else -> Text(
                stringResource(R.string.music_nothing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        music.error?.let { message ->
            Text(
                stringResource(R.string.music_error, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OnboardingContent(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_create_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onPickFolder,
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(stringResource(R.string.onboarding_pick_folder))
        }
    }
}

private const val MAX_ROWS = 100

@Composable
private fun LibraryContent(
    library: Library,
    scanState: ScanState,
    onRescan: () -> Unit,
    onChangeFolder: () -> Unit,
    onCreateStructure: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScanStatusBar(scanState) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRescan, enabled = scanState !is ScanState.Scanning) {
                    Text(stringResource(R.string.library_rescan))
                }
                OutlinedButton(onClick = onChangeFolder) {
                    Text(stringResource(R.string.library_change_folder))
                }
            }
        }
        if (library.isEmpty) {
            item {
                Column {
                    Text(stringResource(R.string.library_empty))
                    Button(onClick = onCreateStructure, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.library_create_structure))
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onDisconnect) {
                Text(stringResource(R.string.library_disconnect))
            }
        }
        if (library.tracks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_tracks, library.tracks.size)) }
            items(library.tracks.take(MAX_ROWS)) { track ->
                Text(
                    "• ${track.title} — ${track.mode.name.lowercase()} [${track.position.angleDeg.toInt()}°]",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item { MoreRow(library.tracks.size) }
        }
        if (library.environments.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_environments, library.environments.size)) }
            items(library.environments.take(MAX_ROWS)) { env ->
                Column {
                    Text("• ${env.name}", fontWeight = FontWeight.SemiBold)
                    env.layers.forEach { layer ->
                        Text(
                            "    ${layer.name} (${layer.kind.name.lowercase()}, ${layer.uris.size})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { MoreRow(library.environments.size) }
        }
        if (library.oneShots.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_one_shots, library.oneShots.size)) }
            items(library.oneShots.take(MAX_ROWS)) { shot ->
                Text(
                    "• ${shot.name} — ${shot.category.name.lowercase()} ×${shot.variants.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item { MoreRow(library.oneShots.size) }
        }
        if (library.weatherLoops.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_weather, library.weatherLoops.size)) }
            items(library.weatherLoops.entries.toList()) { (weather, files) ->
                Text(
                    "• ${weather.name.lowercase()} ×${files.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MoreRow(total: Int) {
    if (total > MAX_ROWS) {
        Text(
            stringResource(R.string.library_more_items, total - MAX_ROWS),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ScanStatusBar(scanState: ScanState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (scanState) {
            is ScanState.Scanning -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.library_scanning))
            }

            is ScanState.Ready -> Column {
                Text(stringResource(R.string.library_files_count, scanState.fileCount))
                if (scanState.warningCount > 0) {
                    Text(
                        stringResource(R.string.library_warnings_count, scanState.warningCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            is ScanState.PermissionLost -> Text(stringResource(R.string.library_permission_lost))
            is ScanState.Failed -> Text(stringResource(R.string.library_failed, scanState.message ?: ""))
            ScanState.Idle -> Text(stringResource(R.string.library_idle))
        }
    }
}
