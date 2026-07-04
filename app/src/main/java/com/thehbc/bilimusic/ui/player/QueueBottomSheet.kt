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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val targetIndex = normalizeMoveTargetIndex(
                    itemCount = localQueue.size,
                    requestedToIndex = to.index
                )
                val reorderedQueue = reorderQueue(
                    queue = localQueue,
                    fromIndex = from.index,
                    requestedToIndex = targetIndex
                )
                if (reorderedQueue != localQueue) {
                    localQueue.clear()
                    localQueue.addAll(reorderedQueue)
                }
                onMoveSong(from.index, targetIndex)
            }

            LaunchedEffect(state.queue, reorderState.isAnyItemDragging) {
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
                        modifier = Modifier.animateItem()
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
                            val songId = song.id
                            SwipeToDismissBox(
                                state = rememberSwipeToDismissBoxState(
                                    positionalThreshold = ::calculateQueueSwipeDeleteThreshold,
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            val index = localQueue.indexOfFirst { it.id == songId }
                                            if (index != -1) {
                                                localQueue.removeAt(index)
                                            }
                                            onRemoveSong(songId)
                                        }
                                        false
                                    }
                                ),
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                    }
                                }
                            ) {
                                QueueSongRow(
                                    song = song,
                                    isCurrentlyPlaying = false,
                                    elevation = elevation,
                                    dragHandleModifier = Modifier.draggableHandle(),
                                    onClick = { onPlaySong(song) }
                                )
                            }
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
