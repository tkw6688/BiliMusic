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
import androidx.media3.common.Timeline
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
import com.thehbc.bilimusic.data.repository.BiliRepository
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
    private val biliRepository: BiliRepository,
    private val playerPrefsManager: PlayerPrefsManager,
    private val activeQueueDao: LocalActiveQueueDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private fun Song.toMediaItem(): MediaItem {
        val fakeUriStr = if (bvid != null && cid != null) {
            "bilimusic://play?bvid=${bvid}&cid=${cid}"
        } else {
            mediaUri ?: ""
        }
        val artistWithParent = buildString {
            append(artist)
            if (!parentTitle.isNullOrEmpty()) {
                append(" · ")
                append(parentTitle)
            }
        }
        val cleanedArtUrl = BiliTitleParser.cleanCoverUrl(albumArtUrl)
        
        val extras = android.os.Bundle().apply {
            putString("bvid", bvid)
            cid?.let { putLong("cid", it) }
            putString("parentTitle", parentTitle)
            page?.let { putInt("page", it) }
            putString("partTitle", partTitle)
            putString("duration", duration)
            putString("albumArtUrl", albumArtUrl)
            putString("artist", artist)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistWithParent)
            .setArtworkUri(if (!cleanedArtUrl.isNullOrEmpty()) Uri.parse(cleanedArtUrl) else null)
            .setExtras(extras)
            .build()

        val cacheKey = if (bvid != null && cid != null) {
            "bilimusic_${bvid}_${cid}"
        } else {
            "bilimusic_$id"
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(fakeUriStr))
            .setCustomCacheKey(cacheKey)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun MediaItem.toSong(): Song {
        val extras = mediaMetadata.extras
        val artistWithParent = mediaMetadata.artist?.toString() ?: ""
        val rawArtist = extras?.getString("artist") ?: artistWithParent.substringBefore(" · ")
        
        return Song(
            id = mediaId,
            title = mediaMetadata.title?.toString() ?: "未知歌曲",
            artist = rawArtist,
            duration = extras?.getString("duration") ?: "00:00",
            albumArtUrl = extras?.getString("albumArtUrl") ?: mediaMetadata.artworkUri?.toString(),
            mediaUri = localConfiguration?.uri?.toString(),
            bvid = extras?.getString("bvid"),
            cid = if (extras?.containsKey("cid") == true) extras.getLong("cid") else null,
            parentTitle = extras?.getString("parentTitle"),
            page = if (extras?.containsKey("page") == true) extras.getInt("page") else null,
            partTitle = extras?.getString("partTitle")
        )
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private var isRestorationComplete = false

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

    private fun syncQueueFromPlayer() {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val newQueue = ArrayList<Song>(count)
        for (i in 0 until count) {
            val item = controller.getMediaItemAt(i)
            newQueue.add(item.toSong())
        }
        val currentIdx = controller.currentMediaItemIndex
        val currentSong = if (currentIdx in newQueue.indices) newQueue[currentIdx] else null
        _state.update {
            it.copy(
                queue = newQueue,
                currentIndex = currentIdx,
                currentSong = currentSong ?: it.currentSong
            )
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val controller = mediaController ?: return
                if (mediaItem != null) {
                    val song = mediaItem.toSong()
                    val idx = controller.currentMediaItemIndex
                    val currentPos = controller.currentPosition
                    val duration = controller.duration.takeIf { d -> d > 0 } ?: parseDurationStringToMs(song.duration)
                    val progressVal = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
                    _state.update {
                        it.copy(
                            currentSong = song,
                            currentIndex = idx,
                            progress = progressVal,
                            currentPositionMs = currentPos,
                            durationMs = duration,
                            audioBitrate = null,
                            audioCodec = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            currentSong = null,
                            currentIndex = -1,
                            progress = 0f,
                            currentPositionMs = 0L,
                            durationMs = 0L
                        )
                    }
                }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncQueueFromPlayer()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                val modeObj = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                }
                _state.update { it.copy(repeatMode = modeObj) }
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(isShuffled = shuffleModeEnabled) }
            }
            override fun onTracksChanged(tracks: Tracks) {
                var sampleRate: Int? = null
                var channelCount: Int? = null
                var bitrate: Int? = null
                var codec: String? = null
                
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
                                if (format.bitrate != Format.NO_VALUE) {
                                    bitrate = format.bitrate
                                }
                                val rawCodec = format.codecs ?: format.sampleMimeType
                                if (!rawCodec.isNullOrEmpty()) {
                                    codec = when {
                                        rawCodec.contains("ec-3", ignoreCase = true) || rawCodec.contains("eac3", ignoreCase = true) -> "Dolby Audio / ec-3"
                                        rawCodec.contains("ac-3", ignoreCase = true) || rawCodec.contains("ac3", ignoreCase = true) -> "Dolby Audio / ac-3"
                                        rawCodec.contains("ac-4", ignoreCase = true) || rawCodec.contains("ac4", ignoreCase = true) -> "Dolby Audio / ac-4"
                                        rawCodec.contains("mp4a", ignoreCase = true) || rawCodec.contains("aac", ignoreCase = true) -> "AAC"
                                        rawCodec.contains("opus", ignoreCase = true) -> "Opus"
                                        rawCodec.contains("flac", ignoreCase = true) -> "FLAC"
                                        rawCodec.contains("mpeg", ignoreCase = true) || rawCodec.contains("mp3", ignoreCase = true) -> "MP3"
                                        else -> rawCodec.substringAfter("audio/").uppercase()
                                    }
                                }
                            }
                        }
                    }
                }
                
                _state.update { 
                    it.copy(
                        audioSampleRate = sampleRate ?: it.audioSampleRate,
                        audioChannels = channelCount ?: it.audioChannels,
                        audioBitrate = bitrate ?: it.audioBitrate,
                        audioCodec = codec ?: it.audioCodec
                    ) 
                }
            }
        })
    }

    private fun getNextSongInQueue(): Song? {
        val controller = mediaController ?: return null
        if (controller.shuffleModeEnabled) return null
        val nextIdx = controller.currentMediaItemIndex + 1
        if (nextIdx in 0 until controller.mediaItemCount) {
            return controller.getMediaItemAt(nextIdx).toSong()
        }
        return null
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            var ticks = 0
            var lastPrefetchedSongId: String? = null
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

                            // 预解析下一首歌曲的播放流 URL (剩余 15 秒以内时触发)
                            val remaining = dur - pos
                            if (remaining < 15000) {
                                val nextSong = getNextSongInQueue()
                                if (nextSong != null && nextSong.id != lastPrefetchedSongId) {
                                    lastPrefetchedSongId = nextSong.id
                                    viewModelScope.launch {
                                        if (nextSong.bvid != null && nextSong.cid != null) {
                                            biliRepository.prefetchPlayUrl(nextSong.bvid, nextSong.cid)
                                        }
                                    }
                                }
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
        val previousQueue = _state.value.queue
        val currentQ = newQueue ?: previousQueue
        var idx = currentQ.indexOfFirst { it.id == song.id }
        if (idx == -1 && currentQ.isEmpty()) {
            idx = 0
        }
        if (idx == -1) return

        if (song.id == _state.value.currentSong?.id) {
            if (newQueue != null && newQueue != previousQueue) {
                val mediaItems = newQueue.map { it.toMediaItem() }
                mediaController?.setMediaItems(mediaItems, idx, mediaController?.currentPosition ?: 0L)
                _state.update { it.copy(queue = newQueue, currentPlaylist = playlist ?: it.currentPlaylist) }
            }
            return
        }

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
                isPlaying = false,
                progress = 0f,
                currentPositionMs = 0L,
                durationMs = 0L,
                queue = if (newQueue != null || currentQ.isEmpty()) (newQueue ?: listOf(song)) else it.queue,
                currentIndex = idx
            )
        }

        prefetchSongCovers(currentQ, idx)

        val controller = mediaController ?: return
        val isPlayerQueueEmpty = controller.mediaItemCount == 0
        val isQueueChanged = newQueue != null && newQueue != previousQueue
        
        if (isPlayerQueueEmpty || isQueueChanged) {
            val mediaItems = currentQ.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, idx, 0L)
            controller.prepare()
            controller.play()
        } else {
            controller.seekTo(idx, 0L)
            controller.play()
        }
        
        // 清除之前的音频规格，等待 ExoPlayer TracksChanged 自动上报
        _state.update {
            it.copy(
                audioBitrate = null,
                audioCodec = null
            )
        }
    }

    fun isSongCached(song: Song): Boolean {
        return biliRepository.isSongCached(song)
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
        val controller = mediaController ?: return
        val currentState = _state.value
        val nextState = if (!currentState.isShuffled && currentState.repeatMode == RepeatMode.OFF) {
            Pair(Player.REPEAT_MODE_ALL, false)
        } else if (!currentState.isShuffled && currentState.repeatMode == RepeatMode.ALL) {
            Pair(Player.REPEAT_MODE_ONE, false)
        } else if (!currentState.isShuffled && currentState.repeatMode == RepeatMode.ONE) {
            Pair(Player.REPEAT_MODE_ALL, true)
        } else {
            Pair(Player.REPEAT_MODE_OFF, false)
        }
        
        controller.repeatMode = nextState.first
        controller.shuffleModeEnabled = nextState.second
    }

    fun skipNext(isAutoAdvance: Boolean = false) {
        mediaController?.let { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNext()
            } else {
                if (!controller.hasNextMediaItem() && _state.value.repeatMode != RepeatMode.ALL && !isAutoAdvance) {
                    controller.seekTo(0, 0L)
                } else {
                    controller.seekToNext()
                }
            }
        }
    }

    fun skipPrevious() {
        mediaController?.let { controller ->
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPrevious()
            } else {
                val count = controller.mediaItemCount
                if (count > 0) {
                    controller.seekTo(count - 1, 0L)
                }
            }
        }
    }

    fun removeSong(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
        }
    }

    fun removeSongById(songId: String) {
        val index = _state.value.queue.indexOfFirst { it.id == songId }
        if (index != -1) removeSong(index)
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        if (fromIndex in 0 until count && toIndex in 0 until count) {
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun insertNext(song: Song) {
        val controller = mediaController ?: return
        val queue = _state.value.queue
        val oldIndex = queue.indexOfFirst { it.id == song.id }
        val currentIdx = controller.currentMediaItemIndex
        
        if (oldIndex != -1) {
            if (oldIndex == currentIdx) return
            controller.removeMediaItem(oldIndex)
        }
        
        val insertIndex = if (controller.mediaItemCount == 0) 0 else (currentIdx + 1)
        controller.addMediaItem(insertIndex, song.toMediaItem())
        
        if (controller.currentMediaItem == null) {
            controller.prepare()
            controller.play()
        } else {
            prefetchSingleSongCover(song)
        }
    }

    fun appendToQueue(song: Song) {
        val controller = mediaController ?: return
        val queue = _state.value.queue
        val oldIndex = queue.indexOfFirst { it.id == song.id }
        
        if (oldIndex != -1) {
            if (oldIndex == controller.currentMediaItemIndex) {
                controller.moveMediaItem(oldIndex, controller.mediaItemCount - 1)
            } else {
                controller.removeMediaItem(oldIndex)
                controller.addMediaItem(song.toMediaItem())
            }
        } else {
            controller.addMediaItem(song.toMediaItem())
        }
        
        if (controller.currentMediaItem == null) {
            controller.prepare()
            controller.play()
        } else {
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

                // 4. 将队列及状态设置到 ExoPlayer 中，但不自动播放
                mediaController?.let { player ->
                    player.repeatMode = when (repeatModeObj) {
                        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.shuffleModeEnabled = lastShuffle
                    
                    player.clearMediaItems()
                    val mediaItems = restoredSongs.map { it.toMediaItem() }
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
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        fun provideFactory(
            application: Application,
            biliRepository: BiliRepository,
            playerPrefsManager: PlayerPrefsManager,
            activeQueueDao: LocalActiveQueueDao
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(application, biliRepository, playerPrefsManager, activeQueueDao) as T
                }
            }
    }
}
