package com.mangareader.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mangareader.data.network.LogCollector
import com.mangareader.data.network.LogEntry
import com.mangareader.data.network.LogLevel
import com.mangareader.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
) {
    val logs by LogCollector.logs.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Логи (${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLogs(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Экспорт логов")
                    }
                    IconButton(onClick = { LogCollector.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Delete,
                title = "Логов пока нет",
                subtitle = "Логи появятся при поиске и загрузке манги",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logs, key = { "${it.timestamp}_${it.tag}_${it.hashCode()}" }) { entry ->
                    LogItem(entry)
                }
            }
        }
    }
}

private fun exportLogs(context: Context) {
    val logFile = LogCollector.getLogFile() ?: return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        logFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "MangaReader Logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться логами"))
}

@Composable
private fun LogItem(entry: LogEntry) {
    val bgColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        LogLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    }
    val textColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tagColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = tagColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = entry.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = entry.level.name,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = tagColor.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                lineHeight = 14.sp,
            )

            if (entry.throwable != null) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (expanded) "Скрыть" else "Стектрейс",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (expanded) {
                    Text(
                        text = entry.throwable.stackTraceToString(),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}
