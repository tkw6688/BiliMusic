package com.thehbc.bilimusic.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

class PlayerPrefsManager(private val context: Context) {
    companion object {
        val LAST_PLAYING_SONG_ID = stringPreferencesKey("last_playing_song_id")
        val LAST_PLAYBACK_POSITION = longPreferencesKey("last_playback_position")
        val LAST_REPEAT_MODE = intPreferencesKey("last_repeat_mode")
        val LAST_SHUFFLE_MODE = booleanPreferencesKey("last_shuffle_mode")
    }

    val lastPlayingSongIdFlow: Flow<String?> = context.playerDataStore.data.map { it[LAST_PLAYING_SONG_ID] }
    val lastPlaybackPositionFlow: Flow<Long> = context.playerDataStore.data.map { it[LAST_PLAYBACK_POSITION] ?: 0L }
    val lastRepeatModeFlow: Flow<Int> = context.playerDataStore.data.map { it[LAST_REPEAT_MODE] ?: 0 }
    val lastShuffleModeFlow: Flow<Boolean> = context.playerDataStore.data.map { it[LAST_SHUFFLE_MODE] ?: false }

    suspend fun savePlayerState(songId: String?, position: Long, repeatMode: Int, isShuffled: Boolean) {
        context.playerDataStore.edit { prefs ->
            if (songId != null) {
                prefs[LAST_PLAYING_SONG_ID] = songId
            } else {
                prefs.remove(LAST_PLAYING_SONG_ID)
            }
            prefs[LAST_PLAYBACK_POSITION] = position
            prefs[LAST_REPEAT_MODE] = repeatMode
            prefs[LAST_SHUFFLE_MODE] = isShuffled
        }
    }
}
