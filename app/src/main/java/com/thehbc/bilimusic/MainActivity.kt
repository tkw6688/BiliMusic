package com.thehbc.bilimusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thehbc.bilimusic.ui.library.LibraryScreen
import com.thehbc.bilimusic.ui.library.LibraryViewModel
import com.thehbc.bilimusic.ui.player.MiniPlayer
import com.thehbc.bilimusic.ui.player.PlayerScreen
import com.thehbc.bilimusic.ui.player.PlayerState
import com.thehbc.bilimusic.ui.player.PlayerViewModel
import com.thehbc.bilimusic.ui.playlist.PlaylistScreen
import com.thehbc.bilimusic.ui.playlist.PlaylistViewModel
import com.thehbc.bilimusic.ui.profile.ProfileScreen
import com.thehbc.bilimusic.ui.auth.LoginScreen
import com.thehbc.bilimusic.ui.auth.AuthViewModel
import com.thehbc.bilimusic.ui.profile.ProfileViewModel
import com.thehbc.bilimusic.ui.profile.AboutScreen
import com.thehbc.bilimusic.ui.theme.BiliMusicTheme

// 定义在文件级别，避免在 Composable 内部每次重建
private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("library", "库",   Icons.Default.LibraryMusic),
    NavItem("profile", "我的", Icons.Default.Person),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 动态申请通知权限，Android 12及以下不需要申请，默认开启
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }

        setContent {
            BiliMusicTheme {
                val appContainer = (application as BiliMusicApp).container
                val playerViewModel: PlayerViewModel = viewModel(
                    factory = PlayerViewModel.provideFactory(
                        application = application,
                        apiService = appContainer.biliApiService,
                        playerPrefsManager = appContainer.playerPrefsManager,
                        activeQueueDao = appContainer.appDatabase.localActiveQueueDao()
                    )
                )
                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.provideFactory(
                        apiService = appContainer.biliApiService,
                        authManager = appContainer.authManager,
                        localPlaylistRepository = appContainer.localPlaylistRepository
                    )
                )
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.provideFactory(
                        apiService = appContainer.biliApiService,
                        authManager = appContainer.authManager
                    )
                )
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.provideFactory(
                        authManager = appContainer.authManager
                    )
                )
                val playlistViewModel: PlaylistViewModel = viewModel(
                    factory = PlaylistViewModel.provideFactory(
                        apiService = appContainer.biliApiService,
                        localPlaylistRepository = appContainer.localPlaylistRepository,
                        authManager = appContainer.authManager
                    )
                )

                BiliMusicApp(
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    authViewModel = authViewModel,
                    profileViewModel = profileViewModel,
                    playlistViewModel = playlistViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliMusicApp(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    playlistViewModel: PlaylistViewModel
) {
    val navController = rememberNavController()
    val playerState by playerViewModel.state.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 监听错误事件并展示 Snackbar
    LaunchedEffect(Unit) {
        playerViewModel.errorEvent.collect { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    // ✅ 修复1：在 bottomBar 外部收集导航状态
    // 这样 NavigationBar 只在路由变化时重组，与 playerState 解耦
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // ✅ 提取 String 类型（Stable），避免传入 NavDestination（Unstable）导致无法跳过重组
    val currentRoute = navBackStackEntry?.destination?.route

    // ✅ 修复2：remember 包裹导航 lambda，防止因 BiliMusicApp 重组而重建
    val onNavigate: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // AppBottomBar 是独立 Composable，
            // 其中 AppNavigationBar 只依赖 currentRoute，可跳过不必要的重组
            AppBottomBar(
                playerState = playerState,
                currentRoute = currentRoute,
                onMiniPlayerTap = { showPlayer = true },
                onPlayPauseClick = playerViewModel::togglePlayPause,
                onSkipNextClick = playerViewModel::skipNext,
                onNavigate = onNavigate,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // ✅ 修复3：消费掉 innerPadding 中的 WindowInsets，
                // 防止内层 Scaffold 的 TopAppBar 再次叠加状态栏高度
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable("library") {
                LibraryScreen(
                    libraryViewModel = libraryViewModel,
                    onPlaylistClick = { playlist ->
                        libraryViewModel.selectPlaylist(playlist)
                        navController.navigate("playlist")
                    },
                )
            }
            composable("playlist") {
                val playlist by libraryViewModel.selectedPlaylist.collectAsState()
                playlist?.let { pl ->
                    PlaylistScreen(
                        playlist = pl,
                        viewModel = playlistViewModel,
                        playerState = playerState,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song ->
                            playerViewModel.playSong(song, pl, playlistViewModel.songs.value)
                            showPlayer = true
                        },
                        onPlayAll = { songs ->
                            if (songs.isNotEmpty()) {
                                playerViewModel.playSong(songs.first(), pl, songs)
                                showPlayer = true
                            }
                        },
                        onInsertNext = { song -> playerViewModel.insertNext(song) },
                        onAppendToQueue = { song -> playerViewModel.appendToQueue(song) },
                        onAddClick = {
                            val localId = pl.id.removePrefix("local_")
                            navController.navigate("add_songs/$localId")
                        }
                    )
                }
            }
            composable("add_songs/{playlistId}") { backStackEntry ->
                val playlistIdStr = backStackEntry.arguments?.getString("playlistId")
                val playlistId = playlistIdStr?.toLongOrNull()
                if (playlistId != null) {
                    com.thehbc.bilimusic.ui.playlist.AddSongsScreen(
                         playlistId = playlistId,
                         viewModel = playlistViewModel,
                         onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onLoginClick = { navController.navigate("login") },
                    onAboutClick = { navController.navigate("about") }
                )
            }
            composable("about") {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onLoginSuccess = {
                        navController.popBackStack()
                        // TODO: 可以在这里刷新数据
                    }
                )
            }
        }
    }

    // MD3 ModalBottomSheet 全屏播放器
    if (showPlayer) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlayer = false },
            sheetState = sheetState,
        ) {
            PlayerScreen(viewModel = playerViewModel)
        }
    }
}

// ✅ 独立 Composable：MiniPlayer + NavigationBar
// Column 本身是内联的，但 AppNavigationBar 作为独立 Composable 是可跳过的（Skippable）
@Composable
private fun AppBottomBar(
    playerState: PlayerState,
    currentRoute: String?,
    onMiniPlayerTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column {
        if (playerState.currentSong != null) {
            MiniPlayer(
                state = playerState,
                onTap = onMiniPlayerTap,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = onSkipNextClick,
            )
        }
        // ✅ AppNavigationBar 仅依赖 currentRoute + onNavigate
        // 当 playerState.progress 变化时，currentRoute 不变 → Compose 完美跳过此组件
        AppNavigationBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
        )
    }
}

// ✅ 参数全部稳定（Stable），Compose 可在父级重组时完整跳过此 Composable
@Composable
private fun AppNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
