package com.mangareader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangareader.data.db.Bookmark
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun MangaDetailsScreen(
    mangaDir: String,
    mangaName: String,
    coverPath: String?,
    chaptersRead: Int,
    totalChapters: Int,
    currentTag: String?,
    currentChapterIndex: Int,
    currentPageIndex: Int,
    onStartReading: () -> Unit,
    onContinueReading: () -> Unit,
    onTagChange: (String) -> Unit,
    onBack: () -> Unit,
    getBookmarks: (String) -> Flow<List<Bookmark>>,
    onDeleteBookmark: (Int) -> Unit,
    onBookmarkClick: (chapterDir: String, pageIndex: Int) -> Unit,
) {
    val bookmarks by getBookmarks(mangaDir).collectAsState(initial = emptyList())
    val hasProgress = currentChapterIndex > 0 || currentPageIndex > 0

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize()) {
            // Cover image (250dp) with gradient scrim
            item {
                Box(Modifier.fillMaxWidth().height(250.dp)) {
                    if (coverPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverPath)
                                .size(600, 500)
                                .crossfade(true)
                                .build(),
                            contentDescription = mangaName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.MenuBook, null,
                                Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    // Gradient scrim at bottom for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            ),
                    )

                    // Manga name overlaid on cover
                    Text(
                        text = mangaName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 60.dp, bottom = 16.dp),
                    )

                    // Back button in top-left overlay
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            }

            // Stats row: chapter count, tag, progress
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem(
                        icon = Icons.Default.MenuBook,
                        value = "$chaptersRead / $totalChapters",
                        label = "Глав",
                    )
                    StatItem(
                        icon = Icons.Default.Percent,
                        value = if (totalChapters > 0) "${(chaptersRead * 100 / totalChapters)}%" else "0%",
                        label = "Прогресс",
                    )
                    StatItem(
                        icon = Icons.Default.Label,
                        value = when (currentTag) {
                            "reading" -> "Читаю"
                            "completed" -> "Прочитано"
                            "dropped" -> "Брошено"
                            "planned" -> "Запланировано"
                            else -> "Нет"
                        },
                        label = "Статус",
                    )
                }
            }

            // Tag selector: FilterChip row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        listOf(
                            "reading" to "Читаю",
                            "completed" to "Прочитано",
                            "dropped" to "Брошено",
                            "planned" to "Запланировано",
                        )
                    ) { (key, label) ->
                        FilterChip(
                            selected = currentTag == key,
                            onClick = { onTagChange(key) },
                            label = { Text(label) },
                        )
                    }
                    if (currentTag != null) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { onTagChange("") },
                                label = { Text("Убрать") },
                            )
                        }
                    }
                }
            }

            // "Начать чтение" / "Продолжить чтение" full-width button
            item {
                Button(
                    onClick = if (hasProgress) onContinueReading else onStartReading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        if (hasProgress) Icons.Default.PlayArrow else Icons.Default.AutoStories,
                        null,
                        Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasProgress) "Продолжить чтение" else "Начать чтение",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Bookmarks section header
            item {
                Text(
                    text = "Закладки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }

            // Bookmarks list or empty state
            if (bookmarks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BookmarkBorder, null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Нет закладок",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(items = bookmarks, key = { it.id }) { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark.chapterDir, bookmark.pageIndex) },
                        onDelete = { onDeleteBookmark(bookmark.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookmarkItem(bookmark: Bookmark, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Bookmark, null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.chapterName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Стр. ${bookmark.pageIndex + 1}  ·  ${formatRelativeTime(bookmark.createdAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete, "Удалить",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} мин. назад"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} ч. назад"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} дн. назад"
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp))
    }
}
