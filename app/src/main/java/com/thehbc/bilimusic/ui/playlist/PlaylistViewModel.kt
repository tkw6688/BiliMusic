package com.thehbc.bilimusic.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.data.repository.LocalPlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val apiService: BiliApiService,
    private val localPlaylistRepository: LocalPlaylistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (playlistId.startsWith("local_")) {
                    // ── 本地自建歌单：从 Room 读取 ──
                    val localId = playlistId.removePrefix("local_").toLongOrNull()
                    if (localId == null) {
                        _error.value = "无效的本地歌单 ID"
                        return@launch
                    }
                    val items = localPlaylistRepository.getItemsForPlaylistSync(localId)
                    _songs.value = items.map { item ->
                        Song(
                            id        = item.id.toString(),
                            bvid      = item.bvid,
                            cid       = item.cid,
                            title     = item.title,
                            artist    = item.artist,
                            duration  = item.durationStr,
                            albumArtUrl = item.albumArtUrl,
                            mediaUri  = null
                        )
                    }
                } else {
                    // ── B站收藏夹：调网络 API ──
                    val mediaId = playlistId.toLongOrNull() ?: run {
                        _error.value = "无效的收藏夹 ID"
                        return@launch
                    }
                    val response = apiService.getFavResources(
                        mediaId  = mediaId,
                        pageNum  = 1,
                        pageSize = 20
                    )
                    if (response.code == 0) {
                        _songs.value = response.data?.medias?.map { media ->
                            val durationSeconds = media.duration ?: 0
                            val minutes = durationSeconds / 60
                            val seconds = durationSeconds % 60
                            Song(
                                id          = media.id.toString(),
                                bvid        = media.bvid,
                                cid         = media.ugc?.first_cid,
                                title       = media.title ?: "未知歌曲",
                                artist      = media.upper?.name ?: "未知歌手",
                                duration    = String.format("%02d:%02d", minutes, seconds),
                                albumArtUrl = media.cover,
                                mediaUri    = null
                            )
                        } ?: emptyList()
                    } else {
                        _error.value = response.message
                    }
                }
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            apiService: BiliApiService,
            localPlaylistRepository: LocalPlaylistRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistViewModel(apiService, localPlaylistRepository) as T
            }
    }
}
