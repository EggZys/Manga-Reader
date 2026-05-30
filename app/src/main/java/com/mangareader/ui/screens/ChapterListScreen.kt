package com.mangareader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangareader.data.Chapter
import com.mangareader.data.db.ReadingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    mangaDir: String,
    mangaName: String,
    onBack: () -> Unit,
    onChapterClick: (index: Int, chapters: List<Chapter>) -> Unit,
    getChapters: suspend (String) -> List<Chapter>,
    getProgress: suspend (String) -> ReadingProgress?,
) {
    var chapters by remember { mutableStateOf(emptyList<Chapter>()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentDir = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mangaDir) {
        chapters = withContext(Dispatchers.IO) { getChapters(mangaDir) }
        currentDir.value = withContext(Dispatchers.IO) { getProgress(mangaDir)?.chapterDir }
        isLoading = false
    }

    val currentPath = currentDir.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mangaName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (chapters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет глав", color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = chapters,
                    key = { _, ch -> ch.dirPath },
                    contentType = { _, _ -> 0 },
                ) { index, chapter ->
                    val isCurrent = chapter.dirPath == currentPath
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .then(
                                if (isCurrent) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable { onChapterClick(index, chapters) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chapter.name,
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
