package com.thehbc.bilimusic.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_active_queue_items")
data class LocalActiveQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val bvid: String?,
    val cid: Long?,
    val title: String,
    val artist: String,
    val durationStr: String,
    val albumArtUrl: String?,
    val parentTitle: String? = null,
    val page: Int? = null,
    val partTitle: String? = null,
    val sortOrder: Int = 0
)
