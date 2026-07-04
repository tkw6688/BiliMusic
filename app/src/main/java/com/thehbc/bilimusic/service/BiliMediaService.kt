package com.thehbc.bilimusic.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.app.PendingIntent
import android.content.Intent
import com.thehbc.bilimusic.MainActivity
import com.thehbc.bilimusic.BiliMusicApp

class BiliMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val appContainer = (applicationContext as BiliMusicApp).container
        val authManager = appContainer.authManager

        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
            
        val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            dataSourceFactory,
            object : androidx.media3.datasource.ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                    val cookieHeader = buildString {
                        if (!authManager.currentBuvid3.isNullOrEmpty()) append("buvid3=${authManager.currentBuvid3}; ")
                        if (!authManager.currentBuvid4.isNullOrEmpty()) append("buvid4=${authManager.currentBuvid4}; ")
                        if (!authManager.currentSessdata.isNullOrEmpty()) append("SESSDATA=${authManager.currentSessdata}; ")
                        if (!authManager.currentBiliJct.isNullOrEmpty()) append("bili_jct=${authManager.currentBiliJct}; ")
                        if (!authManager.currentDedeUserId.isNullOrEmpty()) append("DedeUserID=${authManager.currentDedeUserId}; ")
                    }
                    val currentHeaders = dataSpec.httpRequestHeaders.toMutableMap()
                    currentHeaders["Referer"] = "https://www.bilibili.com/"
                    currentHeaders["Cookie"] = cookieHeader
                    
                    return dataSpec.buildUpon()
                        .setHttpRequestHeaders(currentHeaders)
                        .build()
                }
            }
        )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory)
            
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // 处理音频焦点
            .setHandleAudioBecomingNoisy(true) // 拔出耳机自动暂停
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun seekToNext() {
                val intent = Intent("com.thehbc.bilimusic.ACTION_SKIP_NEXT")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }

            override fun seekToPrevious() {
                val intent = Intent("com.thehbc.bilimusic.ACTION_SKIP_PREVIOUS")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
            
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
            
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
