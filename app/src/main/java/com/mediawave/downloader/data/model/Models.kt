package com.mediawave.downloader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "download_history")
@Serializable
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val filePath: String,
    val fileSize: Long = 0L,
    val duration: String = "",
    val quality: String = "HD",
    val extractor: String = "Unknown",
    val timestamp: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.COMPLETED,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Entity(tableName = "cookie_profiles")
@Serializable
data class CookieProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val content: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val filesize: Long?,
    val vcodec: String,
    val acodec: String,
    val fps: Double?,
    val tbr: Double?,
)

data class VideoInfo(
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String,
    val url: String,
    val formats: List<VideoFormat>,
    val extractor: String,
)

enum class DownloadQuality(val label: String, val ytdlpFormat: String) {
    HD("HD (Best)", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"),
    SD("Standard", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]/best"),
    AUDIO_ONLY("Audio Only (MP3)", "bestaudio/best"),
}

data class ActiveDownload(
    val id: String,
    val url: String,
    val title: String,
    val progress: Float,
    val speed: String,
    val eta: String,
    val status: DownloadStatus,
    val quality: DownloadQuality,
)
