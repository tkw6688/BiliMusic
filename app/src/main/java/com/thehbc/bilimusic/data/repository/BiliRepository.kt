package com.thehbc.bilimusic.data.repository

import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.data.network.model.VideoDetailResponse
import com.thehbc.bilimusic.data.utils.BiliTitleParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

data class FavSongsResult(
    val songs: List<Song>,
    val hasMore: Boolean
)

interface BiliRepository {
    suspend fun getCreatedPlaylists(uid: Long): Result<List<Playlist>>
    suspend fun getPlaylistSongs(mediaId: Long, pageNum: Int, pageSize: Int): Result<FavSongsResult>
    suspend fun getVideoDetail(bvid: String): Result<VideoDetailResponse>
    suspend fun getPlayUrl(bvid: String, cid: Long): Result<String>
    suspend fun prefetchPlayUrl(bvid: String, cid: Long)
    
    // Cache methods
    fun getPlaylistsCache(): List<Playlist>?
    fun savePlaylistsCache(playlists: List<Playlist>)
    fun getSongsCache(playlistId: String): List<Song>?
    fun saveSongsCache(playlistId: String, songs: List<Song>)
    fun isSongCached(song: Song): Boolean
}

class BiliRepositoryImpl(
    private val apiService: BiliApiService,
    private val authManager: AuthManager,
    private val metadataCacheManager: com.thehbc.bilimusic.data.local.MetadataCacheManager,
    private val simpleCache: androidx.media3.datasource.cache.SimpleCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BiliRepository {

    private val playUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun <T> handleBiliError(code: Int, message: String): Result<T> {
        val mappedMessage = when (code) {
            -101 -> {
                // 自动清理 Cookie 状态，触发 UI 退出登录展示
                authManager.clearCookies()
                "登录状态已失效，请重新登录"
            }
            -412 -> "访问过于频繁，已被风控限制，请稍后再试"
            else -> "$message (错误码: $code)"
        }
        return Result.failure(IOException(mappedMessage))
    }

    override suspend fun getCreatedPlaylists(uid: Long): Result<List<Playlist>> = withContext(ioDispatcher) {
        try {
            val response = apiService.getCreatedFavFolders(upMid = uid)
            if (response.code == 0) {
                val list = response.data?.list?.map { folder ->
                    Playlist(
                        id = folder.id.toString(),
                        name = folder.title ?: "未命名收藏夹",
                        songCount = folder.media_count ?: 0,
                        coverColor = androidx.compose.ui.graphics.Color(0xFFFB7299),
                        description = "B站收藏夹",
                        coverUrl = null
                    )
                } ?: emptyList()
                Result.success(list)
            } else {
                handleBiliError(response.code, response.message)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlaylistSongs(
        mediaId: Long,
        pageNum: Int,
        pageSize: Int
    ): Result<FavSongsResult> = withContext(ioDispatcher) {
        try {
            val response = apiService.getFavResources(
                mediaId = mediaId,
                pageNum = pageNum,
                pageSize = pageSize
            )
            if (response.code == 0) {
                val hasMore = response.data?.has_more ?: false
                val medias = response.data?.medias ?: emptyList()
                
                // 并发拉取视频分P信息并进行合集展平
                val songs = coroutineScope {
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
                                                albumArtUrl = BiliTitleParser.cleanCoverUrl(media.cover),
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
                Result.success(FavSongsResult(songs, hasMore))
            } else {
                handleBiliError(response.code, response.message)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToSingleSong(media: com.thehbc.bilimusic.data.network.model.FavMedia): Song {
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
            albumArtUrl = BiliTitleParser.cleanCoverUrl(media.cover),
            mediaUri    = null,
            parentTitle = null,
            page        = 1,
            partTitle   = media.title
        )
    }

    override suspend fun getVideoDetail(bvid: String): Result<VideoDetailResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getVideoDetail(bvid)
            if (response.code == 0) {
                Result.success(response)
            } else {
                handleBiliError(response.code, response.message)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlayUrl(bvid: String, cid: Long): Result<String> = withContext(ioDispatcher) {
        val cacheKey = "${bvid}_$cid"
        playUrlCache[cacheKey]?.let { cachedUrl ->
            return@withContext Result.success(cachedUrl)
        }

        try {
            val response = apiService.getPlayUrl(bvid, cid)
            if (response.code == 0) {
                val dash = response.data?.dash
                val audioStream = dash?.audio?.maxByOrNull { it.bandwidth } ?: dash?.video?.firstOrNull()
                var audioUrl = audioStream?.baseUrl ?: audioStream?.base_url
                
                // MCDN 节点连接容易断线卡缓冲，此处进行规避过滤
                if (audioUrl != null && audioUrl.contains("mcdn.bilivideo")) {
                    val backupUrls = audioStream?.backupUrl ?: emptyList()
                    audioUrl = backupUrls.firstOrNull { !it.contains("mcdn") } ?: audioUrl
                }
                
                if (audioUrl.isNullOrEmpty()) {
                    val durlStream = response.data?.durl?.firstOrNull()
                    audioUrl = durlStream?.url
                    if (audioUrl != null && audioUrl.contains("mcdn.bilivideo")) {
                        audioUrl = durlStream?.backup_url?.firstOrNull { !it.contains("mcdn") } ?: audioUrl
                    }
                }
                
                if (!audioUrl.isNullOrEmpty()) {
                    playUrlCache[cacheKey] = audioUrl
                    Result.success(audioUrl)
                } else {
                    Result.failure(IOException("播放流解析失败: data为空"))
                }
            } else {
                handleBiliError(response.code, response.message)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun prefetchPlayUrl(bvid: String, cid: Long) {
        getPlayUrl(bvid, cid)
    }

    override fun getPlaylistsCache(): List<Playlist>? = metadataCacheManager.getPlaylists()
    override fun savePlaylistsCache(playlists: List<Playlist>) = metadataCacheManager.savePlaylists(playlists)
    override fun getSongsCache(playlistId: String): List<Song>? = metadataCacheManager.getSongs(playlistId)
    override fun saveSongsCache(playlistId: String, songs: List<Song>) = metadataCacheManager.saveSongs(playlistId, songs)
    override fun isSongCached(song: Song): Boolean {
        val cacheKey = if (song.bvid != null && song.cid != null) {
            "bilimusic_${song.bvid}_${song.cid}"
        } else {
            "bilimusic_${song.id}"
        }
        val metadata = simpleCache.getContentMetadata(cacheKey)
        val contentLength = metadata.get(
            androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH,
            -1L
        )
        return contentLength != -1L &&
                simpleCache.getCachedBytes(cacheKey, 0, contentLength) == contentLength
    }
}
