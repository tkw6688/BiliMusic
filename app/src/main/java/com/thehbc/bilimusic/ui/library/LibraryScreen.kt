package com.thehbc.bilimusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thehbc.bilimusic.data.model.Playlist

import androidx.compose.ui.draw.alpha
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNetwork = connectivityManager?.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit = {},
) {
    val playlists by libraryViewModel.playlists.collectAsState()
    val localPlaylists by libraryViewModel.localPlaylists.collectAsState()
    val isLoading by libraryViewModel.isLoading.collectAsState()
    val error by libraryViewModel.error.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    val context = LocalContext.current
    var isOffline by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        isOffline = !isNetworkAvailable(context)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("新建本地歌单") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            libraryViewModel.createLocalPlaylist(newPlaylistName)
                        }
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "我的音乐",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("刷新收藏夹") },
                                onClick = {
                                    showMenu = false
                                    libraryViewModel.refresh()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreatePlaylistDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建歌单")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && playlists.isEmpty() && localPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null && playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (localPlaylists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "本地自建歌单", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(localPlaylists, key = { "local_${it.id}" }) { localPlaylist ->
                            val pl = Playlist(
                                id = "local_${localPlaylist.id}", // Add prefix to distinguish
                                name = localPlaylist.name,
                                songCount = 0, // TODO: we need to get song count from DB
                                coverColor = Color(0xFF6750A4), // 默认本地紫色
                                description = localPlaylist.description,
                                coverUrl = localPlaylist.coverUrl
                            )
                            PlaylistCard(
                                playlist = pl,
                                onClick = { onPlaylistClick(pl) }
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "B站收藏夹", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(playlists, key = { "bili_${it.id}" }) { playlist ->
                            val isCached = remember(playlist.id) { libraryViewModel.isPlaylistCached(playlist.id) }
                            val isPlayable = !isOffline || isCached
                            PlaylistCard(
                                playlist = playlist,
                                isPlayable = isPlayable,
                                onClick = {
                                    if (isPlayable) {
                                        onPlaylistClick(playlist)
                                    } else {
                                        Toast.makeText(context, "处于离线状态，且该收藏夹未缓存", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    isPlayable: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .alpha(if (isPlayable) 1f else 0.38f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // 封面渐变区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                playlist.coverColor,
                                playlist.coverColor.copy(alpha = 0.72f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.White.copy(alpha = 0.85f),
                )
            }
            // 信息区
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (playlist.id.startsWith("local_")) "本地自建歌单" else "${playlist.songCount} 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
