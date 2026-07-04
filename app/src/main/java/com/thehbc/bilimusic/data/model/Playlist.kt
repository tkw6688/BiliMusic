package com.thehbc.bilimusic.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int,
    val coverColor: Color,
    val description: String = "",
    val coverUrl: String? = null,  // 预留给后续 API 接入
)
