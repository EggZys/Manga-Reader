package com.mangareader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangareader.ui.components.EmptyState
import com.mangareader.ui.components.ShimmerLibraryGrid
import com.mangareader.ui.theme.MochaGreen
import com.mangareader.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    onMangaClick: (mangaDir: String, mangaName: String, coverPath: String?) -> Unit,
    onContinueReading: (mangaDir: String, mangaName: String, coverPath: String?, chIndex: Int, pageIndex: Int) -> Unit,
    onSortChange: (SortBy) -> Unit,
    onTagChange: (mangaDir: String, tag: String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterTagChange: (String?) -> Unit,
    onRefresh: () -> Unit = {},
    onOnlineSearch: () -> Unit = {},
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = (state as? LibraryState.Ready)?.searchQuery ?: "",
                    onQueryChange = onSearchQueryChange,
                    onSearch = { showSearch = false },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Поиск манги...") },
                    leadingIcon = {
                        IconButton(onClick = { showSearch = false; onSearchQueryChange("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                        }
                    },
                    trailingIcon = {
                        if ((state as? LibraryState.Ready)?.searchQuery?.isNotEmpty() == true) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {}
            } else {
                TopAppBar(
                    title = { Text("Manga Library") },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(text = { Text("По названию") }, onClick = { onSortChange(SortBy.NAME); showSortMenu = false })
                                DropdownMenuItem(text = { Text("По последнему прочитанному") }, onClick = { onSortChange(SortBy.LAST_READ); showSortMenu = false })
                                DropdownMenuItem(text = { Text("По прогрессу") }, onClick = { onSortChange(SortBy.PROGRESS); showSortMenu = false })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        floatingActionButton = {
            if (!showSearch) {
                ExtendedFloatingActionButton(
                    onClick = onOnlineSearch,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Онлайн")
                }
            }
        },
    ) { padding ->
        when (state) {
            is LibraryState.Loading -> {
                ShimmerLibraryGrid(Modifier.fillMaxSize().padding(padding))
            }
            is LibraryState.Ready -> {
                if (state.mangas.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.MenuBook,
                        title = if (state.searchQuery.isNotBlank()) "Ничего не найдено" else "Библиотека пуста",
                        subtitle = if (state.searchQuery.isBlank()) "Добавьте мангу в папку Documents/manga" else null,
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = onRefresh,
                        modifier = Modifier.padding(padding),
                    ) {
                        Column {
                            // Tag filter chips
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    FilterChip(
                                        selected = state.filterTag == null,
                                        onClick = { onFilterTagChange(null) },
                                        label = { Text("Все") },
                                    )
                                }
                                items(state.availableTags) { tag ->
                                    val label = when (tag) {
                                        "reading" -> "Читаю"
                                        "completed" -> "Прочитано"
                                        "dropped" -> "Брошено"
                                        "planned" -> "Запланировано"
                                        else -> tag
                                    }
                                    FilterChip(
                                        selected = state.filterTag == tag,
                                        onClick = { onFilterTagChange(tag) },
                                        label = { Text(label) },
                                    )
                                }
                            }

                            // Continue reading banner
                            state.continueReading?.let { cr ->
                                ContinueReadingCard(
                                    mangaName = cr.manga.manga.name,
                                    chapterName = cr.manga.progress?.chapterName ?: "",
                                    coverPath = cr.manga.progress?.coverPath,
                                    onClick = {
                                        onContinueReading(
                                            cr.manga.manga.dirPath,
                                            cr.manga.manga.name,
                                            cr.manga.progress?.coverPath,
                                            cr.chapterIndex,
                                            cr.pageIndex,
                                        )
                                    },
                                )
                            }

                            // Manga grid
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(150.dp),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(
                                    items = state.mangas,
                                    key = { it.manga.dirPath },
                                    contentType = { "manga" },
                                ) { mwp ->
                                    MangaGridCard(
                                        name = mwp.manga.name,
                                        coverUri = mwp.manga.coverPath,
                                        progress = if (mwp.totalChapters > 0) mwp.chaptersRead.toFloat() / mwp.totalChapters else 0f,
                                        chaptersText = "${mwp.chaptersRead}/${mwp.totalChapters}",
                                        tag = mwp.tag,
                                        onClick = { onMangaClick(mwp.manga.dirPath, mwp.manga.name, mwp.manga.coverPath) },
                                        onTagChange = { onTagChange(mwp.manga.dirPath, it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    mangaName: String,
    chapterName: String,
    coverPath: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = onClick,
    ) {
        Row(Modifier.height(88.dp)) {
            if (coverPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverPath)
                        .size(88, 88)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Продолжить чтение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    mangaName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                Icons.Default.PlayArrow, null,
                Modifier.align(Alignment.CenterVertically).padding(end = 14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MangaGridCard(
    name: String,
    coverUri: String?,
    progress: Float,
    chaptersText: String,
    tag: String?,
    onClick: () -> Unit,
    onTagChange: (String) -> Unit,
) {
    var showTagMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(coverUri != null) }
    var hasError by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUri != null && !hasError) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUri)
                            .size(300, 440)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onSuccess = { isLoading = false },
                        onError = { isLoading = false; hasError = true },
                    )
                }
                if (isLoading && !hasError) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp)
                }
                if (hasError || coverUri == null) {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(48.dp).align(Alignment.Center), tint = MaterialTheme.colorScheme.outline)
                }

                // Tag badge with improved styling
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { showTagMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            when (tag) {
                                "reading" -> "📖 Читаю"
                                "completed" -> "✅ Прочитано"
                                "dropped" -> "❌ Брошено"
                                "planned" -> "📋 Запланировано"
                                else -> "+"
                            },
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    DropdownMenu(expanded = showTagMenu, onDismissRequest = { showTagMenu = false }) {
                        listOf("reading" to "Читаю", "completed" to "Прочитано", "dropped" to "Брошено", "planned" to "Запланировано").forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { onTagChange(key); showTagMenu = false })
                        }
                        if (tag != null) {
                            DropdownMenuItem(text = { Text("Убрать") }, onClick = { onTagChange(""); showTagMenu = false })
                        }
                    }
                }
            }

            Column(Modifier.padding(12.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (progress >= 1f) MochaGreen else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    chaptersText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
