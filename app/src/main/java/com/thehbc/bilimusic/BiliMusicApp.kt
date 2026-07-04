package com.thehbc.bilimusic

import android.app.Application
import com.thehbc.bilimusic.di.AppContainer

class BiliMusicApp : Application() {
    
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
