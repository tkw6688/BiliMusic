package com.thehbc.bilimusic.ui.playlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.data.utils.BiliTitleParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel,
    onBack: () -> Unit
) {
    var selectedFolder by remember { mutableStateOf<Playlist?>(null) }
    val folders by viewModel.biliPlaylists.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadBiliPlaylists()
    }

    if (selectedFolder == null) {
        // Step 1: Folder selection and Manual BV input
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("添加歌曲", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // BV Input Section
                var bvid by remember { mutableStateOf("") }
                var isAdding by remember { mutableStateOf(false) }
                var statusText by remember { mutableStateOf<String?>(null) }
                var isError by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("手动输入 BV 号导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bvid,
                            onValueChange = { bvid = it.trim() },
                            label = { Text("视频 BV 号") },
                            placeholder = { Text("例如: BV1xx411c7xv") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isAdding
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (bvid.isEmpty()) return@Button
                                isAdding = true
                                statusText = "正在获取视频信息并展示..."
                                isError = false
                                viewModel.addSongByBvid(
                                    playlistId = playlistId,
                                    bvid = bvid,
                                    onSuccess = {
                                        isAdding = false
                                        Toast.makeText(context, "导入成功。", Toast.LENGTH_SHORT).show()
                                        statusText = "添加成功！"
                                        bvid = ""
                                    },
                                    onError = { err ->
                                        isAdding = false
                                        isError = true
                                        statusText = err
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bvid.isNotEmpty() && !isAdding
                        ) {
                            if (isAdding) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("解析并添加")
                        }
                        if (statusText != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = statusText!!,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    "选择 B 站收藏夹导入",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (folders.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("暂无 B 站收藏夹，请确保已登录", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(folders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${folder.songCount} 首歌曲") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                },
                                modifier = Modifier.clickable { selectedFolder = folder }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Step 2: Song selection list (matching the look of the normal list screen)
        BiliSongsSelectLayout(
            playlistId = playlistId,
            folder = selectedFolder!!,
            viewModel = viewModel,
            onBack = { selectedFolder = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiliSongsSelectLayout(
    playlistId: Long,
    folder: Playlist,
    viewModel: PlaylistViewModel,
    onBack: () -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageNum by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    val selectedSongs = remember { mutableStateListOf<Song>() }
    var existingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    fun loadPage(page: Int) {
        if (page == 1) {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        coroutineScope.launch {
            try {
                if (page == 1) {
                    existingKeys = viewModel.getExistingKeysForPlaylist(playlistId)
                }

                val response = viewModel.apiService.getFavResources(
                    mediaId = folder.id.toLong(),
                    pageNum = page,
                    pageSize = 20
                )
                if (response.code == 0) {
                    val medias = response.data?.medias ?: emptyList()
                    hasMore = response.data?.has_more ?: false

                    val mapped = coroutineScope {
                        medias.map { media ->
                            async {
                                if (media.bvid != null && media.page != null && media.page > 1) {
                                    try {
                                        val detailResponse = viewModel.apiService.getVideoDetail(media.bvid)
                                        if (detailResponse.code == 0 && detailResponse.data?.pages != null) {
                                            detailResponse.data.pages.map { pageItem ->
                                                Song(
                                                    id = "${media.id}_${pageItem.cid}",
                                                    bvid = media.bvid,
                                                    cid = pageItem.cid,
                                                    title = BiliTitleParser.cleanPageTitle(pageItem.part ?: "未知歌曲"),
                                                    artist = media.upper?.name ?: "未知歌手",
                                                    duration = String.format("%02d:%02d", pageItem.duration / 60, pageItem.duration % 60),
                                                    albumArtUrl = media.cover,
                                                    parentTitle = media.title,
                                                    page = pageItem.page,
                                                    partTitle = pageItem.part
                                                )
                                            }
                                        } else {
                                            listOf(viewModel.mapToSingleSong(media))
                                        }
                                    } catch (e: Exception) {
                                        listOf(viewModel.mapToSingleSong(media))
                                    }
                                } else {
                                    listOf(viewModel.mapToSingleSong(media))
                                }
                            }
                        }.awaitAll().flatten()
                    }

                    if (page == 1) {
                        songs = mapped
                    } else {
                        songs = songs + mapped
                    }
                } else {
                    if (page == 1) error = response.message
                }
            } catch (e: Exception) {
                if (page == 1) error = e.message
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(folder.id) {
        pageNum = 1
        hasMore = true
        selectedSongs.clear()
        songs = emptyList()
        loadPage(1)
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMore && !isLoading && !isLoadingMore) {
            pageNum += 1
            loadPage(pageNum)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(folder.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("共 ${songs.size} 首已加载", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.addSongsToLocalPlaylist(playlistId, selectedSongs)
                            Toast.makeText(context, "成功添加了 ${selectedSongs.size} 首歌曲", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        enabled = selectedSongs.isNotEmpty()
                    ) {
                        Text("确定 (${selectedSongs.size})", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && songs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && songs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("加载失败: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Header Select All line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val insertableSongs = remember(songs, existingKeys) {
                        songs.filter { !existingKeys.contains("${it.bvid}_${it.cid}") }
                    }
                    val allSelected = insertableSongs.isNotEmpty() && selectedSongs.size == insertableSongs.size
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedSongs.clear()
                            if (checked == true) {
                                selectedSongs.addAll(insertableSongs)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("全选 (${selectedSongs.size}/${insertableSongs.size})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(songs) { song ->
                        val isAlreadyInPlaylist = existingKeys.contains("${song.bvid}_${song.cid}")
                        val isChecked = selectedSongs.contains(song) || isAlreadyInPlaylist
                        val canClick = !isAlreadyInPlaylist
                        
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (canClick) {
                                                if (checked == true) selectedSongs.add(song) else selectedSongs.remove(song)
                                            }
                                        },
                                        enabled = canClick
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                if (isAlreadyInPlaylist) {
                                    Text("已添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                } else {
                                    Text(song.duration, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier = Modifier.clickable(enabled = canClick) {
                                if (isChecked) selectedSongs.remove(song) else selectedSongs.add(song)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = if (isAlreadyInPlaylist) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                else MaterialTheme.colorScheme.onSurface,
                                supportingColor = if (isAlreadyInPlaylist) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                  else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            }
        }
    }
}
