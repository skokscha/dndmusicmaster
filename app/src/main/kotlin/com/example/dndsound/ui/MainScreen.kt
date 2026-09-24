package com.example.dndsound.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.dndsound.core.ambience.AmbienceState
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.oneshot.OneShotState
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.wheel.WheelMath
import com.example.dndsound.ui.wheel.MoodWheel

/**
 * Stage 6 screen: mood wheel, ambience, one-shot sounds and the library
 * debug listing behind section toggles. The full three-panel layout replaces
 * this in stage 7.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val music by viewModel.music.collectAsStateWithLifecycle()
    val ambience by viewModel.ambience.collectAsStateWithLifecycle()
    val oneShots by viewModel.oneShots.collectAsStateWithLifecycle()
    val favoriteOneShots by viewModel.favoriteOneShots.collectAsStateWithLifecycle()
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
                selected = section == Section.AMBIENCE,
                onClick = { section = Section.AMBIENCE },
                label = { Text(stringResource(R.string.section_ambience)) },
            )
            FilterChip(
                selected = section == Section.SOUNDS,
                onClick = { section = Section.SOUNDS },
                label = { Text(stringResource(R.string.section_sounds)) },
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

            Section.AMBIENCE -> AmbienceSection(
                library = library,
                ambience = ambience,
                onSelectEnvironment = viewModel::selectEnvironment,
                onTimeOfDay = viewModel::setTimeOfDay,
                onLayerEnabled = viewModel::setLayerEnabled,
                onLayerGain = viewModel::setLayerGain,
                onWeather = viewModel::setWeather,
                onStop = viewModel::pauseAmbience,
                onResume = viewModel::resumeAmbience,
            )

            Section.SOUNDS -> SoundsSection(
                state = oneShots,
                favorites = favoriteOneShots,
                onPlay = viewModel::playOneShot,
                onStopAll = viewModel::stopAllOneShots,
                onToggleFavorite = viewModel::toggleOneShotFavorite,
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

private enum class Section { WHEEL, AMBIENCE, SOUNDS, LIBRARY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundsSection(
    state: OneShotState,
    favorites: Set<String>,
    onPlay: (String) -> Unit,
    onStopAll: () -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.groups.isEmpty()) {
            Text(
                stringResource(R.string.sounds_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            return
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.sounds_search_hint)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onStopAll,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.sounds_stop_all))
            }
        }
        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isEmpty()) {
            state.groups
        } else {
            state.groups.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            filtered.take(MAX_ROWS).forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onPlay(group.id) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("${group.name} ×${group.variants.size}")
                    }
                    TextButton(
                        onClick = { onToggleFavorite(group.id) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (group.id in favorites) "★" else "☆",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
        if (filtered.size > MAX_ROWS) {
            Text(
                stringResource(R.string.library_more_items, filtered.size - MAX_ROWS),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.ducking) {
            Text(
                stringResource(R.string.sounds_ducking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { message ->
            Text(
                stringResource(R.string.music_error, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmbienceSection(
    library: Library,
    ambience: AmbienceState,
    onSelectEnvironment: (Environment?, TimeOfDay) -> Unit,
    onTimeOfDay: (TimeOfDay) -> Unit,
    onLayerEnabled: (String, Boolean) -> Unit,
    onLayerGain: (String, Float) -> Unit,
    onWeather: (Weather) -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val environments = library.environments
        if (environments.isEmpty()) {
            Text(
                stringResource(R.string.ambience_no_environments),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            return
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            environments.take(MAX_ROWS).forEach { env ->
                FilterChip(
                    selected = ambience.environmentId == env.id,
                    onClick = {
                        if (ambience.environmentId == env.id) {
                            onSelectEnvironment(null, ambience.timeOfDay)
                        } else {
                            onSelectEnvironment(env, ambience.timeOfDay)
                        }
                    },
                    label = { Text(env.name) },
                )
            }
        }
        if (ambience.environmentId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = ambience.timeOfDay == TimeOfDay.DAY,
                    onClick = { onTimeOfDay(TimeOfDay.DAY) },
                    label = { Text(stringResource(R.string.ambience_day)) },
                )
                FilterChip(
                    selected = ambience.timeOfDay == TimeOfDay.NIGHT,
                    onClick = { onTimeOfDay(TimeOfDay.NIGHT) },
                    label = { Text(stringResource(R.string.ambience_night)) },
                )
            }
            Text(
                stringResource(R.string.ambience_weather),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Weather.entries.forEach { weather ->
                    FilterChip(
                        selected = ambience.weather == weather,
                        onClick = { onWeather(weather) },
                        label = { Text(weatherLabel(weather)) },
                    )
                }
            }
            val loopLayers = ambience.layers.filter { it.kind == LayerKind.LOOP }
            if (loopLayers.isNotEmpty()) {
                Text(
                    stringResource(R.string.ambience_layers),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                loopLayers.forEach { layer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Switch(
                            checked = layer.enabled,
                            onCheckedChange = { onLayerEnabled(layer.id, it) },
                        )
                        Text(layer.name, modifier = Modifier.padding(start = 8.dp))
                        Slider(
                            value = layer.gainDb.coerceIn(-30f, 6f),
                            onValueChange = { onLayerGain(layer.id, it) },
                            valueRange = -30f..6f,
                            enabled = layer.enabled,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                if (ambience.playing) {
                    Button(onClick = onStop) { Text(stringResource(R.string.music_pause)) }
                } else {
                    Button(onClick = onResume) { Text(stringResource(R.string.music_play)) }
                }
            }
            ambience.error?.let { message ->
                Text(
                    stringResource(R.string.music_error, message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun weatherLabel(weather: Weather): String = when (weather) {
    Weather.NONE -> stringResource(R.string.weather_none)
    Weather.RAIN -> stringResource(R.string.weather_rain)
    Weather.STORM -> stringResource(R.string.weather_storm)
    Weather.WIND -> stringResource(R.string.weather_wind)
    Weather.SNOW -> stringResource(R.string.weather_snow)
}

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
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
