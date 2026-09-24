package com.example.dndsound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dndsound.ui.MainScreen
import com.example.dndsound.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DnDSoundApp).container
        setContent {
            DnDSoundTheme {
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
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun DnDSoundTheme(content: @Composable () -> Unit) {
    // Tavern/parchment dark palette is designed in the UI stage; for now a
    // simple dark Material scheme is enough.
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
