package com.thehbc.bilimusic.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<LocalPlaylist>>

    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): LocalPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylist): Long

    @Delete
    suspend fun deletePlaylist(playlist: LocalPlaylist)

    @Query("SELECT * FROM local_playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<LocalPlaylistItem>>
    
    @Query("SELECT * FROM local_playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getItemsForPlaylistSync(playlistId: Long): List<LocalPlaylistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<LocalPlaylistItem>)

    @Delete
    suspend fun deleteItem(item: LocalPlaylistItem)
    
    @Query("DELETE FROM local_playlist_items WHERE playlistId = :playlistId AND id = :itemId")
    suspend fun deleteItemById(playlistId: Long, itemId: Long)

    @Update
    suspend fun updateItems(items: List<LocalPlaylistItem>)
    
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM local_playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: Long): Int
}
