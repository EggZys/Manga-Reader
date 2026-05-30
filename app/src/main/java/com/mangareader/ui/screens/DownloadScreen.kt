package com.mangareader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangareader.data.network.ChapterDownloadStatus
import com.mangareader.data.network.ChapterInfo
import com.mangareader.data.network.DownloadProgress
import com.mangareader.data.network.MangaSearchResult
import com.mangareader.ui.components.EmptyState
import com.mangareader.ui.viewmodel.DownloadScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    state: DownloadScreenState,
    onBack: () -> Unit,
    onToggleChapter: (Float) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onStartDownload: () -> Unit,
) {
    val manga = state.manga ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        manga.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (!state.isDownloading && !state.downloadComplete) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Выбрано: ${state.selectedChapters.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = onStartDownload,
                            enabled = state.selectedChapters.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Скачать")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Manga info header
            MangaHeader(manga = manga)

            // Chapter selection toolbar
            if (!state.isDownloading && !state.downloadComplete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Все", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onSelectNone,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Нет", fontSize = 13.sp)
                    }
                }
            }

            when {
                state.isLoadingChapters -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Загрузка глав...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.chapters.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.MenuBook,
                        title = "Главы не найдены",
                    )
                }
                state.isDownloading || state.downloadComplete -> {
                    // Download progress view
                    DownloadProgressView(
                        chapters = state.chapters,
                        progress = state.downloadProgress,
                        isDownloading = state.isDownloading,
                        isComplete = state.downloadComplete,
                    )
                }
                else -> {
                    // Chapter grid
                    ChapterSelectionGrid(
                        chapters = state.chapters,
                        selectedChapters = state.selectedChapters,
                        onToggleChapter = onToggleChapter,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaHeader(manga: MangaSearchResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(Modifier.height(90.dp)) {
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (manga.coverThumbUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(manga.coverThumbUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.MenuBook, null,
                        Modifier.size(32.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = manga.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${manga.chapters_count} глав",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChapterSelectionGrid(
    chapters: List<ChapterInfo>,
    selectedChapters: Set<Float>,
    onToggleChapter: (Float) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(85.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gridItems(chapters, key = { it.numberFloat }) { chapter ->
            val isSelected = selectedChapters.contains(chapter.numberFloat)
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable { onToggleChapter(chapter.numberFloat)}
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = chapter.displayNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!chapter.name.isNullOrBlank()) {
                        Text(
                            text = chapter.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Checkmark
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressView(
    chapters: List<ChapterInfo>,
    progress: Map<String, DownloadProgress>,
    isDownloading: Boolean,
    isComplete: Boolean,
) {
    val totalChapters = chapters.size
    val doneChapters = progress.values.count {
        it.status == ChapterDownloadStatus.DONE || it.status == ChapterDownloadStatus.SKIPPED
    }
    val overallProgress = if (totalChapters > 0) doneChapters.toFloat() / totalChapters else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // Overall progress
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isComplete) "Загрузка завершена" else "Загрузка...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$doneChapters / $totalChapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val animProgress by animateFloatAsState(targetValue = overallProgress, label = "progress")
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isComplete) Color(0xFFA6E3A1) else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Chapter list with progress
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chapters, key = { it.numberFloat }) { chapter ->
                val chProgress = progress[chapter.displayNumber]
                ChapterProgressItem(chapter = chapter, progress = chProgress)
            }
        }
    }
}

@Composable
private fun ChapterProgressItem(
    chapter: ChapterInfo,
    progress: DownloadProgress?,
) {
    val status = progress?.status ?: ChapterDownloadStatus.QUEUED
    val statusColor = when (status) {
        ChapterDownloadStatus.DONE -> Color(0xFFA6E3A1)
        ChapterDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        ChapterDownloadStatus.ERROR -> MaterialTheme.colorScheme.error
        ChapterDownloadStatus.QUEUED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ChapterDownloadStatus.SKIPPED -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (status) {
        ChapterDownloadStatus.DONE -> "${progress?.downloadedPages ?: 0} стр"
        ChapterDownloadStatus.DOWNLOADING -> "${progress?.downloadedPages ?: 0} / ${progress?.totalPages ?: 0}"
        ChapterDownloadStatus.ERROR -> "Ошибка"
        ChapterDownloadStatus.QUEUED -> "В очереди"
        ChapterDownloadStatus.SKIPPED -> "Пропущено"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (status) {
                        ChapterDownloadStatus.DONE -> Icons.Default.CheckCircle
                        ChapterDownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                        ChapterDownloadStatus.ERROR -> Icons.Default.Error
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = statusColor,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Глава ${chapter.displayNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Medium,
            )
        }

        // Per-chapter progress bar
        if (status == ChapterDownloadStatus.DOWNLOADING && progress != null && progress.totalPages > 0) {
            val chProgress by animateFloatAsState(
                targetValue = progress.downloadedPages.toFloat() / progress.totalPages,
                label = "ch_progress",
            )
            LinearProgressIndicator(
                progress = { chProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
