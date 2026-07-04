package com.thehbc.bilimusic.ui.library

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.data.repository.LocalPlaylistRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val apiService: BiliApiService,
    private val authManager: AuthManager,
    private val localPlaylistRepository: LocalPlaylistRepository
) : ViewModel() {

    // B站收藏夹
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 本地自建歌单
    val localPlaylists: StateFlow<List<com.thehbc.bilimusic.data.local.room.LocalPlaylist>> = 
        localPlaylistRepository.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 当前选中的歌单 (可以是 B站收藏夹，未来也可以扩展成本地歌单)
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    init {
        // 监听 uid 变化，当 uid 有值时自动拉取收藏夹
        viewModelScope.launch {
            authManager.uidFlow.distinctUntilChanged().collectLatest { uid ->
                if (uid != null && uid > 0) {
                    fetchPlaylists(uid)
                } else {
                    _playlists.value = emptyList() // 未登录清空列表
                }
            }
        }
    }

    private fun fetchPlaylists(uid: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getCreatedFavFolders(upMid = uid)
                if (response.code == 0) {
                    val list = response.data?.list?.map { folder ->
                        Playlist(
                            id = folder.id.toString(),
                            name = folder.title ?: "未命名收藏夹",
                            songCount = folder.media_count ?: 0,
                            coverColor = Color(0xFFFB7299), // 默认 B 站粉
                            description = "B站收藏夹",
                            coverUrl = null // 如果有封面可以接入
                        )
                    } ?: emptyList()
                    _playlists.value = list
                } else {
                    _error.value = response.message
                }
            } catch (e: Exception) {
                _error.value = "获取收藏夹失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        _selectedPlaylist.value = playlist
    }

    fun createLocalPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            localPlaylistRepository.createPlaylist(name, description)
        }
    }

    fun deleteLocalPlaylist(playlist: com.thehbc.bilimusic.data.local.room.LocalPlaylist) {
        viewModelScope.launch {
            localPlaylistRepository.deletePlaylist(playlist)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val uid = authManager.uidFlow.firstOrNull()
            if (uid != null && uid > 0) {
                fetchPlaylists(uid)
            }
        }
    }

    companion object {
        fun provideFactory(
            apiService: BiliApiService,
            authManager: AuthManager,
            localPlaylistRepository: LocalPlaylistRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(apiService, authManager, localPlaylistRepository) as T
                }
            }
    }
}
