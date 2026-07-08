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
import android.net.Uri
import java.io.IOException
import com.thehbc.bilimusic.MainActivity
import com.thehbc.bilimusic.BiliMusicApp
import com.thehbc.bilimusic.data.repository.PlayUrlInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.BitmapImage
import coil3.asDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

class BiliMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())


    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
                    val uri = dataSpec.uri
                    var targetUri = uri
                    var resolvedCacheKey: String? = null
                    
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
                    
                    if (uri.scheme == "bilimusic" && uri.host == "play") {
                        val bvid = uri.getQueryParameter("bvid")
                        val cid = uri.getQueryParameter("cid")?.toLongOrNull()
                        if (bvid != null && cid != null) {
                            try {
                                val result = kotlinx.coroutines.runBlocking {
                                    appContainer.biliRepository.getPlayUrl(bvid, cid)
                                }
                                val playUrlInfo = result.getOrThrow()
                                targetUri = Uri.parse(playUrlInfo.url)
                                resolvedCacheKey = playUrlInfo.cacheKey
                            } catch (e: Exception) {
                                throw IOException("播放流解析失败: ${e.message}", e)
                            }
                        } else {
                            throw IOException("播放流解析失败: 无效的 bvid 或 cid")
                        }
                    }
                    
                    val builder = dataSpec.buildUpon()
                        .setUri(targetUri)
                        .setHttpRequestHeaders(currentHeaders)
                    if (resolvedCacheKey != null) {
                        builder.setKey(resolvedCacheKey)
                    }
                    return builder.build()
                }
            }
        )

        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(appContainer.simpleCache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)
            
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // 处理音频焦点
            .setHandleAudioBecomingNoisy(true) // 拔出耳机自动暂停
            .setWakeMode(C.WAKE_MODE_LOCAL) // 锁屏/熄屏 CPU 唤醒锁
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            

            
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
            
        val underlyingBitmapLoader = CoilBitmapLoader(this)
        val cacheBitmapLoader = androidx.media3.session.CacheBitmapLoader(underlyingBitmapLoader)

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(cacheBitmapLoader)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }


    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

class CoilBitmapLoader(private val context: android.content.Context) : BitmapLoader {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean {
        return true
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(Exception("Decode failed"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val cleanUrl = com.thehbc.bilimusic.data.utils.BiliTitleParser.cleanCoverUrl(uri.toString()) ?: ""
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(cleanUrl)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val image = result.image
                    val bitmap = (image as? BitmapImage)?.bitmap
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        val drawable = image.asDrawable(context.resources)
                        val bmp = if (drawable is BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
                            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
                            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(b)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            b
                        }
                        future.set(bmp)
                    }
                } else {
                    future.setException(Exception("Coil load failed"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
