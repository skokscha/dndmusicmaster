package com.example.dndsound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.example.dndsound.ui.LayoutMode
import com.example.dndsound.ui.MainScreen
import com.example.dndsound.ui.MainViewModel
import com.example.dndsound.ui.theme.DnDSoundTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DnDSoundApp).container
        setContent {
            val viewModel: MainViewModel = viewModel {
                MainViewModel(
                    context = this@MainActivity,
                    settingsRepository = container.settingsRepository,
                    libraryRepository = container.libraryRepository,
                    scanner = container.scanner,
                    musicEngine = container.musicEngine,
                    ambienceEngine = container.ambienceEngine,
                    oneShotEngine = container.oneShotEngine,
                )
            }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            DnDSoundTheme(settings.theme) {
                val adaptiveInfo = currentWindowAdaptiveInfoV2()
                val window = adaptiveInfo.windowSizeClass
                val layoutMode = when {
                    window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
                        LayoutMode.EXPANDED
                    window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
                        LayoutMode.MEDIUM
                    else -> LayoutMode.COMPACT
                }
                MainScreen(viewModel = viewModel, layoutMode = layoutMode)
            }
        }
    }
}
