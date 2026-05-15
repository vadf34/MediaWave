package com.mediawave.downloader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadRecord

@Database(
    entities = [DownloadRecord::class, CookieProfile::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun cookieDao(): CookieDao
}
