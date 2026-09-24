package com.example.dndsound.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dndsound.R
import com.example.dndsound.core.ambience.AmbienceState
import com.example.dndsound.core.model.Bus
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.oneshot.OneShotDisplay
import com.example.dndsound.core.oneshot.OneShotState
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState
import com.example.dndsound.core.wheel.Tier
import com.example.dndsound.core.wheel.WheelZone
import com.example.dndsound.core.wheel.WheelZonePalette
import com.example.dndsound.core.wheel.WheelZones
import com.example.dndsound.ui.theme.AppBackdrop
import com.example.dndsound.ui.wheel.MoodWheel

/** Panels of the main screen; the label/icon pair feeds chips and the rail. */
private enum class Section { WHEEL, AMBIENCE, SOUNDS, MIXER, LIBRARY }

/** Window layout buckets; mapped from the adaptive WindowSizeClass in MainActivity. */
enum class LayoutMode { COMPACT, MEDIUM, EXPANDED }

private val Section.labelRes: Int
    get() = when (this) {
        Section.WHEEL -> R.string.section_wheel
        Section.AMBIENCE -> R.string.section_ambience
        Section.SOUNDS -> R.string.section_sounds
        Section.MIXER -> R.string.section_mixer
        Section.LIBRARY -> R.string.section_library
    }

private val Section.icon: ImageVector
    get() = when (this) {
        Section.WHEEL -> Icons.Filled.Album
        Section.AMBIENCE -> Icons.Filled.Forest
        Section.SOUNDS -> Icons.Filled.GraphicEq
        Section.MIXER -> Icons.Filled.Tune
        Section.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
    }

/**
 * Adaptive main screen (stage 7):
 *  - compact: title + chip row, one panel at a time;
 *  - medium:  navigation rail + one panel;
 *  - expanded: navigation rail + the three audio panels side by side
 *    (mixer and library open as a single panel).
 */
