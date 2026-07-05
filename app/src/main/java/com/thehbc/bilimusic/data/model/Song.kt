package com.thehbc.bilimusic.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val albumArtUrl: String? = null,
    val mediaUri: String? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val parentTitle: String? = null,
)
