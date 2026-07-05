package com.thehbc.bilimusic.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.thehbc.bilimusic.data.model.Song
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onMoveSong: (Int, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "播放队列 (${state.queue.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val lazyListState = rememberLazyListState()
            val localQueue = remember { mutableStateListOf<Song>() }
            var dragOriginalIndex by remember { mutableIntStateOf(-1) }

            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val targetIndex = normalizeMoveTargetIndex(
                    itemCount = localQueue.size,
                    requestedToIndex = to.index
                )
                if (from.index != targetIndex && from.index in localQueue.indices) {
                    val item = localQueue.removeAt(from.index)
                    localQueue.add(targetIndex, item)
                }
            }

            LaunchedEffect(state.queue) {
                if (shouldSyncLocalQueue(state.queue, localQueue, reorderState.isAnyItemDragging)) {
                    localQueue.clear()
                    localQueue.addAll(state.queue)
                }
            }

            val currentSongId = state.currentSong?.id

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(
                    items = localQueue,
                    key = { _, song -> song.id }
                ) { _, song ->
                    val isCurrentlyPlaying = song.id == currentSongId

                    ReorderableItem(
                        state = reorderState,
                        key = song.id,
                        modifier = if (shouldAnimateQueueItemPlacement(reorderState.isAnyItemDragging)) {
                            Modifier.animateItem()
                        } else {
                            Modifier
                        }
                    ) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            label = "drag_elevation"
                        )

                        if (isCurrentlyPlaying) {
                            QueueSongRow(
                                song = song,
                                isCurrentlyPlaying = true,
                                elevation = elevation,
                                dragHandleModifier = null,
                                onClick = { onPlaySong(song) }
                            )
                        } else {
                            QueueSongRow(
                                song = song,
                                isCurrentlyPlaying = false,
                                elevation = elevation,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragOriginalIndex =
                                            localQueue.indexOfFirst { it.id == song.id }
                                    },
                                    onDragStopped = {
                                        val finalIndex =
                                            localQueue.indexOfFirst { it.id == song.id }
                                        if (dragOriginalIndex != -1 && finalIndex != -1 &&
                                            dragOriginalIndex != finalIndex
                                        ) {
                                            onMoveSong(dragOriginalIndex, finalIndex)
                                        }
                                        dragOriginalIndex = -1
                                    }
                                ),
                                onClick = { onPlaySong(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSongRow(
    song: Song,
    isCurrentlyPlaying: Boolean,
    elevation: Dp,
    dragHandleModifier: Modifier?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!song.albumArtUrl.isNullOrEmpty()) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentlyPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (dragHandleModifier != null) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = dragHandleModifier
                    .size(32.dp)
                    .padding(4.dp)
            )
        } else {
            Spacer(Modifier.size(32.dp))
        }
    }
}