@Composable
fun MainScreen(viewModel: MainViewModel, layoutMode: LayoutMode) {
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
    val compact = layoutMode == LayoutMode.COMPACT
    val expanded = layoutMode == LayoutMode.EXPANDED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackdrop)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppHeader(onToggleTheme = viewModel::toggleTheme)
        when {
            compact -> {
                SectionChips(selected = section, onSelect = { section = it })
                Box(modifier = Modifier.weight(1f)) {
                    MainPanel(
                        section = section,
                        viewModel = viewModel,
                        settings = settings,
                        library = library,
                        scanState = scanState,
                        music = music,
                        ambience = ambience,
                        oneShots = oneShots,
                        favorites = favoriteOneShots,
                        liveMarker = liveMarker,
                        onChangeFolder = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            else -> Row(modifier = Modifier.weight(1f)) {
                AppRail(selected = section, onSelect = { section = it })
                if (expanded && section != Section.MIXER && section != Section.LIBRARY) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MainPanel(
                            section = Section.WHEEL,
                            viewModel = viewModel,
                            settings = settings,
                            library = library,
                            scanState = scanState,
                            music = music,
                            ambience = ambience,
                            oneShots = oneShots,
                            favorites = favoriteOneShots,
                            liveMarker = liveMarker,
                            onChangeFolder = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                        MainPanel(
                            section = Section.AMBIENCE,
                            viewModel = viewModel,
                            settings = settings,
                            library = library,
                            scanState = scanState,
                            music = music,
                            ambience = ambience,
                            oneShots = oneShots,
                            favorites = favoriteOneShots,
                            liveMarker = liveMarker,
                            onChangeFolder = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                        MainPanel(
                            section = Section.SOUNDS,
                            viewModel = viewModel,
                            settings = settings,
                            library = library,
                            scanState = scanState,
                            music = music,
                            ambience = ambience,
                            oneShots = oneShots,
                            favorites = favoriteOneShots,
                            liveMarker = liveMarker,
                            onChangeFolder = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        MainPanel(
                            section = section,
                            viewModel = viewModel,
                            settings = settings,
                            library = library,
                            scanState = scanState,
                            music = music,
                            ambience = ambience,
                            oneShots = oneShots,
                            favorites = favoriteOneShots,
                            liveMarker = liveMarker,
                            onChangeFolder = { folderPicker.launch(null) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** One panel of [section], with everything that panel needs to render. */
@Composable
private fun MainPanel(
    section: Section,
    viewModel: MainViewModel,
    settings: com.example.dndsound.core.repo.AppSettings,
    library: Library,
    scanState: ScanState,
    music: com.example.dndsound.core.music.MusicState,
    ambience: AmbienceState,
    oneShots: OneShotState,
    favorites: Set<String>,
    liveMarker: com.example.dndsound.core.model.WheelPoint?,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        Section.WHEEL -> WheelPanel(
            music = music,
            liveMarker = liveMarker,
            emptyZoneIds = library.tracks
                .mapNotNull { it.position?.let { point -> WheelZones.zoneAt(point).id } }
                .toSet(),
            desaturateEmpty = settings.highlightEmptyZones && library.tracks.isNotEmpty(),
            onWheelDrag = viewModel::onWheelDrag,
            onModeChange = viewModel::setMode,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            modifier = modifier,
        )

        Section.AMBIENCE -> AmbiencePanel(
            library = library,
            ambience = ambience,
            onSelectEnvironment = viewModel::selectEnvironment,
            onTimeOfDay = viewModel::setTimeOfDay,
            onLayerEnabled = viewModel::setLayerEnabled,
            onLayerGain = viewModel::setLayerGain,
            onWeather = viewModel::setWeather,
            onStop = viewModel::pauseAmbience,
            onResume = viewModel::resumeAmbience,
            modifier = modifier,
        )

        Section.SOUNDS -> SoundsPanel(
            state = oneShots,
            favorites = favorites,
            onPlay = viewModel::playOneShot,
            onStopAll = viewModel::stopAllOneShots,
            onToggleFavorite = viewModel::toggleOneShotFavorite,
            modifier = modifier,
        )

        Section.MIXER -> MixerPanel(
            masterDb = settings.masterDb,
            musicDb = settings.musicBusDb,
            ambienceDb = settings.ambienceBusDb,
            sfxDb = settings.sfxBusDb,
            onBusGain = viewModel::setBusGain,
            modifier = modifier,
        )

        Section.LIBRARY -> LibraryContent(
            library = library,
            scanState = scanState,
            onRescan = viewModel::rescan,
            onChangeFolder = onChangeFolder,
            onCreateStructure = viewModel::createFolderStructure,
            onDisconnect = viewModel::disconnectFolder,
            onChooseZone = viewModel::setTrackZone,
            modifier = modifier,
        )
    }
}

@Composable
private fun AppHeader(onToggleTheme: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleTheme) {
            Icon(
                Icons.Filled.DarkMode,
                contentDescription = stringResource(R.string.theme_toggle),
            )
        }
    }
}

@Composable
private fun SectionChips(selected: Section, onSelect: (Section) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Section.entries.forEach { s ->
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(s) },
                label = { Text(stringResource(s.labelRes)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun AppRail(selected: Section, onSelect: (Section) -> Unit) {
    NavigationRail {
        Section.entries.forEach { s ->
            NavigationRailItem(
                selected = selected == s,
                onClick = { onSelect(s) },
                icon = {
                    Icon(s.icon, contentDescription = stringResource(s.labelRes))
                },
                label = { Text(stringResource(s.labelRes)) },
            )
        }
    }
}

/** Mixer bus faders; values live in [com.example.dndsound.core.repo.AppSettings]. */
@Composable
private fun MixerPanel(
    masterDb: Float,
    musicDb: Float,
    ambienceDb: Float,
    sfxDb: Float,
    onBusGain: (Bus, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BusRow(R.string.bus_master, Icons.Filled.VolumeUp, masterDb) { onBusGain(Bus.MASTER, it) }
                BusRow(R.string.bus_music, Icons.Filled.MusicNote, musicDb) { onBusGain(Bus.MUSIC, it) }
                BusRow(R.string.bus_ambience, Icons.Filled.Forest, ambienceDb) { onBusGain(Bus.AMBIENCE, it) }
                BusRow(R.string.bus_sfx, Icons.Filled.GraphicEq, sfxDb) { onBusGain(Bus.SFX, it) }
            }
        }
    }
}

@Composable
private fun BusRow(labelRes: Int, icon: ImageVector, gainDb: Float, onChange: (Float) -> Unit) {
    // Drag locally; commit once on release so DataStore and the engines get a
    // single, settled value instead of one write per frame.
    var dragValue by rememberSaveable(gainDb) { mutableFloatStateOf(gainDb) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = stringResource(labelRes),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
        Slider(
            value = dragValue.coerceIn(-30f, 6f),
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onChange(dragValue) },
            valueRange = -30f..6f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            stringResource(R.string.bus_gain_value, dragValue),
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundsPanel(
    state: OneShotState,
    favorites: Set<String>,
    onPlay: (String) -> Unit,
    onStopAll: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var soundLimit by rememberSaveable { mutableIntStateOf(MAX_ROWS) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                onValueChange = {
                    query = it
                    soundLimit = MAX_ROWS
                },
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
        val sorted = OneShotDisplay.sortedForDisplay(state.groups, favorites)
        val filtered = if (trimmedQuery.isEmpty()) {
            sorted
        } else {
            sorted.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            filtered.take(soundLimit).forEach { group ->
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
        if (filtered.size > soundLimit) {
            OutlinedButton(
                onClick = { soundLimit += MAX_ROWS },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.show_more, minOf(MAX_ROWS, filtered.size - soundLimit)))
            }
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
private fun AmbiencePanel(
    library: Library,
    ambience: AmbienceState,
    onSelectEnvironment: (Environment?, TimeOfDay) -> Unit,
    onTimeOfDay: (TimeOfDay) -> Unit,
    onLayerEnabled: (String, Boolean) -> Unit,
    onLayerGain: (String, Float) -> Unit,
    onWeather: (Weather) -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val environments = library.environments
        if (environments.isEmpty()) {
            Text(
                stringResource(R.string.ambience_no_environments),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            return
        }
        // Libraries can hold hundreds of environments: search narrows them,
        // the chips page in batches so the FlowRow stays bounded.
        var envQuery by rememberSaveable { mutableStateOf("") }
        var envLimit by rememberSaveable { mutableIntStateOf(MAX_ROWS) }
        OutlinedTextField(
            value = envQuery,
            onValueChange = {
                envQuery = it
                envLimit = MAX_ROWS
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.sounds_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Match names the way they are built from folder names: separators
        // collapse to spaces ("env_011" finds "Env 011").
        val normalizedQuery = envQuery.trim().replace('_', ' ').replace('-', ' ')
        val visibleEnvironments = environments.filter {
            normalizedQuery.isBlank() || it.name.contains(normalizedQuery, ignoreCase = true)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            visibleEnvironments.take(envLimit).forEach { env ->
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
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (visibleEnvironments.size > envLimit) {
            OutlinedButton(
                onClick = { envLimit += MAX_ROWS },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.show_more, minOf(MAX_ROWS, visibleEnvironments.size - envLimit)))
            }
        }
        if (ambience.environmentId != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilterChip(
                    selected = ambience.timeOfDay == TimeOfDay.DAY,
                    onClick = { onTimeOfDay(TimeOfDay.DAY) },
                    label = { Text(stringResource(R.string.ambience_day)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = ambience.timeOfDay == TimeOfDay.NIGHT,
                    onClick = { onTimeOfDay(TimeOfDay.NIGHT) },
                    label = { Text(stringResource(R.string.ambience_night)) },
                    modifier = Modifier.heightIn(min = 48.dp),
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
                        modifier = Modifier.heightIn(min = 48.dp),
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
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
                                Text(layer.name, modifier = Modifier.width(96.dp))
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
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
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
private fun WheelPanel(
    music: com.example.dndsound.core.music.MusicState,
    liveMarker: com.example.dndsound.core.model.WheelPoint?,
    emptyZoneIds: Set<String>,
    desaturateEmpty: Boolean,
    onWheelDrag: (com.example.dndsound.core.model.WheelPoint) -> Unit,
    onModeChange: (MusicMode) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = music.mode == MusicMode.EXPLORATION,
                onClick = { onModeChange(MusicMode.EXPLORATION) },
                label = { Text(stringResource(R.string.mode_exploration)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = music.mode == MusicMode.BATTLE,
                onClick = { onModeChange(MusicMode.BATTLE) },
                label = { Text(stringResource(R.string.mode_battle)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        MoodWheel(
            marker = liveMarker ?: music.anchor,
            mode = music.mode,
            emptyZoneIds = emptyZoneIds,
            desaturateEmpty = desaturateEmpty,
            onPointChange = onWheelDrag,
            // Wide panels would otherwise stretch the wheel to absurd sizes.
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .padding(top = 8.dp),
        )
        // Zone signature: small "mode · sector" line, large zone name.
        val signatureZone = (liveMarker ?: music.anchor)
            ?.let { WheelZones.zoneAt(it) }
            ?: music.targetZone
        signatureZone?.let { zone ->
            val modeLabel = stringResource(
                if (music.mode == MusicMode.BATTLE) R.string.mode_battle else R.string.mode_exploration,
            )
            Text(
                zoneSubtitle(zone, modeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                zoneName(zone),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.wheel_double_tap_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TrackStatusLine(music)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        val track = music.currentTrack
        when {
            track != null -> {
                Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                // The chip tracks target/playing divergence, so it survives
                // auto-advance inside the fallback zone.
                if (music.playingZone != null && music.playingZone != music.targetZone) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            stringResource(R.string.zone_playing, zoneName(music.playingZone!!)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (music.crossfading) {
                    Text(
                        stringResource(R.string.music_crossfading),
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
            .background(AppBackdrop)
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
    onChooseZone: (trackId: String, zoneId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoneChooserTrack by remember { mutableStateOf<Track?>(null) }
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
        val unplaced = library.tracks.filter { it.position == null }
        if (unplaced.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_unplaced, unplaced.size)) }
            item {
                Text(
                    stringResource(R.string.library_unplaced_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(unplaced) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "• ${track.title}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { zoneChooserTrack = track },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.action_choose_zone))
                    }
                }
            }
        }
        val placed = library.tracks.filter { it.position != null }
        if (placed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_tracks, placed.size)) }
            items(placed) { track ->
                Text(
                    "• ${track.title} — ${zoneName(WheelZones.zoneAt(track.position!!))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (library.environments.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_environments, library.environments.size)) }
            items(library.environments) { env ->
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
        }
        if (library.oneShots.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_one_shots, library.oneShots.size)) }
            items(library.oneShots) { shot ->
                Text(
                    "• ${shot.name} — ${shot.category.name.lowercase()} ×${shot.variants.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
    zoneChooserTrack?.let { track ->
        ZoneChooserDialog(
            track = track,
            onPick = { zoneId ->
                onChooseZone(track.id, zoneId)
                zoneChooserTrack = null
            },
            onDismiss = { zoneChooserTrack = null },
        )
    }
}

/** All 25 zones grouped: neutral, sectors with both tiers, then transitions. */
@Composable
private fun ZoneChooserDialog(
    track: Track,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.zones_dialog_title)} · ${track.title}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ZoneRow(WheelZone.Neutral, onPick)
                Mood.entries.forEach { mood ->
                    Text(
                        stringResource(moodLabelRes(mood)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    Tier.entries.forEach { tier ->
                        ZoneRow(WheelZone.Sector(mood, tier), onPick)
                    }
                }
                Text(
                    stringResource(R.string.zones_group_transitions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                transitions().forEach { zone -> ZoneRow(zone, onPick) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun transitions(): List<WheelZone> = buildList {
    val moods = Mood.entries
    for (i in moods.indices) {
        add(WheelZonePalette.transitionZone(moods[i], moods[(i + 1) % moods.size]) ?: continue)
    }
}

@Composable
private fun ZoneRow(zone: WheelZone, onPick: (String) -> Unit) {
    TextButton(
        onClick = { onPick(zone.id) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
    ) {
        Text(zoneName(zone))
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
