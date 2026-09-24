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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dndsound.R
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.repo.ScanState

/**
 * Stage 3 screen: onboarding (folder pick) + a debug listing of the indexed
 * library. The real three-panel UI replaces [LibraryContent] in stage 7.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onFolderPicked(uri) }

    if (settings.libraryRootUri == null) {
        OnboardingContent(
            onPickFolder = { folderPicker.launch(null) },
        )
    } else {
        LibraryContent(
            library = library,
            scanState = scanState,
            onRescan = viewModel::rescan,
            onChangeFolder = { folderPicker.launch(null) },
            onCreateStructure = viewModel::createFolderStructure,
            onDisconnect = viewModel::disconnectFolder,
        )
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

@Composable
private fun LibraryContent(
    library: Library,
    scanState: ScanState,
    onRescan: () -> Unit,
    onChangeFolder: () -> Unit,
    onCreateStructure: () -> Unit,
    onDisconnect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
                Text("• ${track.title} — ${track.mode.name.lowercase()} [${track.position.angleDeg.toInt()}°]", style = MaterialTheme.typography.bodySmall)
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
                Text("• ${shot.name} — ${shot.category.name.lowercase()} ×${shot.variants.size}", style = MaterialTheme.typography.bodySmall)
            }
            item { MoreRow(library.oneShots.size) }
        }
        if (library.weatherLoops.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_section_weather, library.weatherLoops.size)) }
            items(library.weatherLoops.entries.toList()) { (weather, files) ->
                Text("• ${weather.name.lowercase()} ×${files.size}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val MAX_ROWS = 100

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
