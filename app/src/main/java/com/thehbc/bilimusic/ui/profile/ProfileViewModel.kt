package com.thehbc.bilimusic.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thehbc.bilimusic.data.local.AuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileState(
    val isLoggedIn: Boolean = false,
    val uname: String = "",
    val uid: Long = 0L,
    val faceUrl: String? = null
)

class ProfileViewModel(private val authManager: AuthManager) : ViewModel() {

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

    fun logout() {
        viewModelScope.launch {
            authManager.clearCookies()
        }
    }

    companion object {
        fun provideFactory(authManager: AuthManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(authManager) as T
                }
            }
    }
}
