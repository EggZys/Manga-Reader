package com.mangareader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangareader.data.db.ReadingProgress
import com.mangareader.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<ReadingProgress>,
    onContinueReading: (ReadingProgress) -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "История пуста",
            subtitle = "Начните читать мангу",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = history,
                key = { it.mangaDir },
            ) { progress ->
                HistoryListItem(
                    progress = progress,
                    onClick = { onContinueReading(progress) },
                )
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    progress: ReadingProgress,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (progress.coverPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(progress.coverPath)
                        .size(56, 56)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = progress.mangaName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = progress.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatRelativeTime(progress.lastReadAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Icon(
                Icons.Default.History,
                null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> {
            val min = (diff / 60_000).toInt()
            when {
                min % 10 == 1 && min % 100 != 11 -> "$min минуту назад"
                min % 10 in 2..4 && min % 100 !in 12..14 -> "$min минуты назад"
                else -> "$min минут назад"
            }
        }
        diff < 86_400_000 -> {
            val hours = (diff / 3_600_000).toInt()
            when {
                hours % 10 == 1 && hours % 100 != 11 -> "$hours час назад"
                hours % 10 in 2..4 && hours % 100 !in 12..14 -> "$hours часа назад"
                else -> "$hours часов назад"
            }
        }
        diff < 604_800_000 -> {
            val days = (diff / 86_400_000).toInt()
            when {
                days % 10 == 1 && days % 100 != 11 -> "$days день назад"
                days % 10 in 2..4 && days % 100 !in 12..14 -> "$days дня назад"
                else -> "$days дней назад"
            }
        }
        else -> {
            val days = (diff / 86_400_000).toInt()
            "$days дней назад"
        }
    }
}
