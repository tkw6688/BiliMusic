package com.thehbc.bilimusic.data.repository

import com.thehbc.bilimusic.data.local.room.LocalPlaylist
import com.thehbc.bilimusic.data.local.room.LocalPlaylistDao
import com.thehbc.bilimusic.data.local.room.LocalPlaylistItem
import kotlinx.coroutines.flow.Flow

class LocalPlaylistRepository(private val playlistDao: LocalPlaylistDao) {

    fun getAllPlaylists(): Flow<List<LocalPlaylist>> = playlistDao.getAllPlaylists()

    suspend fun getPlaylistById(id: Long): LocalPlaylist? = playlistDao.getPlaylistById(id)

    suspend fun createPlaylist(name: String, description: String = "", coverUrl: String? = null): Long {
        val playlist = LocalPlaylist(name = name, description = description, coverUrl = coverUrl)
        return playlistDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: LocalPlaylist) {
        playlistDao.deletePlaylist(playlist)
    }

    fun getItemsForPlaylist(playlistId: Long): Flow<List<LocalPlaylistItem>> = playlistDao.getItemsForPlaylist(playlistId)

    suspend fun getItemsForPlaylistSync(playlistId: Long): List<LocalPlaylistItem> = playlistDao.getItemsForPlaylistSync(playlistId)

    suspend fun addItemsToPlaylist(playlistId: Long, items: List<LocalPlaylistItem>) {
        val currentMaxSort = playlistDao.getMaxSortOrder(playlistId)
        val itemsWithSortOrder = items.mapIndexed { index, item ->
            item.copy(sortOrder = currentMaxSort + index + 1)
        }
        playlistDao.insertItems(itemsWithSortOrder)
    }

    suspend fun removeItemsFromPlaylist(playlistId: Long, itemIds: List<Long>) {
        itemIds.forEach { itemId ->
            playlistDao.deleteItemById(playlistId, itemId)
        }
    }

    suspend fun getPlaylistIdsContainingSong(bvid: String, cid: Long): List<Long> =
        playlistDao.getPlaylistIdsContainingSong(bvid, cid)
}
