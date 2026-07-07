package com.thehbc.bilimusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.ui.theme.BiliMusicTheme
import java.util.Locale

/**
 * 全屏播放器内容。
 * 由外层 ModalBottomSheet 包裹，skipPartiallyExpanded = true 使其直接全屏弹出。
 * 遵循 MD3 BottomSheet 规范：顶部 DragHandle 由 ModalBottomSheet 自动提供。
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerScreenContent(
            state = state,
            onPlayPause = viewModel::togglePlayPause,
            onSeek = viewModel::seekTo,
            onNext = { viewModel.skipNext() },
            onPrevious = viewModel::skipPrevious,
            onToggleFavorite = viewModel::toggleFavorite,
            onCycleRepeatMode = viewModel::cyclePlayMode,
            onPlaySong = { viewModel.playSong(it) },
            onRemoveSong = { viewModel.removeSongById(it) },
            onMoveSong = { from, to -> viewModel.moveSong(from, to) }
        )
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // 给底部的控制器留出空间
        )
    }
}

@Composable
fun PlayerScreenContent(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onPlaySong: (com.thehbc.bilimusic.data.model.Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onMoveSong: (Int, Int) -> Unit
) {
    val song = state.currentSong
    val coverColor = state.currentPlaylist?.coverColor
        ?: MaterialTheme.colorScheme.primaryContainer
    var showQueueSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 标题行 ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                var showInfoDialog by remember { mutableStateOf(false) }
                
                if (showQueueSheet) {
                    QueueBottomSheet(
                        state = state,
                        onDismiss = { showQueueSheet = false },
                        onPlaySong = onPlaySong,
                        onRemoveSong = onRemoveSong,
                        onMoveSong = onMoveSong
                    )
                }

                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        title = { Text("音频信息") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val hasParts = !song?.parentTitle.isNullOrEmpty()
                                if (hasParts) {
                                    Text("标题: ${song?.parentTitle ?: "未知"}")
                                    Text("p数: ${song?.page ?: 1}")
                                    Text("p名: ${song?.partTitle ?: song?.title ?: "未知"}")
                                } else {
                                    Text("标题: ${song?.title ?: "未知"}")
                                }
                                Text("BV号: ${song?.bvid ?: "未知"}")
                                val bitrateStr = state.audioBitrate?.let { "${it / 1000} kbps" } ?: "未知"
                                Text("比特率: $bitrateStr")
                                val sampleRateStr = state.audioSampleRate?.let { "${it} Hz" } ?: "未知"
                                Text("采样率: $sampleRateStr")
                                val channelsStr = state.audioChannels?.toString() ?: "未知"
                                Text("声道: $channelsStr")
                                Text("编码格式: ${state.audioCodec ?: "未知"}")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("确定")
                            }
                        }
                    )
                }

                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多选项",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("查看音频信息") },
                        onClick = { 
                            menuExpanded = false
                            showInfoDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("在B站打开") },
                        onClick = {
                            menuExpanded = false
                            val currentSong = song
                            if (currentSong != null && !currentSong.bvid.isNullOrEmpty()) {
                                val pageIndex = (currentSong.page ?: 1) - 1
                                val appUriStr = if (currentSong.parentTitle != null) {
                                    "bilibili://video/${currentSong.bvid}?page=$pageIndex"
                                } else {
                                    "bilibili://video/${currentSong.bvid}"
                                }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUriStr))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "未安装哔哩哔哩客户端", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "当前无播放歌曲或缺失B站视频ID", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── 封面大图 ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) // 改为 B站标准的 16:9 比例
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            coverColor,
                            coverColor.copy(alpha = 0.65f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!song?.albumArtUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song?.albumArtUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.weight(1.2f))

        // ── 歌曲信息 + 收藏按钮 ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = song?.title ?: "未知歌曲",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = buildString {
                    append(song?.artist ?: "未知歌手")
                    if (!song?.parentTitle.isNullOrEmpty()) {
                        append(" · ")
                        append(song?.parentTitle)
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Default.Favorite
                                  else Icons.Default.FavoriteBorder,
                    contentDescription = if (state.isFavorite) "取消收藏" else "收藏",
                    tint = if (state.isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 进度条 ────────────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = state.progress,
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(state.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.durationMs > 0) formatDuration(state.durationMs) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 播放控制行 ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 播放模式单按钮循环
            IconButton(onClick = onCycleRepeatMode) {
                val icon = if (state.isShuffled) {
                    Icons.Default.Shuffle
                } else if (state.repeatMode == RepeatMode.ONE) {
                    Icons.Default.RepeatOne
                } else if (state.repeatMode == RepeatMode.ALL) {
                    Icons.Default.Repeat
                } else {
                    Icons.Default.FormatListNumbered
                }
                
                Icon(
                    icon,
                    contentDescription = "播放模式",
                    tint = if (state.repeatMode == RepeatMode.OFF && !state.isShuffled) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary,
                )
            }
            // 上一首
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            // 播放/暂停 — 大圆形填充按钮（MD3 FilledIconButton）
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause
                                  else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            // 下一首
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            // 队列 (触发底窗)
            IconButton(onClick = { showQueueSheet = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "队列",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        // ── 辅助操作行 ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                val currentSong = song
                if (currentSong != null) {
                    val bvid = currentSong.bvid
                    if (!bvid.isNullOrEmpty()) {
                        val shareUrl = if (currentSong.parentTitle != null && currentSong.page != null) {
                            "https://www.bilibili.com/video/$bvid?p=${currentSong.page}"
                        } else {
                            "https://www.bilibili.com/video/$bvid"
                        }
                        val shareText = buildString {
                            append("我在 BiliMusic 上听这首歌，推荐给你：\n")
                            if (!currentSong.parentTitle.isNullOrEmpty()) {
                                append("🎵 ${currentSong.parentTitle} - ${currentSong.title}")
                            } else {
                                append("🎵 ${currentSong.title}")
                            }
                            if (!currentSong.artist.isNullOrEmpty()) {
                                append(" - ${currentSong.artist}")
                            }
                            append("\n链接：$shareUrl")
                        }
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "分享歌曲")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "无法分享该歌曲，缺少 B 站视频 ID (BV号)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "当前未播放任何歌曲", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = "歌词",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Preview(name = "PlayerScreen · Light", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PlayerScreenLightPreview() {
    BiliMusicTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PlayerScreenContent(
                state = PlayerState(
                    currentSong = com.thehbc.bilimusic.data.model.Song("1", "测试歌曲", "歌手名", "04:30"),
                    isPlaying = true,
                    progress = 0.3f,
                    repeatMode = RepeatMode.OFF
                ),
                onPlayPause = {},
                onSeek = {},
                onNext = {},
                onPrevious = {},
                onToggleFavorite = {},
                onCycleRepeatMode = {},
                onPlaySong = {},
                onRemoveSong = {},
                onMoveSong = { _, _ -> }
            )
        }
    }
}

@Preview(name = "PlayerScreen · Dark", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PlayerScreenDarkPreview() {
    BiliMusicTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PlayerScreenContent(
                state = PlayerState(
                    currentSong = com.thehbc.bilimusic.data.model.Song("1", "测试歌曲", "歌手名", "04:30"),
                    isPlaying = false,
                    progress = 0.3f,
                    repeatMode = RepeatMode.ALL
                ),
                onPlayPause = {},
                onSeek = {},
                onNext = {},
                onPrevious = {},
                onToggleFavorite = {},
                onCycleRepeatMode = {},
                onPlaySong = {},
                onRemoveSong = {},
                onMoveSong = { _, _ -> }
            )
        }
    }
}
