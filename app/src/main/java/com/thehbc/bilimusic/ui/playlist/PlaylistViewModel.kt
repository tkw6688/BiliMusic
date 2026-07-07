package com.thehbc.bilimusic.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.data.network.model.FavMedia
import com.thehbc.bilimusic.data.repository.LocalPlaylistRepository
import com.thehbc.bilimusic.data.utils.BiliTitleParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PlaylistViewModel(
    val apiService: BiliApiService,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val authManager: com.thehbc.bilimusic.data.local.AuthManager
) : ViewModel() {

    private val _biliPlaylists = MutableStateFlow<List<com.thehbc.bilimusic.data.model.Playlist>>(emptyList())
    val biliPlaylists: StateFlow<List<com.thehbc.bilimusic.data.model.Playlist>> = _biliPlaylists.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    val localPlaylists: StateFlow<List<com.thehbc.bilimusic.data.local.room.LocalPlaylist>> =
        localPlaylistRepository.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _playlistsContainingSong = MutableStateFlow<List<Long>>(emptyList())
    val playlistsContainingSong: StateFlow<List<Long>> = _playlistsContainingSong.asStateFlow()

    fun loadPlaylistsContainingSong(bvid: String, cid: Long) {
        viewModelScope.launch {
            _playlistsContainingSong.value = localPlaylistRepository.getPlaylistIdsContainingSong(bvid, cid)
        }
    }

    fun addSongsToLocalPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            val existingItems = localPlaylistRepository.getItemsForPlaylistSync(playlistId)
            val existingKeys = existingItems.map { "${it.bvid}_${it.cid}" }.toSet()
            val items = songs.filter { song ->
                val key = "${song.bvid}_${song.cid}"
                !existingKeys.contains(key)
            }.map { song ->
                com.thehbc.bilimusic.data.local.room.LocalPlaylistItem(
                    playlistId = playlistId,
                    bvid = song.bvid ?: "",
                    cid = song.cid ?: 0L,
                    title = song.title,
                    artist = song.artist,
                    durationStr = song.duration,
                    albumArtUrl = song.albumArtUrl,
                    parentTitle = song.parentTitle,
                    sortOrder = 0,
                    page = song.page,
                    partTitle = song.partTitle
                )
            }
            if (items.isNotEmpty()) {
                localPlaylistRepository.addItemsToPlaylist(playlistId, items)
            }
            if (currentPlaylistId == "local_$playlistId") {
                loadPlaylist("local_$playlistId")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, itemId: Long) {
        viewModelScope.launch {
            localPlaylistRepository.removeItemsFromPlaylist(playlistId, listOf(itemId))
            if (currentPlaylistId == "local_$playlistId") {
                loadPlaylist("local_$playlistId")
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentPlaylistId: String? = null
    private var currentPage = 1
    private var hasMore = false

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        currentPage = 1
        hasMore = false
        _songs.value = emptyList()
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
                            mediaUri  = null,
                            parentTitle = item.parentTitle,
                            page      = item.page,
                            partTitle = item.partTitle
                        )
                    }
                } else {
                    // ── B站收藏夹：调网络 API ──
                    val mediaId = playlistId.toLongOrNull() ?: run {
                        _error.value = "无效的收藏夹 ID"
                        return@launch
                    }
                    fetchPage(mediaId, 1, isAppend = false)
                }
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        val playlistId = currentPlaylistId ?: return
        if (playlistId.startsWith("local_") || _isLoading.value || _isLoadingMore.value || !hasMore) return

        val mediaId = playlistId.toLongOrNull() ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                fetchPage(mediaId, currentPage + 1, isAppend = true)
            } catch (e: Exception) {
                // 静默失败
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun fetchPage(mediaId: Long, pageNum: Int, isAppend: Boolean) {
        val response = apiService.getFavResources(
            mediaId  = mediaId,
            pageNum  = pageNum,
            pageSize = 20
        )
        if (response.code == 0) {
            hasMore = response.data?.has_more ?: false
            currentPage = pageNum
            val medias = response.data?.medias ?: emptyList()
            val newSongs = coroutineScope {
                medias.map { media ->
                    async {
                        if (media.bvid != null && media.page != null && media.page > 1) {
                            try {
                                val detailResponse = apiService.getVideoDetail(media.bvid)
                                if (detailResponse.code == 0 && detailResponse.data?.pages != null) {
                                    val cleanVideoTitle = media.title ?: ""
                                    val uploader = media.upper?.name ?: "未知歌手"
                                    detailResponse.data.pages.map { page ->
                                        val durationSeconds = page.duration
                                        val minutes = durationSeconds / 60
                                        val seconds = durationSeconds % 60
                                        val cleanPageTitle = BiliTitleParser.cleanPageTitle(page.part ?: "未知歌曲")
                                        Song(
                                            id          = "${media.id}_${page.cid}",
                                            bvid        = media.bvid,
                                            cid         = page.cid,
                                            title       = cleanPageTitle,
                                            artist      = uploader,
                                            duration    = String.format("%02d:%02d", minutes, seconds),
                                            albumArtUrl = media.cover,
                                            mediaUri    = null,
                                            parentTitle = cleanVideoTitle,
                                            page        = page.page,
                                            partTitle   = page.part
                                        )
                                    }
                                } else {
                                    listOf(mapToSingleSong(media))
                                }
                            } catch (e: Exception) {
                                listOf(mapToSingleSong(media))
                            }
                        } else {
                            listOf(mapToSingleSong(media))
                        }
                    }
                }.awaitAll().flatten()
            }
            if (isAppend) {
                _songs.value = _songs.value + newSongs
            } else {
                _songs.value = newSongs
            }
        } else {
            if (!isAppend) {
                _error.value = response.message
            }
        }
    }

    fun getPlaylistSongsFull(playlistId: String, currentSongs: List<Song>, onComplete: (List<Song>) -> Unit) {
        if (playlistId.startsWith("local_") || !hasMore) {
            onComplete(currentSongs)
            return
        }
        val mediaId = playlistId.toLongOrNull() ?: run {
            onComplete(currentSongs)
            return
        }
        viewModelScope.launch {
            _isLoadingMore.value = true
            val fullList = currentSongs.toMutableList()
            try {
                var page = currentPage + 1
                var more = true
                while (more) {
                    val response = apiService.getFavResources(
                        mediaId  = mediaId,
                        pageNum  = page,
                        pageSize = 20
                    )
                    if (response.code == 0) {
                        more = response.data?.has_more ?: false
                        val medias = response.data?.medias ?: emptyList()
                        val pageSongs = coroutineScope {
                            medias.map { media ->
                                async {
                                    if (media.bvid != null && media.page != null && media.page > 1) {
                                        try {
                                            val detailResponse = apiService.getVideoDetail(media.bvid)
                                            if (detailResponse.code == 0 && detailResponse.data?.pages != null) {
                                                val cleanVideoTitle = media.title ?: ""
                                                val uploader = media.upper?.name ?: "未知歌手"
                                                detailResponse.data.pages.map { pageItem ->
                                                    val durationSeconds = pageItem.duration
                                                    val minutes = durationSeconds / 60
                                                    val seconds = durationSeconds % 60
                                                    val cleanPageTitle = BiliTitleParser.cleanPageTitle(pageItem.part ?: "未知歌曲")
                                                    Song(
                                                        id          = "${media.id}_${pageItem.cid}",
                                                        bvid        = media.bvid,
                                                        cid         = pageItem.cid,
                                                        title       = cleanPageTitle,
                                                        artist      = uploader,
                                                        duration    = String.format("%02d:%02d", minutes, seconds),
                                                        albumArtUrl = media.cover,
                                                        mediaUri    = null,
                                                        parentTitle = cleanVideoTitle,
                                                        page        = pageItem.page,
                                                        partTitle   = pageItem.part
                                                    )
                                                }
                                            } else {
                                                listOf(mapToSingleSong(media))
                                            }
                                        } catch (e: Exception) {
                                            listOf(mapToSingleSong(media))
                                        }
                                    } else {
                                        listOf(mapToSingleSong(media))
                                    }
                                }
                            }.awaitAll().flatten()
                        }
                        fullList.addAll(pageSongs)
                        page++
                    } else {
                        more = false
                    }
                }
                
                _songs.value = fullList
                currentPage = page - 1
                hasMore = false
                onComplete(fullList)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadBiliPlaylists() {
        viewModelScope.launch {
            val uid = authManager.uidFlow.firstOrNull() ?: return@launch
            try {
                val response = apiService.getCreatedFavFolders(upMid = uid)
                if (response.code == 0) {
                    _biliPlaylists.value = response.data?.list?.map { folder ->
                        com.thehbc.bilimusic.data.model.Playlist(
                            id = folder.id.toString(),
                            name = folder.title ?: "未命名收藏夹",
                            songCount = folder.media_count ?: 0,
                            coverColor = androidx.compose.ui.graphics.Color(0xFFFB7299),
                            description = "B站收藏夹"
                        )
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addSongByBvid(
        playlistId: Long,
        bvid: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.getVideoDetail(bvid)
                if (response.code == 0 && response.data != null) {
                    val data = response.data
                    val uploader = data.owner?.name ?: "未知歌手"
                    val title = data.title ?: "未知歌曲"
                    val cover = data.pic
                    
                    val existingKeys = getExistingKeysForPlaylist(playlistId)
                    val itemsToAdd = if (data.pages != null && data.pages.size > 1) {
                        data.pages.map { page ->
                            com.thehbc.bilimusic.data.local.room.LocalPlaylistItem(
                                playlistId = playlistId,
                                bvid = bvid,
                                cid = page.cid,
                                title = BiliTitleParser.cleanPageTitle(page.part ?: "未知歌曲"),
                                artist = uploader,
                                durationStr = String.format("%02d:%02d", page.duration / 60, page.duration % 60),
                                albumArtUrl = cover,
                                parentTitle = title,
                                sortOrder = 0,
                                page = page.page,
                                partTitle = page.part
                            )
                        }
                    } else {
                        val durationSeconds = data.duration
                        val minutes = durationSeconds / 60
                        val seconds = durationSeconds % 60
                        listOf(
                            com.thehbc.bilimusic.data.local.room.LocalPlaylistItem(
                                playlistId = playlistId,
                                bvid = bvid,
                                cid = data.cid ?: 0L,
                                title = title,
                                artist = uploader,
                                durationStr = String.format("%02d:%02d", minutes, seconds),
                                albumArtUrl = cover,
                                parentTitle = null,
                                sortOrder = 0,
                                page = 1,
                                partTitle = title
                            )
                        )
                    }
                    val nonDuplicateItems = itemsToAdd.filter { item ->
                        val key = "${item.bvid}_${item.cid}"
                        !existingKeys.contains(key)
                    }
                    if (nonDuplicateItems.isNotEmpty()) {
                        localPlaylistRepository.addItemsToPlaylist(playlistId, nonDuplicateItems)
                    }
                    if (currentPlaylistId == "local_$playlistId") {
                        loadPlaylist("local_$playlistId")
                    }
                    onSuccess()
                } else {
                    onError(response.message)
                }
            } catch (e: Exception) {
                onError("获取视频信息失败: ${e.message}")
            }
        }
    }

    suspend fun getExistingKeysForPlaylist(playlistId: Long): Set<String> {
        return localPlaylistRepository.getItemsForPlaylistSync(playlistId)
            .map { "${it.bvid}_${it.cid}" }
            .toSet()
    }

    fun mapToSingleSong(media: FavMedia): Song {
        val durationSeconds = media.duration ?: 0
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return Song(
            id          = media.id.toString(),
            bvid        = media.bvid,
            cid         = media.ugc?.first_cid,
            title       = media.title ?: "未知歌曲",
            artist      = media.upper?.name ?: "未知歌手",
            duration    = String.format("%02d:%02d", minutes, seconds),
            albumArtUrl = media.cover,
            mediaUri    = null,
            parentTitle = null,
            page        = 1,
            partTitle   = media.title
        )
    }

    companion object {
        fun provideFactory(
            apiService: BiliApiService,
            localPlaylistRepository: LocalPlaylistRepository,
            authManager: com.thehbc.bilimusic.data.local.AuthManager
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistViewModel(apiService, localPlaylistRepository, authManager) as T
            }
    }
}
