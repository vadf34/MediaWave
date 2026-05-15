package com.mediawave.downloader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MediaWaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
