package com.thehbc.bilimusic.ui.player

import com.thehbc.bilimusic.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueBottomSheetBehaviorTest {

    @Test
    fun isQueueSwipeDeleteEnabled_returnsTrue_forNormalIdleItem() {
        assertTrue(
            isQueueSwipeDeleteEnabled(
                isCurrentlyPlaying = false,
                isAnyItemDragging = false
            )
        )
    }

    @Test
    fun isQueueSwipeDeleteEnabled_returnsFalse_forCurrentSong() {
        assertFalse(
            isQueueSwipeDeleteEnabled(
                isCurrentlyPlaying = true,
                isAnyItemDragging = false
            )
        )
    }

    @Test
    fun isQueueSwipeDeleteEnabled_returnsFalse_whileDragging() {
        assertFalse(
            isQueueSwipeDeleteEnabled(
                isCurrentlyPlaying = false,
                isAnyItemDragging = true
            )
        )
    }

    @Test
    fun shouldAllowQueueDragStart_returnsFalse_whenAnotherItemIsSwipedOpen() {
        assertFalse(shouldAllowQueueDragStart(hasOpenSwipeItem = true))
    }

    @Test
    fun shouldAllowQueueDragStart_returnsTrue_whenNoItemIsSwipedOpen() {
        assertTrue(shouldAllowQueueDragStart(hasOpenSwipeItem = false))
    }

    @Test
    fun shouldAnimateQueueItemPlacement_returnsFalse_whileDragging() {
        assertFalse(shouldAnimateQueueItemPlacement(isDragging = true))
    }

    @Test
    fun shouldAnimateQueueItemPlacement_returnsTrue_whenIdle() {
        assertTrue(shouldAnimateQueueItemPlacement(isDragging = false))
    }

    @Test
    fun shouldSyncLocalQueue_returnsTrue_whenExternalQueueChangedAndNotDragging() {
        val externalQueue = listOf(song("1"), song("3"))
        val localQueue = listOf(song("1"), song("2"), song("3"))

        assertTrue(
            shouldSyncLocalQueue(
                externalQueue = externalQueue,
                localQueue = localQueue,
                isDragging = false
            )
        )
    }

    @Test
    fun shouldSyncLocalQueue_returnsFalse_whileDragging() {
        val externalQueue = listOf(song("1"), song("3"))
        val localQueue = listOf(song("1"), song("2"), song("3"))

        assertFalse(
            shouldSyncLocalQueue(
                externalQueue = externalQueue,
                localQueue = localQueue,
                isDragging = true
            )
        )
    }



    @Test
    fun normalizeMoveTargetIndex_clampsDropPositionToLastItem() {
        assertEquals(4, normalizeMoveTargetIndex(itemCount = 5, requestedToIndex = 5))
    }

    @Test
    fun reorderQueue_movesItemToNormalizedTargetIndex() {
        val reordered = reorderQueue(
            queue = listOf(song("1"), song("2"), song("3"), song("4"), song("5")),
            fromIndex = 1,
            requestedToIndex = 5
        )

        assertEquals(listOf("1", "3", "4", "5", "2"), reordered.map(Song::id))
    }

    private fun song(id: String) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        duration = "03:00"
    )
}
