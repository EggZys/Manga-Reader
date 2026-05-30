package com.mangareader.ui.screens

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangareader.data.Chapter
import com.mangareader.data.Page
import com.mangareader.data.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ReaderScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapters: List<Chapter>,
    startIndex: Int,
    startPage: Int,
    mangaDir: String,
    mangaName: String,
    coverPath: String?,
    targetChapterDir: String? = null,
    initialReadingMode: ReadingMode = ReadingMode.PAGES,
    onBack: () -> Unit,
    getPages: suspend (String) -> List<Page>,
    saveProgress: suspend (chapterDir: String, chapterName: String, pageIndex: Int, totalPages: Int) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    var chapterIndex by remember { mutableIntStateOf(startIndex) }
    var currentPage by remember { mutableIntStateOf(startPage) }
    var pages by remember { mutableStateOf(emptyList<Page>()) }
    var showControls by remember { mutableStateOf(false) }
    var isImmersive by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var readingMode by remember { mutableStateOf(initialReadingMode) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var pendingPage by remember { mutableStateOf<String?>(null) }

    // Resolve chapter index from targetChapterDir when chapters arrive late
    LaunchedEffect(chapters.size, targetChapterDir) {
        if (chapters.isNotEmpty() && targetChapterDir != null) {
            val resolved = chapters.indexOfFirst { it.dirPath == targetChapterDir }
            if (resolved >= 0 && resolved != chapterIndex) {
                chapterIndex = resolved
            }
        }
    }

    // Load pages when chapter changes (also re-fires when chapters list arrives)
    LaunchedEffect(chapterIndex, chapters.size) {
        val ch = chapters.getOrNull(chapterIndex) ?: return@LaunchedEffect
        pages = getPages(ch.dir.absolutePath)
        when (pendingPage) {
            "last" -> {
                currentPage = if (pages.isEmpty()) 0 else pages.lastIndex
                pendingPage = null
            }
            "first" -> {
                currentPage = 0
                pendingPage = null
            }
            else -> if (chapterIndex != startIndex) currentPage = 0
        }
    }

    // Auto-save progress with debounce
    LaunchedEffect(currentPage, chapterIndex) {
        delay(2000)
        val ch = chapters.getOrNull(chapterIndex) ?: return@LaunchedEffect
        saveProgress(ch.dir.absolutePath, ch.name, currentPage, pages.size)
    }

    // Preload adjacent pages
    LaunchedEffect(currentPage, chapterIndex) {
        scope.launch(Dispatchers.IO) {
            val loader = coil.Coil.imageLoader(context)
            for (offset in 1..3) {
                val idx = currentPage + offset
                if (idx in pages.indices) {
                    val f = pages[idx].file
                    if (f.exists() && f.length() > 0) {
                        try {
                            loader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(f)
                                    .size(screenWidthPx, screenHeightPx)
                                    .build()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            // Preload first page of next chapter
            if (currentPage >= pages.size - 3) {
                val nextCh = chapters.getOrNull(chapterIndex + 1)
                if (nextCh != null) {
                    val nextPages = getPages(nextCh.dir.absolutePath)
                    nextPages.firstOrNull()?.file?.let { f ->
                        if (f.exists() && f.length() > 0) {
                            try {
                                loader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(f)
                                        .size(screenWidthPx, screenHeightPx)
                                        .build()
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    // Immersive mode
    LaunchedEffect(isImmersive) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isImmersive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                val controller = WindowCompat.getInsetsController(it, it.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (pages.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.outline, strokeWidth = 2.dp)
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (readingMode == ReadingMode.WEBTOON) {
            WebtoonContent(
                pages = pages,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        } else {
            // PAGES mode: HorizontalPager for swipe between pages
            // key(chapterIndex) ensures pager is recreated on chapter change
            key(chapterIndex) {
                val safePage = currentPage.coerceIn(0, pages.lastIndex)
                val pagerState = rememberPagerState(
                    initialPage = safePage,
                    pageCount = { pages.size },
                )

                // Sync pager -> currentPage (user swiped in pager)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { page ->
                        currentPage = page
                    }
                }

                // Sync currentPage -> pager (slider changed currentPage)
                LaunchedEffect(currentPage) {
                    val target = currentPage.coerceIn(0, pages.lastIndex)
                    if (pagerState.currentPage != target) {
                        pagerState.animateScrollToPage(target)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    ZoomablePage(
                        file = pages[page].file,
                        pageNum = page + 1,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                    )
                }

                // Tap zones: left 25% = prev page, right 25% = next page, center = toggle controls
                // Uses awaitEachGesture which does NOT consume down events, so HorizontalPager swipe works
                var tapAction by remember { mutableIntStateOf(0) } // -1=prev, 0=toggle, 1=next
                LaunchedEffect(tapAction) {
                    when (tapAction) {
                        -1 -> {
                            if (currentPage > 0) {
                                pagerState.animateScrollToPage(currentPage - 1)
                            } else if (chapterIndex > 0) {
                                chapterIndex--
                                pendingPage = "last"
                            }
                        }
                        1 -> {
                            if (currentPage < pages.lastIndex) {
                                pagerState.animateScrollToPage(currentPage + 1)
                            } else if (chapterIndex < chapters.lastIndex) {
                                chapterIndex++
                                pendingPage = "first"
                            }
                        }
                    }
                    tapAction = 0
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(pages.size, chapterIndex) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                val x = up.position.x
                                val w = size.width.toFloat()
                                tapAction = when {
                                    x < w * 0.25f -> -1
                                    x > w * 0.75f -> 1
                                    else -> {
                                        showControls = !showControls
                                        isImmersive = !showControls
                                        0
                                    }
                                }
                            }
                        }
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chapters.getOrNull(chapterIndex)?.name ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${currentPage + 1} / ${pages.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                ),
            )
        }

        // Bottom bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomAppBar(containerColor = Color.Black.copy(alpha = 0.7f)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Глава: ${chapters.getOrNull(chapterIndex)?.name ?: ""}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { currentPage = it.toInt() },
                        onValueChangeFinished = {},
                        valueRange = 0f..pages.lastIndex.coerceAtLeast(0).toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                if (chapterIndex > 0) {
                                    chapterIndex--
                                    pendingPage = "last"
                                }
                            },
                            enabled = chapterIndex > 0,
                        ) {
                            Text("<< Назад", color = if (chapterIndex > 0) Color.White else MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            "${currentPage + 1} / ${pages.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        TextButton(
                            onClick = {
                                if (chapterIndex < chapters.lastIndex) {
                                    chapterIndex++
                                    pendingPage = "first"
                                }
                            },
                            enabled = chapterIndex < chapters.lastIndex,
                        ) {
                            Text("Вперед >>", color = if (chapterIndex < chapters.lastIndex) Color.White else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        // Brightness overlay
        if (brightness < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1f - brightness))
            )
        }

        // Settings sheet
        if (showSettings) {
            ReaderSettingsSheet(
                readingMode = readingMode,
                brightness = brightness,
                onReadingModeChange = { readingMode = it },
                onBrightnessChange = { brightness = it },
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun WebtoonContent(
    pages: List<Page>,
    screenWidthPx: Int,
    screenHeightPx: Int,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.Top,
    ) {
        items(
            count = pages.size,
            key = { pages[it].filePath },
        ) { index ->
            val file = pages[index].file
            if (!file.exists() || file.length() == 0L) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .size(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.BrokenImage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .size(screenWidthPx, screenHeightPx)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    file: File,
    pageNum: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom/pan when page changes
    LaunchedEffect(file.absolutePath) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val animScale by animateFloatAsState(targetValue = scale, label = "scale")

    if (!file.exists() || file.length() == 0L) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Text("Страница $pageNum не найдена", color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    // transformable handles only pinch zoom — no drag conflict with HorizontalPager
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale > 1f) {
            val maxOffX = ((newScale - 1f) * screenWidthPx) / 2f
            val maxOffY = ((newScale - 1f) * screenHeightPx) / 2f
            offsetX = (offsetX + panChange.x).coerceIn(-maxOffX, maxOffX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOffY, maxOffY)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        scale = newScale
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        var isLoading by remember(file.absolutePath) { mutableStateOf(true) }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(false)
                .size(screenWidthPx, screenHeightPx)
                .allowHardware(false)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit,
            onSuccess = { isLoading = false },
            onError = {
                isLoading = false
                Log.e(TAG, "Coil: ${file.name}", it.result.throwable)
            },
        )

        if (isLoading) {
            CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.outline, strokeWidth = 2.dp)
        }
    }
}
