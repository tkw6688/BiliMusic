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
import kotlinx.coroutines.delay
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
    private val apiService: BiliApiService
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

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

        if (song.bvid != null && song.cid != null) {
            viewModelScope.launch {
                try {
                    val response = apiService.getPlayUrl(song.bvid, song.cid)
                    if (response.code == 0) {
                        val dash = response.data?.dash
                        // 优先选择带宽 (bandwidth) 最高、也就是音质最好的音轨 (通常 30280 是 320kbps)
                        val audioStream = dash?.audio?.maxByOrNull { it.bandwidth } ?: dash?.video?.firstOrNull()
                        
                        var audioUrl = audioStream?.baseUrl ?: audioStream?.base_url
                        
                        // 【核心修复】过滤 B站 P2P MCDN 节点，防止 ConnectException
                        if (audioUrl != null && audioUrl.contains("mcdn.bilivideo")) {
                            val backupUrls = audioStream?.backupUrl ?: emptyList()
                            audioUrl = backupUrls.firstOrNull { !it.contains("mcdn") } ?: audioUrl
                        }
                        
                        _state.update {
                            it.copy(
                                audioBitrate = audioStream?.bandwidth,
                                audioCodec = audioStream?.codecs
                            )
                        }
                        
                        // 如果连 dash 都没有，尝试 fallback 到早期的 durl 格式
                        if (audioUrl.isNullOrEmpty()) {
                            val durlStream = response.data?.durl?.firstOrNull()
                            audioUrl = durlStream?.url
                            if (audioUrl != null && audioUrl.contains("mcdn.bilivideo")) {
                                audioUrl = durlStream?.backup_url?.firstOrNull { !it.contains("mcdn") } ?: audioUrl
                            }
                        }

                        if (!audioUrl.isNullOrEmpty()) {
                            val metadata = MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setArtworkUri(if (!song.albumArtUrl.isNullOrEmpty()) Uri.parse(song.albumArtUrl) else null)
                                .build()

                            val mediaItem = MediaItem.Builder()
                                .setMediaId(song.id)
                                .setUri(Uri.parse(audioUrl))
                                .setMediaMetadata(metadata)
                                .build()

                            mediaController?.setMediaItem(mediaItem)
                            mediaController?.prepare()
                            mediaController?.play()
                            
                            // 更新 uri 回状态中（可选）
                            _state.update { it.copy(currentSong = song.copy(mediaUri = audioUrl)) }
                        } else {
                            _errorEvent.emit("播放失败: ${response.message}")
                            _state.update { it.copy(isPlaying = false) }
                        }
                    } else {
                        _errorEvent.emit("播放失败: ${response.message}")
                        _state.update { it.copy(isPlaying = false) }
                    }
                } catch (e: Exception) {
                    _errorEvent.emit("网络异常或资源失效")
                    _state.update { it.copy(isPlaying = false) }
                }
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
                player.seekTo((dur * progress).toLong())
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

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(mediaCommandReceiver)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        fun provideFactory(application: Application, apiService: BiliApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(application, apiService) as T
                }
            }
    }
}
