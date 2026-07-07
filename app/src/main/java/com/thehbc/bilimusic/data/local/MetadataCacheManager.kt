package com.thehbc.bilimusic.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.thehbc.bilimusic.data.model.Playlist
import com.thehbc.bilimusic.data.model.Song
import java.io.File
import java.lang.reflect.Type

class ColorTypeAdapter : JsonSerializer<Color>, JsonDeserializer<Color> {
    override fun serialize(src: Color, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.value.toLong())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Color {
        return Color(json.asLong.toULong())
    }
}

class MetadataCacheManager(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorTypeAdapter())
        .create()

    private fun getCacheFile(filename: String): File {
        val dir = File(context.cacheDir, "metadata_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, filename)
    }

    fun savePlaylists(playlists: List<Playlist>) {
        try {
            val json = gson.toJson(playlists)
            getCacheFile("bili_playlists.json").writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPlaylists(): List<Playlist>? {
        return try {
            val file = getCacheFile("bili_playlists.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<Playlist>>() {}.type
                gson.fromJson(json, type)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveSongs(playlistId: String, songs: List<Song>) {
        try {
            val json = gson.toJson(songs)
            getCacheFile("bili_songs_$playlistId.json").writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSongs(playlistId: String): List<Song>? {
        return try {
            val file = getCacheFile("bili_songs_$playlistId.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<Song>>() {}.type
                gson.fromJson(json, type)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
