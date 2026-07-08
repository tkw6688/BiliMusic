package com.thehbc.bilimusic.ui.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val quality by viewModel.preferredAudioQuality.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.updateCacheSize()
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("首选播放音质") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.savePreferredAudioQuality(0)
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = quality == 0, onClick = {
                            viewModel.savePreferredAudioQuality(0)
                            showQualityDialog = false
                        })
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("极佳音质 (优先最高)", style = MaterialTheme.typography.bodyLarge)
                            Text("优先匹配 Hi-Res 无损 / 杜比全景声 / 320k", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.savePreferredAudioQuality(1)
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = quality == 1, onClick = {
                            viewModel.savePreferredAudioQuality(1)
                            showQualityDialog = false
                        })
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("标准音质 (约 128k)", style = MaterialTheme.typography.bodyLarge)
                            Text("平衡音质与流量消耗，加载更顺畅", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.savePreferredAudioQuality(2)
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = quality == 2, onClick = {
                            viewModel.savePreferredAudioQuality(2)
                            showQualityDialog = false
                        })
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("省流模式 (64k 低码率)", style = MaterialTheme.typography.bodyLarge)
                            Text("音频文件极小，弱网秒开，最省流量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("确定要清除本地缓存的歌曲数据吗？离线后已缓存的歌曲将无法播放。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    isClearingCache = true
                    viewModel.clearCache {
                        isClearingCache = false
                        Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (isClearingCache) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在清理中...") },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放与设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "播放设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 音质 ListItem
            val qualityText = when (quality) {
                1 -> "标准音质 (约 128k)"
                2 -> "省流模式 (64k)"
                else -> "极佳音质 (优先最高)"
            }
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("首选播放音质") },
                    supportingContent = { Text(qualityText) },
                    leadingContent = { Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showQualityDialog = true }
                )
            }

            Text(
                text = "本地缓存与存储",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 缓存清理 ListItem
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("清理音频缓存") },
                    supportingContent = { Text("已缓存大小: ${formatBytes(cacheSize)}") },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showClearCacheDialog = true }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.2f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}
