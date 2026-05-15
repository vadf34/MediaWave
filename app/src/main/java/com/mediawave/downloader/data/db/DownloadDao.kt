package com.mediawave.downloader.data.db

import androidx.room.*
import com.mediawave.downloader.data.model.DownloadRecord
import com.mediawave.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM download_history WHERE status = :status ORDER BY timestamp DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(record: DownloadRecord): Long

    @Delete
    suspend fun deleteDownload(record: DownloadRecord)

    @Query("DELETE FROM download_history")
    suspend fun clearAll()

    @Query("SELECT * FROM download_history WHERE id = :id")
    suspend fun getById(id: Int): DownloadRecord?

    @Update
    suspend fun updateDownload(record: DownloadRecord)

    @Query("SELECT COUNT(*) FROM download_history WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>
}
