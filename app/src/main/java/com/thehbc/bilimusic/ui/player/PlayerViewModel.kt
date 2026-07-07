package com.thehbc.bilimusic.ui.player

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaMetadata
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.service.BiliMediaService
import com.thehbc.bilimusic.data.local.PlayerPrefsManager
import com.thehbc.bilimusic.data.local.room.LocalActiveQueueDao
import com.thehbc.bilimusic.data.local.room.LocalActiveQueueItem
import com.thehbc.bilimusic.data.utils.BiliTitleParser
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val currentSong: Song? = null,
    val currentPlaylist: Playlist? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f, // 范围 0.0 ~ 1.0
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val audioBitrate: Int? = null,
    val audioCodec: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null
)

/**
 * 跨页面共享的播放器状态 ViewModel。
 * 作用域为 Activity，生命周期与 Activity 一致。
 * 接管 ExoPlayer/MediaController 与 Compose UI 的状态同步。
 */
class PlayerViewModel(
    application: Application,
    private val apiService: BiliApiService,
    private val playerPrefsManager: PlayerPrefsManager,
    private val activeQueueDao: LocalActiveQueueDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private var isRestorationComplete = false

    private val mediaCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.thehbc.bilimusic.ACTION_SKIP_NEXT" -> skipNext(isAutoAdvance = false)
                "com.thehbc.bilimusic.ACTION_SKIP_PREVIOUS" -> skipPrevious()
            }
        }
    }

    init {
        initializeController()
        startProgressTracker()
        
        // 观察播放器配置状态并持久化
        viewModelScope.launch {
            var lastSongId: String? = null
            var lastRepeatMode: RepeatMode? = null
            var lastIsShuffled: Boolean? = null
            
            state.collect { currentState ->
                if (!isRestorationComplete) return@collect
                
                val songId = currentState.currentSong?.id
                val repeatMode = currentState.repeatMode
                val isShuffled = currentState.isShuffled
                
                if (songId != lastSongId || repeatMode != lastRepeatMode || isShuffled != lastIsShuffled) {
                    lastSongId = songId
                    lastRepeatMode = repeatMode
                    lastIsShuffled = isShuffled
                    
                    playerPrefsManager.savePlayerState(
                        songId = songId,
                        position = currentState.currentPositionMs,
                        repeatMode = when (repeatMode) {
                            RepeatMode.OFF -> 0
                            RepeatMode.ALL -> 1
                            RepeatMode.ONE -> 2
                        },
                        isShuffled = isShuffled
                    )
                }
            }
        }

        // 观察队列变化并持久化
        viewModelScope.launch {
            var lastQueue: List<Song>? = null
            state.collect { currentState ->
                if (!isRestorationComplete) return@collect
                
                if (currentState.queue != lastQueue) {
                    lastQueue = currentState.queue
                    val queueItems = currentState.queue.mapIndexed { index, song ->
                        LocalActiveQueueItem(
                            songId = song.id,
                            bvid = song.bvid,
                            cid = song.cid,
                            title = song.title,
                            artist = song.artist,
                            durationStr = song.duration,
                            albumArtUrl = song.albumArtUrl,
                            parentTitle = song.parentTitle,
                            page = song.page,
                            partTitle = song.partTitle,
                            sortOrder = index
                        )
                    }
                    activeQueueDao.saveActiveQueue(queueItems)
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction("com.thehbc.bilimusic.ACTION_SKIP_NEXT")
            addAction("com.thehbc.bilimusic.ACTION_SKIP_PREVIOUS")
        }
        ContextCompat.registerReceiver(
            getApplication(),
            mediaCommandReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), BiliMediaService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
                restorePersistedState()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    skipNext(isAutoAdvance = true)
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                var sampleRate: Int? = null
                var channelCount: Int? = null
                
                for (group in tracks.groups) {
                    if (group.isSelected && group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.getTrackFormat(i)
                                if (format.sampleRate != Format.NO_VALUE) {
                                    sampleRate = format.sampleRate
                                }
                                if (format.channelCount != Format.NO_VALUE) {
                                    channelCount = format.channelCount
                                }
                            }
                        }
                    }
                }
                
                if (sampleRate != null || channelCount != null) {
                    _state.update { 
                        it.copy(
                            audioSampleRate = sampleRate ?: it.audioSampleRate,
                            audioChannels = channelCount ?: it.audioChannels
                        ) 
                    }
                }
            }
        })
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(100)
                mediaController?.let { player ->
                    if (player.isPlaying && player.currentMediaItem?.mediaId == _state.value.currentSong?.id) {
                        val pos = player.currentPosition
                        val dur = player.duration
                        if (dur > 0) {
                            _state.update { 
                                it.copy(
                                    progress = pos.toFloat() / dur.toFloat(),
                                    currentPositionMs = pos,
                                    durationMs = dur,
                                ) 
                            }
                            ticks++
                            if (ticks >= 50) {
                                ticks = 0
                                playerPrefsManager.savePlayerState(
                                    songId = _state.value.currentSong?.id,
                                    position = pos,
                                    repeatMode = when (_state.value.repeatMode) {
                                        RepeatMode.OFF -> 0
                                        RepeatMode.ALL -> 1
                                        RepeatMode.ONE -> 2
                                    },
                                    isShuffled = _state.value.isShuffled
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun playSong(song: Song, playlist: Playlist? = null, newQueue: List<Song>? = null) {
        val currentQ = newQueue ?: _state.value.queue
        if (song.id == _state.value.currentSong?.id) {
            // 如果是同一首歌，仅仅更新队列，不要重新加载
            if (newQueue != null && newQueue != _state.value.queue) {
                _state.update { it.copy(queue = newQueue, currentPlaylist = playlist ?: it.currentPlaylist) }
            }
            return
        }
        var idx = currentQ.indexOfFirst { it.id == song.id }
        if (idx == -1 && currentQ.isEmpty()) {
            idx = 0
        }
        
        if (idx == -1) return

        // 立即停止当前播放，防止拉取新流时还在播放上一首
        mediaController?.stop()
        mediaController?.clearMediaItems()
        
        // 显式启动前台服务，以保持前台状态
        try {
            val serviceIntent = Intent(getApplication(), BiliMediaService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(serviceIntent)
            } else {
                getApplication<Application>().startService(serviceIntent)
            }
        } catch (e: Exception) {
            // 忽略可能的启动服务异常
        }

        // 先同步 UI 状态
        _state.update {
            it.copy(
                currentSong = song,
                currentPlaylist = playlist ?: it.currentPlaylist,
                isPlaying = false, // 等待 player 真实起播
                progress = 0f,
                currentPositionMs = 0L,
                durationMs = 0L,
                queue = if (newQueue != null || currentQ.isEmpty()) (newQueue ?: listOf(song)) else it.queue,
                currentIndex = idx
            )
        }

        prefetchSongCovers(currentQ, idx)

        if (song.bvid != null && song.cid != null) {
            val fakeUriStr = "bilimusic://play?bvid=${song.bvid}&cid=${song.cid}"
            val artistWithParent = buildString {
                append(song.artist)
                if (!song.parentTitle.isNullOrEmpty()) {
                    append(" · ")
                    append(song.parentTitle)
                }
            }
            val cleanedArtUrl = BiliTitleParser.cleanCoverUrl(song.albumArtUrl)
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(artistWithParent)
                .setArtworkUri(if (!cleanedArtUrl.isNullOrEmpty()) Uri.parse(cleanedArtUrl) else null)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(fakeUriStr))
                .setMediaMetadata(metadata)
                .build()

            mediaController?.setMediaItem(mediaItem)
            mediaController?.prepare()
            mediaController?.play()
            
            // 清除之前的音频规格，等待 ExoPlayer TracksChanged 自动上报
            _state.update {
                it.copy(
                    audioBitrate = null,
                    audioCodec = null,
                    currentSong = song.copy(mediaUri = fakeUriStr)
                )
            }
        } else if (song.mediaUri != null) {
            // 回退到假数据（如果有）
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.mediaUri))
                .build()

            mediaController?.setMediaItem(mediaItem)
            mediaController?.prepare()
            mediaController?.play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { player ->
            if (player.isPlaying) {
                player.pause()
                // 暂停时保存进度
                viewModelScope.launch {
                    playerPrefsManager.savePlayerState(
                        songId = _state.value.currentSong?.id,
                        position = _state.value.currentPositionMs,
                        repeatMode = when (_state.value.repeatMode) {
                            RepeatMode.OFF -> 0
                            RepeatMode.ALL -> 1
                            RepeatMode.ONE -> 2
                        },
                        isShuffled = _state.value.isShuffled
                    )
                }
            } else {
                player.play()
            }
        }
    }

    fun seekTo(progress: Float) {
        _state.update { it.copy(progress = progress) }
        mediaController?.let { player ->
            val dur = player.duration
            if (dur > 0) {
                val targetPosition = (dur * progress).toLong()
                player.seekTo(targetPosition)
                // 实时更新 currentPositionMs，解决暂停拖拽时时间不变化的问题
                _state.update { it.copy(currentPositionMs = targetPosition) }
            }
        }
    }

    fun toggleFavorite() {
        _state.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun cyclePlayMode() {
        _state.update {
            if (!it.isShuffled && it.repeatMode == RepeatMode.OFF) {
                // Sequential -> Loop All
                it.copy(repeatMode = RepeatMode.ALL, isShuffled = false)
            } else if (!it.isShuffled && it.repeatMode == RepeatMode.ALL) {
                // Loop All -> Loop One
                it.copy(repeatMode = RepeatMode.ONE, isShuffled = false)
            } else if (!it.isShuffled && it.repeatMode == RepeatMode.ONE) {
                // Loop One -> Shuffle
                it.copy(repeatMode = RepeatMode.ALL, isShuffled = true)
            } else {
                // Shuffle -> Sequential
                it.copy(repeatMode = RepeatMode.OFF, isShuffled = false)
            }
        }
    }

    fun skipNext(isAutoAdvance: Boolean = false) {
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        var nextIndex = currentState.currentIndex + 1
        
        if (currentState.isShuffled) {
            nextIndex = queue.indices.random()
        } else if (nextIndex >= queue.size) {
            if (currentState.repeatMode == RepeatMode.ALL || !isAutoAdvance) {
                nextIndex = 0 // 回到第一首
            } else if (currentState.repeatMode == RepeatMode.OFF && isAutoAdvance) {
                // 播完了整个列表，且不循环
                mediaController?.stop()
                return
            }
        }
        
        if (currentState.repeatMode == RepeatMode.ONE && isAutoAdvance) {
            nextIndex = currentState.currentIndex // 单曲循环
        }

        if (nextIndex in queue.indices) {
            playSong(queue[nextIndex])
        }
    }

    fun skipPrevious() {
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        var prevIndex = currentState.currentIndex - 1
        
        if (currentState.isShuffled) {
            prevIndex = queue.indices.random()
        } else if (prevIndex < 0) {
            prevIndex = queue.size - 1
        }

        if (prevIndex in queue.indices) {
            playSong(queue[prevIndex])
        }
    }

    fun removeSong(index: Int) {
        val currentState = _state.value
        val queue = currentState.queue
        if (index !in queue.indices) return

        val newQueue = queue.toMutableList()
        newQueue.removeAt(index)

        var newCurrentIndex = currentState.currentIndex
        if (index < newCurrentIndex) {
            newCurrentIndex--
        } else if (index == newCurrentIndex) {
            if (newQueue.isEmpty()) {
                mediaController?.stop()
                _state.update { it.copy(queue = emptyList(), currentSong = null, isPlaying = false, progress = 0f, currentPositionMs = 0L, currentIndex = -1) }
                return
            } else {
                if (newCurrentIndex >= newQueue.size) {
                    newCurrentIndex = 0
                }
                playSong(newQueue[newCurrentIndex], newQueue = newQueue)
                return
            }
        }

        _state.update { it.copy(queue = newQueue, currentIndex = newCurrentIndex) }
    }

    fun removeSongById(songId: String) {
        val index = _state.value.queue.indexOfFirst { it.id == songId }
        if (index != -1) removeSong(index)
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        val currentState = _state.value
        val queue = currentState.queue
        if (fromIndex !in queue.indices) return

        val targetIndex = normalizeMoveTargetIndex(
            itemCount = queue.size,
            requestedToIndex = toIndex
        )
        val newQueue = reorderQueue(
            queue = queue,
            fromIndex = fromIndex,
            requestedToIndex = targetIndex
        )
        if (newQueue == queue) return

        var newCurrentIndex = currentState.currentIndex
        if (fromIndex == newCurrentIndex) {
            newCurrentIndex = targetIndex
        } else if (fromIndex < newCurrentIndex && targetIndex >= newCurrentIndex) {
            newCurrentIndex--
        } else if (fromIndex > newCurrentIndex && targetIndex <= newCurrentIndex) {
            newCurrentIndex++
        }

        _state.update { it.copy(queue = newQueue, currentIndex = newCurrentIndex) }
    }

    fun insertNext(song: Song) {
        val currentState = _state.value
        val queue = currentState.queue
        
        val cleanQueue = queue.toMutableList()
        val oldIndex = cleanQueue.indexOfFirst { it.id == song.id }
        
        var currentIndex = currentState.currentIndex
        if (oldIndex != -1) {
            cleanQueue.removeAt(oldIndex)
            if (oldIndex < currentIndex) {
                currentIndex--
            }
        }
        
        val insertIndex = if (cleanQueue.isEmpty()) 0 else (currentIndex + 1)
        cleanQueue.add(insertIndex, song)
        
        if (currentState.currentSong == null) {
            playSong(song, newQueue = cleanQueue)
        } else {
            val newCurrentIndex = cleanQueue.indexOfFirst { it.id == (currentState.currentSong?.id ?: "") }
            val targetIdx = if (newCurrentIndex != -1) newCurrentIndex else currentIndex
            _state.update {
                it.copy(
                    queue = cleanQueue,
                    currentIndex = targetIdx
                )
            }
            prefetchSongCovers(cleanQueue, targetIdx)
            prefetchSingleSongCover(song)
        }
    }

    fun appendToQueue(song: Song) {
        val currentState = _state.value
        val queue = currentState.queue
        
        val cleanQueue = queue.toMutableList()
        val oldIndex = cleanQueue.indexOfFirst { it.id == song.id }
        
        var currentIndex = currentState.currentIndex
        if (oldIndex != -1) {
            cleanQueue.removeAt(oldIndex)
            if (oldIndex < currentIndex) {
                currentIndex--
            }
        }
        
        cleanQueue.add(song)
        
        if (currentState.currentSong == null) {
            playSong(song, newQueue = cleanQueue)
        } else {
            val newCurrentIndex = cleanQueue.indexOfFirst { it.id == (currentState.currentSong?.id ?: "") }
            val targetIdx = if (newCurrentIndex != -1) newCurrentIndex else currentIndex
            _state.update {
                it.copy(
                    queue = cleanQueue,
                    currentIndex = targetIdx
                )
            }
            prefetchSongCovers(cleanQueue, targetIdx)
            prefetchSingleSongCover(song)
        }
    }

    private fun restorePersistedState() {
        viewModelScope.launch {
            try {
                // 1. 读取队列
                val queueItems = activeQueueDao.getActiveQueue()
                if (queueItems.isEmpty()) {
                    isRestorationComplete = true
                    return@launch
                }
                
                val restoredSongs = queueItems.map { item ->
                    Song(
                        id = item.songId,
                        bvid = item.bvid,
                        cid = item.cid,
                        title = item.title,
                        artist = item.artist,
                        duration = item.durationStr,
                        albumArtUrl = BiliTitleParser.cleanCoverUrl(item.albumArtUrl),
                        parentTitle = item.parentTitle,
                        page = item.page,
                        partTitle = item.partTitle
                    )
                }

                // 2. 读取配置
                val lastSongId = playerPrefsManager.lastPlayingSongIdFlow.firstOrNull()
                val lastPos = playerPrefsManager.lastPlaybackPositionFlow.firstOrNull() ?: 0L
                val lastRepeat = playerPrefsManager.lastRepeatModeFlow.firstOrNull() ?: 0
                val lastShuffle = playerPrefsManager.lastShuffleModeFlow.firstOrNull() ?: false

                val repeatModeObj = when (lastRepeat) {
                    1 -> RepeatMode.ALL
                    2 -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                }

                val currentSongObj = restoredSongs.firstOrNull { it.id == lastSongId } ?: restoredSongs.firstOrNull()
                val currentIndexVal = restoredSongs.indexOf(currentSongObj)
                val songDurationMs = parseDurationStringToMs(currentSongObj?.duration)
                val initialProgress = if (songDurationMs > 0) lastPos.toFloat() / songDurationMs.toFloat() else 0f

                // 3. 更新 UI 状态
                _state.update {
                    it.copy(
                        queue = restoredSongs,
                        currentSong = currentSongObj,
                        currentIndex = currentIndexVal,
                        currentPositionMs = lastPos,
                        durationMs = songDurationMs,
                        progress = initialProgress,
                        repeatMode = repeatModeObj,
                        isShuffled = lastShuffle
                    )
                }

                // 4. 将队列设置到 ExoPlayer 中，但不自动播放
                mediaController?.let { player ->
                    player.clearMediaItems()
                    
                    val mediaItems = restoredSongs.map { song ->
                        val fakeUriStr = if (song.bvid != null && song.cid != null) {
                            "bilimusic://play?bvid=${song.bvid}&cid=${song.cid}"
                        } else {
                            song.mediaUri ?: ""
                        }
                        
                        val artistWithParent = buildString {
                            append(song.artist)
                            if (!song.parentTitle.isNullOrEmpty()) {
                                append(" · ")
                                append(song.parentTitle)
                            }
                        }
                        val cleanedArtUrl = BiliTitleParser.cleanCoverUrl(song.albumArtUrl)
                        val metadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(artistWithParent)
                            .setArtworkUri(if (!cleanedArtUrl.isNullOrEmpty()) Uri.parse(cleanedArtUrl) else null)
                            .build()

                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(Uri.parse(fakeUriStr))
                            .setMediaMetadata(metadata)
                            .build()
                    }
                    player.setMediaItems(mediaItems)
                    
                    if (currentIndexVal >= 0 && currentIndexVal < mediaItems.size) {
                        player.seekTo(currentIndexVal, lastPos)
                    }
                    player.prepare()
                }
            } catch (e: Exception) {
                // 静默失败
            } finally {
                isRestorationComplete = true
            }
        }
    }

    private fun parseDurationStringToMs(durationStr: String?): Long {
        if (durationStr.isNullOrEmpty()) return 0L
        val parts = durationStr.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: 0L
            val seconds = parts[1].toLongOrNull() ?: 0L
            return (minutes * 60 + seconds) * 1000L
        }
        return 0L
    }

    private fun prefetchSongCovers(songs: List<Song>, startIndex: Int) {
        val imageLoader = coil3.SingletonImageLoader.get(getApplication())
        // 预载当前及接下来的2首歌曲封面
        for (i in 0 until 3) {
            val index = (startIndex + i) % songs.size
            if (index in songs.indices) {
                val song = songs[index]
                val cleanUrl = BiliTitleParser.cleanCoverUrl(song.albumArtUrl)
                if (!cleanUrl.isNullOrEmpty()) {
                    val request = coil3.request.ImageRequest.Builder(getApplication())
                        .data(cleanUrl)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }
    private fun prefetchSingleSongCover(song: Song) {
        val imageLoader = coil3.SingletonImageLoader.get(getApplication())
        val cleanUrl = BiliTitleParser.cleanCoverUrl(song.albumArtUrl)
        if (!cleanUrl.isNullOrEmpty()) {
            val request = coil3.request.ImageRequest.Builder(getApplication())
                .data(cleanUrl)
                .build()
            imageLoader.enqueue(request)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(mediaCommandReceiver)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        fun provideFactory(
            application: Application,
            apiService: BiliApiService,
            playerPrefsManager: PlayerPrefsManager,
            activeQueueDao: LocalActiveQueueDao
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(application, apiService, playerPrefsManager, activeQueueDao) as T
                }
            }
    }
}
