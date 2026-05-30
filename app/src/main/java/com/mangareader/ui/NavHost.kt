package com.mangareader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mangareader.data.Chapter
import com.mangareader.data.ReadingMode
import com.mangareader.data.ThemeMode
import com.mangareader.ui.screens.ChapterListScreen
import com.mangareader.ui.screens.DownloadScreen
import com.mangareader.ui.screens.HistoryScreen
import com.mangareader.ui.screens.LibraryScreen
import com.mangareader.ui.screens.MangaDetailsScreen
import com.mangareader.ui.screens.ReaderScreen
import com.mangareader.ui.screens.LogsScreen
import com.mangareader.ui.screens.LogsScreen
import com.mangareader.ui.screens.SearchScreen
import com.mangareader.ui.screens.SettingsScreen
import com.mangareader.ui.viewmodel.DownloadViewModel
import com.mangareader.ui.viewmodel.LibraryViewModel
import com.mangareader.ui.viewmodel.MangaWithProgress

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("library", "Библиотека", Icons.Default.MenuBook),
    BottomTab("history", "История", Icons.Default.History),
    BottomTab("settings", "Настройки", Icons.Default.Settings),
    BottomTab("logs", "Логи", Icons.Default.BugReport),
)

private val routesWithBottomBar = setOf("library", "history", "settings", "logs")

