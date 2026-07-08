package com.thehbc.bilimusic.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.local.PlayerPrefsManager
import com.thehbc.bilimusic.data.repository.LocalPlaylistRepository
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileState(
    val isLoggedIn: Boolean = false,
    val uname: String = "",
    val uid: Long = 0L,
    val faceUrl: String? = null
)

class ProfileViewModel(
    private val authManager: AuthManager,
    private val playerPrefsManager: PlayerPrefsManager,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val simpleCache: SimpleCache
) : ViewModel() {

    val uiState: StateFlow<ProfileState> = combine(
        authManager.sessdataFlow,
        authManager.unameFlow,
        authManager.uidFlow,
        authManager.faceFlow
    ) { sessdata, uname, uid, face ->
        ProfileState(
            isLoggedIn = !sessdata.isNullOrEmpty(),
            uname = uname ?: "未知用户",
            uid = uid ?: 0L,
            faceUrl = face
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileState()
    )

    val localPlaylistCount: StateFlow<Int> = localPlaylistRepository.getAllPlaylists()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val preferredAudioQuality: StateFlow<Int> = playerPrefsManager.preferredAudioQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    init {
        updateCacheSize()
    }

    fun updateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheSize.value = simpleCache.cacheSpace
        }
    }

    fun clearCache(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val keys = simpleCache.keys
                for (key in keys) {
                    simpleCache.removeResource(key)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                _cacheSize.value = simpleCache.cacheSpace
                viewModelScope.launch(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun savePreferredAudioQuality(quality: Int) {
        viewModelScope.launch {
            playerPrefsManager.savePreferredAudioQuality(quality)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearCookies()
        }
    }

    companion object {
        fun provideFactory(
            authManager: AuthManager,
            playerPrefsManager: PlayerPrefsManager,
            localPlaylistRepository: LocalPlaylistRepository,
            simpleCache: SimpleCache
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(
                        authManager,
                        playerPrefsManager,
                        localPlaylistRepository,
                        simpleCache
                    ) as T
                }
            }
    }
}
