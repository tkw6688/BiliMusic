package com.thehbc.bilimusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.ui.player.PlayerState
import com.thehbc.bilimusic.ui.theme.BiliMusicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    viewModel: PlaylistViewModel,
    playerState: PlayerState,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onInsertNext: (Song) -> Unit,
    onAppendToQueue: (Song) -> Unit,
    onAddClick: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )
    
    val playlistState by viewModel.currentPlaylist.collectAsState()
    val playlist = playlistState
    
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    
    val context = LocalContext.current
    var activeSongMenu by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSelectDialogForSong by remember { mutableStateOf<Song?>(null) }
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var isFullLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    if (playlist == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // 监听滑动触底，自动加载下一页
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex > 0 && totalItemsNumber > 0 && lastVisibleItemIndex >= totalItemsNumber - 4
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (playlist.id.startsWith("local_")) {
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Default.Add, contentDescription = "添加歌曲")
                        }
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 封面 Header
            item {
                PlaylistHeader(
                    playlist = playlist,
                    songCount = if (playlist.id.startsWith("local_")) songs.size else playlist.songCount,
                    loadedCount = songs.size,
                    onPlayAll = {
                        if (!playlist.id.startsWith("local_")) {
                            isFullLoading = true
                            viewModel.getPlaylistSongsFull(playlist.id, songs) { fullSongs ->
                                isFullLoading = false
                                onPlayAll(fullSongs)
                            }
                        } else {
                            onPlayAll(songs)
                        }
                    },
                )
            }
            
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (error != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // 歌曲列表
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isCurrentlyPlaying = playerState.currentSong?.id == song.id,
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onClick = { onSongClick(song) },
                        onMoreClick = { activeSongMenu = song }
                    )
                }
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            // 底部留白
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (isFullLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在拉取完整歌曲列表...") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    if (activeSongMenu != null) {
        val song = activeSongMenu!!
        ModalBottomSheet(
            onDismissRequest = { activeSongMenu = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text(song.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(song.artist) }
                )
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("插播下一首") },
                    leadingContent = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onInsertNext(song)
                        activeSongMenu = null
                    }
                )
                
                ListItem(
                    headlineContent = { Text("添加至队尾") },
                    leadingContent = { Icon(Icons.Default.Queue, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onAppendToQueue(song)
                        activeSongMenu = null
                    }
                )
                
                ListItem(
                    headlineContent = { Text("添加到本地歌单") },
                    leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showPlaylistSelectDialogForSong = song
                        activeSongMenu = null
                    }
                )
                
                if (playlist.id.startsWith("local_")) {
                    val localId = playlist.id.removePrefix("local_").toLongOrNull()
                    if (localId != null) {
                        ListItem(
                            headlineContent = { Text("从歌单中删除", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                viewModel.removeSongFromPlaylist(localId, song.id.toLong())
                                activeSongMenu = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistSelectDialogForSong != null) {
        val song = showPlaylistSelectDialogForSong!!
        val containingIds by viewModel.playlistsContainingSong.collectAsState()
        
        LaunchedEffect(song.id) {
            viewModel.loadPlaylistsContainingSong(song.bvid ?: "", song.cid ?: 0L)
        }
        
        SelectLocalPlaylistDialog(
            playlists = localPlaylists,
            containingIds = containingIds,
            onDismiss = { showPlaylistSelectDialogForSong = null },
            onSelect = { localPl ->
                viewModel.addSongsToLocalPlaylist(localPl.id, listOf(song))
                Toast.makeText(context, "添加成功！", Toast.LENGTH_SHORT).show()
                showPlaylistSelectDialogForSong = null
            }
        )
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    songCount: Int,
    loadedCount: Int,
    onPlayAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 封面横幅
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.2f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            playlist.coverColor,
                            playlist.coverColor.copy(alpha = 0.55f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 封面图标卡片
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.18f),
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        // 信息区
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            val isLocal = playlist.id.startsWith("local_")
            val subtitleText = if (isLocal) {
                "$songCount 首歌曲"
            } else {
                "视频数: $songCount · 已加载 $loadedCount 首歌曲"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            // 操作按钮行
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("播放全部")
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("随机播放")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SongListItem(
    song: Song,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        headlineContent = {
            Text(
                text = song.title,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentlyPlaying) primaryColor else onSurfaceColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val subtitle = buildString {
                append(song.artist)
                if (!song.parentTitle.isNullOrEmpty()) {
                    append(" · ")
                    append(song.parentTitle)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariantColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCurrentlyPlaying)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.VolumeUp
                                  else if (isCurrentlyPlaying) Icons.Default.Pause
                                  else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrentlyPlaying) primaryColor else onSurfaceVariantColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = song.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariantColor,
                )
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(18.dp),
                        tint = onSurfaceVariantColor,
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SelectLocalPlaylistDialog(
    playlists: List<com.thehbc.bilimusic.data.local.room.LocalPlaylist>,
    containingIds: List<Long>,
    onDismiss: () -> Unit,
    onSelect: (com.thehbc.bilimusic.data.local.room.LocalPlaylist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到本地歌单") },
        text = {
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无本地歌单，请先去“媒体库”创建")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    items(playlists) { playlist ->
                        val isAlreadyContaining = containingIds.contains(playlist.id)
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { 
                                Text(
                                    if (isAlreadyContaining) "已包含此歌曲" 
                                    else playlist.description.ifEmpty { "暂无描述" }
                                ) 
                            },
                            leadingContent = {
                                Icon(Icons.Default.QueueMusic, contentDescription = null)
                            },
                            modifier = Modifier.clickable(enabled = !isAlreadyContaining) { onSelect(playlist) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = if (isAlreadyContaining) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) 
                                                else MaterialTheme.colorScheme.onSurface,
                                supportingColor = if (isAlreadyContaining) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                                 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
