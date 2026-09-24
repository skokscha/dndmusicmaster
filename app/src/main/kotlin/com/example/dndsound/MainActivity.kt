package com.example.dndsound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DnDSoundTheme {
                PlaceholderScreen()
            }
        }
    }
}

@Composable
fun DnDSoundTheme(content: @Composable () -> Unit) {
    // Tavern/parchment dark palette is designed in the UI stage; for the
    // scaffold a simple dark Material scheme is enough.
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}

@Composable
private fun PlaceholderScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1410))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = Color(0xFFE8D5B0),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.scaffold_hint),
            color = Color(0xFF9C8A6E),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    DnDSoundTheme {
        PlaceholderScreen()
    }
}
