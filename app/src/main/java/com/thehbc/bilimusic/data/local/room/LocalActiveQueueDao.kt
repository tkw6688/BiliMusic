package com.thehbc.bilimusic.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocalActiveQueueDao {
    @Query("SELECT * FROM local_active_queue_items ORDER BY sortOrder ASC")
    suspend fun getActiveQueue(): List<LocalActiveQueueItem>

    @Query("DELETE FROM local_active_queue_items")
    suspend fun clearActiveQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<LocalActiveQueueItem>)

    @Transaction
    suspend fun saveActiveQueue(items: List<LocalActiveQueueItem>) {
        clearActiveQueue()
        insertItems(items)
    }
}
