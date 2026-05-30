package com.mangareader.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.mangareader.data.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    readingMode: ReadingMode,
    brightness: Float,
    onReadingModeChange: (ReadingMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var currentOrientation by remember {
        mutableIntStateOf(context.resources.configuration.orientation)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Настройки чтения", style = MaterialTheme.typography.titleMedium)

            // Reading mode
            Text("Режим чтения", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = readingMode == ReadingMode.PAGES,
                    onClick = { onReadingModeChange(ReadingMode.PAGES) },
                    label = { Text("Страницы") },
                    leadingIcon = if (readingMode == ReadingMode.PAGES) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = readingMode == ReadingMode.WEBTOON,
                    onClick = { onReadingModeChange(ReadingMode.WEBTOON) },
                    label = { Text("Вебтун") },
                    leadingIcon = if (readingMode == ReadingMode.WEBTOON) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }

            // Brightness
            Text("Яркость", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessLow, null, Modifier.size(20.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    valueRange = 0.1f..1f,
                )
                Icon(Icons.Default.BrightnessHigh, null, Modifier.size(20.dp))
            }

            // Orientation
            Text("Ориентация", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    onClick = {
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    },
                    label = { Text("Портрет") },
                )
                FilterChip(
                    selected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    onClick = {
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },
                    label = { Text("Ландшафт") },
                )
                FilterChip(
                    selected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    onClick = {
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    },
                    label = { Text("Авто") },
                )
            }
        }
    }
}
