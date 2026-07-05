package com.thehbc.bilimusic.ui.player

import com.thehbc.bilimusic.data.model.Song

internal fun isQueueSwipeDeleteEnabled(
    isCurrentlyPlaying: Boolean,
    isAnyItemDragging: Boolean
): Boolean = !isCurrentlyPlaying && !isAnyItemDragging

internal fun shouldAllowQueueDragStart(hasOpenSwipeItem: Boolean): Boolean = !hasOpenSwipeItem

internal fun shouldAnimateQueueItemPlacement(isDragging: Boolean): Boolean = !isDragging

internal fun shouldSyncLocalQueue(
    externalQueue: List<Song>,
    localQueue: List<Song>,
    isDragging: Boolean
): Boolean {
    if (isDragging) return false
    return externalQueue.map(Song::id) != localQueue.map(Song::id)
}



internal fun normalizeMoveTargetIndex(itemCount: Int, requestedToIndex: Int): Int {
    if (itemCount <= 0) return 0
    return requestedToIndex.coerceIn(0, itemCount - 1)
}

internal fun reorderQueue(
    queue: List<Song>,
    fromIndex: Int,
    requestedToIndex: Int
): List<Song> {
    if (fromIndex !in queue.indices) return queue

    val targetIndex = normalizeMoveTargetIndex(
        itemCount = queue.size,
        requestedToIndex = requestedToIndex
    )
    if (fromIndex == targetIndex) return queue

    val reordered = queue.toMutableList()
    val movedSong = reordered.removeAt(fromIndex)
    reordered.add(targetIndex, movedSong)
    return reordered
}
