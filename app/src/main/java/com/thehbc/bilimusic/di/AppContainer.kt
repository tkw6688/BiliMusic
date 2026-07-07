package com.thehbc.bilimusic.di

import android.content.Context
import com.thehbc.bilimusic.data.local.AuthManager
import com.thehbc.bilimusic.data.network.api.BiliApiService
import com.thehbc.bilimusic.data.network.interceptor.CookieInterceptor
import com.thehbc.bilimusic.data.network.interceptor.WbiInterceptor
import com.thehbc.bilimusic.data.local.room.AppDatabase
import com.thehbc.bilimusic.data.repository.LocalPlaylistRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {

    val authManager: AuthManager by lazy {
        AuthManager(context)
    }

    val playerPrefsManager: com.thehbc.bilimusic.data.local.PlayerPrefsManager by lazy {
        com.thehbc.bilimusic.data.local.PlayerPrefsManager(context)
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 用于调试
        }

        OkHttpClient.Builder()
            .addInterceptor(CookieInterceptor(authManager))
            .addInterceptor(WbiInterceptor(authManager))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val biliApiService: BiliApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/") // 默认 Base URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BiliApiService::class.java)
    }

    val appDatabase: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    val localPlaylistRepository: LocalPlaylistRepository by lazy {
        LocalPlaylistRepository(appDatabase.localPlaylistDao())
    }
}
