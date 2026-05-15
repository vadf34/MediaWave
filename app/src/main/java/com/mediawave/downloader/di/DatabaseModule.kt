package com.mediawave.downloader.di

import android.content.Context
import androidx.room.Room
import com.mediawave.downloader.data.db.AppDatabase
import com.mediawave.downloader.data.db.CookieDao
import com.mediawave.downloader.data.db.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediawave.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideCookieDao(db: AppDatabase): CookieDao = db.cookieDao()
}
