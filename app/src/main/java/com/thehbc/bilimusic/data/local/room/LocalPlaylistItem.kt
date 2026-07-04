package com.thehbc.bilimusic.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class LocalPlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val artist: String,
    val durationStr: String,
    val albumArtUrl: String?,
    val sortOrder: Int
)