@Composable
fun AppNavHost(vm: LibraryViewModel = viewModel(), downloadVm: DownloadViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val themeMode by vm.themeMode.collectAsState(ThemeMode.DARK)
    val dynamicColor by vm.dynamicColor.collectAsState(true)
    val readingMode by vm.readingMode.collectAsState(ReadingMode.PAGES)
    val mangaFolder by vm.mangaFolder.collectAsState("")

    var selectedMangaDir by rememberSaveable { mutableStateOf("") }
    var selectedMangaName by rememberSaveable { mutableStateOf("") }
    var selectedChapters by remember { mutableStateOf(emptyList<Chapter>()) }
    var selectedChapterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var coverPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChapterDir by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute in routesWithBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("library") {
                val onMangaClick = remember<(String, String, String?) -> Unit> {
                    { dir, name, cover ->
                        selectedMangaDir = dir; selectedMangaName = name; coverPath = cover
                        nav.navigate("manga_details")
                    }
                }
                val onContinue = remember<(String, String, String?, Int, Int) -> Unit> {
                    { dir, name, cover, ch, pg ->
                        selectedMangaDir = dir; selectedMangaName = name; coverPath = cover
                        selectedChapterIndex = ch; selectedPageIndex = pg
                        nav.navigate("reader")
                    }
                }
                LibraryScreen(
                    state = state,
                    onMangaClick = onMangaClick,
                    onContinueReading = onContinue,
                    onSortChange = remember { { vm.setSortBy(it) } },
                    onTagChange = remember { { dir, tag -> vm.setTag(dir, tag) } },
                    onSearchQueryChange = remember { { vm.setSearchQuery(it) } },
                    onFilterTagChange = remember { { vm.setFilterTag(it) } },
                    onRefresh = remember { { vm.forceRefresh() } },
                    onOnlineSearch = remember { { nav.navigate("search") } },
                )
            }

            composable("history") {
                val history by vm.getRecentHistory().collectAsState(initial = emptyList())
                HistoryScreen(
                    history = history,
                    onContinueReading = { progress ->
                        selectedMangaDir = progress.mangaDir
                        selectedMangaName = progress.mangaName
                        coverPath = progress.coverPath
                        selectedChapterIndex = 0
                        selectedPageIndex = progress.pageIndex
                        selectedChapters = emptyList()
                        selectedChapterDir = progress.chapterDir
                        nav.navigate("reader")
                    },
                )
            }

            composable("settings") {
                SettingsScreen(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    readingMode = readingMode,
                    mangaFolder = mangaFolder,
                    onThemeModeChange = { vm.setThemeMode(it) },
                    onDynamicColorChange = { vm.setDynamicColor(it) },
                    onReadingModeChange = { vm.setReadingMode(it) },
                    onMangaFolderChange = { vm.setMangaFolder(it) },
                )
            }

            composable("logs") {
                LogsScreen(onBack = { nav.popBackStack() })
            }

            composable(
                "manga_details",
                enterTransition = { slideInHorizontally { it } },
                exitTransition = { slideOutHorizontally { -it } },
                popEnterTransition = { slideInHorizontally { -it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                var mangaDetails by remember { mutableStateOf<MangaWithProgress?>(null) }

                LaunchedEffect(selectedMangaDir) {
                    mangaDetails = vm.getMangaDetails(selectedMangaDir)
                }

                MangaDetailsScreen(
                    mangaDir = selectedMangaDir,
                    mangaName = selectedMangaName,
                    coverPath = coverPath,
                    chaptersRead = mangaDetails?.chaptersRead ?: 0,
                    totalChapters = mangaDetails?.totalChapters ?: 0,
                    currentTag = mangaDetails?.tag,
                    currentChapterIndex = mangaDetails?.progress?.let { p ->
                        mangaDetails?.manga?.dir?.listFiles()
                            ?.filter { it.isDirectory && it.name.startsWith("chapter_") }
                            ?.sortedBy { it.name }
                            ?.indexOfFirst { it.absolutePath == p.chapterDir }
                            ?.let { if (it >= 0) it else 0 }
                    } ?: 0,
                    currentPageIndex = mangaDetails?.progress?.pageIndex ?: 0,
                    onStartReading = remember {
                        {
                            selectedChapterIndex = 0; selectedPageIndex = 0
                            selectedChapterDir = null
                            nav.navigate("chapters")
                        }
                    },
                    onContinueReading = remember {
                        {
                            selectedChapterIndex = mangaDetails?.progress?.let { p ->
                                mangaDetails?.manga?.dir?.listFiles()
                                    ?.filter { it.isDirectory && it.name.startsWith("chapter_") }
                                    ?.sortedBy { it.name }
                                    ?.indexOfFirst { it.absolutePath == p.chapterDir }
                                    ?.let { if (it >= 0) it else 0 }
                            } ?: 0
                            selectedPageIndex = mangaDetails?.progress?.pageIndex ?: 0
                            nav.navigate("chapters")
                        }
                    },
                    onTagChange = remember { { tag -> vm.setTag(selectedMangaDir, tag) } },
                    onBack = remember { { nav.popBackStack() } },
                    getBookmarks = remember { { dir -> vm.getBookmarksForManga(dir) } },
                    onDeleteBookmark = remember { { id -> vm.deleteBookmark(id) } },
                    onBookmarkClick = remember {
                        { chapterDir, pageIndex ->
                            selectedChapterIndex = 0
                            selectedPageIndex = pageIndex
                            selectedChapterDir = chapterDir
                            nav.navigate("reader")
                        }
                    },
                )
            }

            composable(
                "chapters",
                enterTransition = { slideInHorizontally { it } },
                exitTransition = { slideOutHorizontally { -it } },
                popEnterTransition = { slideInHorizontally { -it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                val onClick = remember<(Int, List<Chapter>) -> Unit> {
                    { index, chapters ->
                        selectedChapters = chapters; selectedChapterIndex = index; selectedPageIndex = 0
                        selectedChapterDir = null
                        nav.navigate("reader")
                    }
                }
                val onBack = remember<() -> Unit> { { nav.popBackStack() } }
                val getCh = remember<suspend (String) -> List<Chapter>> { { vm.getChapters(it) } }
                val getPr = remember<suspend (String) -> com.mangareader.data.db.ReadingProgress?> { { vm.getProgress(it) } }

                ChapterListScreen(
                    mangaDir = selectedMangaDir,
                    mangaName = selectedMangaName,
                    onBack = onBack,
                    onChapterClick = onClick,
                    getChapters = getCh,
                    getProgress = getPr,
                )
            }

            composable(
                "reader",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                val onBack = remember<() -> Unit> { { nav.popBackStack() } }
                val getPages = remember<suspend (String) -> List<com.mangareader.data.Page>> { { vm.getPages(it) } }
                val saveProg = remember<suspend (String, String, Int, Int) -> Unit> {
                    { chDir, chName, page, total ->
                        vm.saveProgress(selectedMangaDir, selectedMangaName, chDir, chName, page, total, coverPath)
                    }
                }

                LaunchedEffect(Unit) {
                    if (selectedChapters.isEmpty()) {
                        selectedChapters = vm.getChapters(selectedMangaDir)
                    }
                }

                ReaderScreen(
                    chapters = selectedChapters,
                    startIndex = selectedChapterIndex,
                    startPage = selectedPageIndex,
                    mangaDir = selectedMangaDir,
                    mangaName = selectedMangaName,
                    coverPath = coverPath,
                    targetChapterDir = selectedChapterDir,
                    initialReadingMode = readingMode,
                    onBack = onBack,
                    getPages = getPages,
                    saveProgress = saveProg,
                )
            }

            composable(
                "search",
                enterTransition = { slideInHorizontally { it } },
                exitTransition = { slideOutHorizontally { -it } },
                popEnterTransition = { slideInHorizontally { -it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                val searchState by downloadVm.searchState.collectAsState()
                SearchScreen(
                    state = searchState,
                    onQueryChange = { downloadVm.updateSearchQuery(it) },
                    onSearch = { downloadVm.searchManga() },
                    onMangaSelect = { manga ->
                        downloadVm.selectManga(manga)
                        nav.navigate("download")
                    },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                "download",
                enterTransition = { slideInHorizontally { it } },
                exitTransition = { slideOutHorizontally { -it } },
                popEnterTransition = { slideInHorizontally { -it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                val downloadState by downloadVm.downloadState.collectAsState()
                DownloadScreen(
                    state = downloadState,
                    onBack = { nav.popBackStack() },
                    onToggleChapter = { downloadVm.toggleChapter(it) },
                    onSelectAll = { downloadVm.selectAllChapters() },
                    onSelectNone = { downloadVm.selectNoneChapters() },
                    onStartDownload = { downloadVm.startDownload() },
                )
            }
        }
    }
}
