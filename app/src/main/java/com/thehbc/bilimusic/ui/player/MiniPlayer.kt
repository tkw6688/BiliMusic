package com.thehbc.bilimusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.ui.theme.BiliMusicTheme

/**
 * 底部迷你播放器栏。
 * 放置在 Scaffold 的 bottomBar Column 中，NavigationBar 之上。
 * 仅在 currentSong != null 时显示。
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 封面占位图
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(78.dp) // 16:9 ratio
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            state.currentPlaylist?.coverColor
                                ?: MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!song.albumArtUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = song.albumArtUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // 歌曲信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 播放/暂停
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause
                                      else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 下一首
                IconButton(onClick = onSkipNextClick) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 播放进度细条 (替换自带的 LinearProgressIndicator 以去除 MD3 默认的末尾小圆点)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = state.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "MiniPlayer · Light", showBackground = true, widthDp = 360)
@Composable
private fun MiniPlayerLightPreview() {
    BiliMusicTheme(darkTheme = false) {
        MiniPlayer(
            state = PlayerState(
                currentSong = Song("1", "局域网测试曲目", "本地网络", "00:00"),
                currentPlaylist = Playlist("1", "网络测试歌单", 1, Color(0xFFFB7299)),
                isPlaying = true,
                progress = 0.4f,
            ),
            onTap = {},
            onPlayPauseClick = {},
            onSkipNextClick = {},
        )
    }
}

@Preview(name = "MiniPlayer · Dark", showBackground = true, widthDp = 360)
@Composable
private fun MiniPlayerDarkPreview() {
    BiliMusicTheme(darkTheme = true) {
        MiniPlayer(
            state = PlayerState(
                currentSong = Song("1", "局域网测试曲目", "本地网络", "00:00"),
                currentPlaylist = Playlist("1", "网络测试歌单", 1, Color(0xFFFB7299)),
                isPlaying = false,
                progress = 0.4f,
            ),
            onTap = {},
            onPlayPauseClick = {},
            onSkipNextClick = {},
        )
    }
}
