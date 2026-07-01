package com.mediawave.downloader.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import com.mediawave.downloader.R
import com.mediawave.downloader.data.model.ActiveDownload
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.data.model.DownloadStatus
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    private var isInitialized = false
    private val downloadSemaphore = Semaphore(permits = 5)

    fun init() {
        if (isInitialized) return
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            Aria2c.getInstance().init(context)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getYtdlpVersion(): String {
        return try {
            YoutubeDL.getInstance().version(context) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    suspend fun updateYtdlp(onProgress: (String) -> Unit): String {
        return withContext(Dispatchers.IO) {
            try {
                val result = YoutubeDL.getInstance().updateYoutubeDL(context)
                when (result) {
                    YoutubeDL.UpdateStatus.DONE, YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                        val version = getYtdlpVersion()
                        onProgress(version)
                        version
                    }
                    else -> "Update failed"
                }
            } catch (e: Exception) {
                "Update failed: ${e.message}"
            }
        }
    }

    suspend fun startDownload(
        url: String,
        quality: DownloadQuality,
        cookieProfile: CookieProfile? = null,
        dbRecordId: Long? = null,
        onProgress: (ActiveDownload) -> Unit,
        onComplete: (filePath: String, title: String, author: String, thumbnail: String, extractor: String) -> Unit,
        onError: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        downloadSemaphore.withPermit {
            val downloadId = UUID.randomUUID().toString()
            val outputDir = getOutputDirectory(quality)
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                onError(context.getString(R.string.error_no_storage_permission))
                return@withContext
            }

            // Filename template with limits to avoid Errno 36
            val outputTemplate = "${outputDir.absolutePath}/%(uploader).50s - %(title).100s.%(ext)s"

            val request = YoutubeDLRequest(url).apply {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                
                when (quality) {
                    DownloadQuality.HD -> addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best")
                    DownloadQuality.SD -> addOption("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best")
                    DownloadQuality.AUDIO_ONLY -> {
                        addOption("-f", "bestaudio/best")
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    }
                }

                addOption("-o", outputTemplate)
                if (quality != DownloadQuality.AUDIO_ONLY) addOption("--merge-output-format", "mp4")
                
                addOption("--add-metadata")
                addOption("--no-playlist")
                addOption("--newline") // Crucial for progress parsing
                addOption("--progress") // Force progress output
                addOption("--print-json") // RESTORED for thumbnails
                
                cookieProfile?.let { addOption("--cookies", createTempCookieFile(it.content).absolutePath) }

                addOption("--retries", "10")
                addOption("--fragment-retries", "10")
                addOption("--trim-file-name", "120")
                addOption("--no-warnings")
            }

            val initialDownload = ActiveDownload(
                id = downloadId, url = url, title = context.getString(R.string.status_getting_info),
                progress = 0f, speed = "", eta = "", status = DownloadStatus.DOWNLOADING,
                quality = quality, dbRecordId = dbRecordId
            )
            updateActiveDownload(downloadId, initialDownload)

            var attempt = 0
            val maxAttempts = 3
            while (attempt < maxAttempts) {
                attempt++
                try {
                    var lastTitle = ""
                    var lastAuthor = ""
                    var lastThumbnail = ""
                    var lastExtractor = ""
                    var isAudioPart = false

                    YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                        // 1. Parsing Metadata from JSON line
                        if (line.trim().startsWith("{")) {
                            try {
                                val json = JSONObject(line.trim())
                                if (lastTitle.isBlank()) lastTitle = json.optString("title", json.optString("fulltitle", ""))
                                if (lastAuthor.isBlank()) lastAuthor = json.optString("uploader", json.optString("channel", "Unknown"))
                                if (lastThumbnail.isBlank()) {
                                    val thumb = json.optString("thumbnail", "")
                                    if (thumb.isNotBlank()) lastThumbnail = thumb
                                    else {
                                        val arr = json.optJSONArray("thumbnails")
                                        if (arr != null && arr.length() > 0) {
                                            lastThumbnail = arr.getJSONObject(arr.length() - 1).optString("url", "")
                                        }
                                    }
                                }
                                if (lastExtractor.isBlank()) lastExtractor = json.optString("extractor_key", json.optString("extractor", "Unknown"))
                            } catch (_: Exception) {}
                        }

                        // 2. Detection of Audio part to prevent 0-100% reset
                        if (line.contains("Destination:") && (line.contains(".m4a") || line.contains(".mp3") || line.contains("audio"))) {
                            isAudioPart = true
                        }

                        // 3. Parsing Speed
                        val speedRegex = Regex("""([\d.]+\s*[KMGk]iB/s)""")
                        val speed = speedRegex.find(line)?.groupValues?.get(1) ?: ""

                        // 4. Parsing ETA
                        val eta = if (etaInSeconds > 0) {
                            val m = etaInSeconds / 60
                            val s = etaInSeconds % 60
                            if (m > 0) "${m}${context.getString(R.string.time_min)} ${s}${context.getString(R.string.time_sec)}"
                            else "${s}${context.getString(R.string.time_sec)}"
                        } else ""

                        // 5. Smooth Progress (Video 90%, Audio 10%)
                        val adjustedProgress = if (quality == DownloadQuality.AUDIO_ONLY) {
                            progress / 100f
                        } else {
                            if (!isAudioPart) (progress * 0.9f) / 100f
                            else (90f + (progress * 0.1f)) / 100f
                        }

                        // 6. Update state
                        val displayTitle = if (lastTitle.isNotBlank()) lastTitle else initialDownload.title
                        val activeDownload = initialDownload.copy(
                            title = displayTitle,
                            progress = adjustedProgress.coerceIn(0f, 1f),
                            speed = speed,
                            eta = eta
                        )
                        updateActiveDownload(downloadId, activeDownload)
                        onProgress(activeDownload)
                    }

                    val downloadedFile = findLatestFile(outputDir)
                    removeActiveDownload(downloadId)

                    if (downloadedFile != null) {
                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(downloadedFile.extension)
                            ?: if (quality == DownloadQuality.AUDIO_ONLY) "audio/mpeg" else "video/mp4"
                        MediaScannerConnection.scanFile(context, arrayOf(downloadedFile.absolutePath), arrayOf(mimeType), null)
                        onComplete(downloadedFile.absolutePath, lastTitle.ifBlank { downloadedFile.nameWithoutExtension }, lastAuthor, lastThumbnail, lastExtractor)
                        return@withPermit
                    } else throw Exception(context.getString(R.string.error_file_not_found))
                } catch (e: Exception) {
                    if (attempt >= maxAttempts || e.message?.contains("Operation not permitted") == true) {
                        removeActiveDownload(downloadId)
                        onError(e.message ?: context.getString(R.string.error_download_failed))
                        return@withPermit
                    }
                    delay(2000L * attempt)
                }
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(downloadId)
            removeActiveDownload(downloadId)
        } catch (_: Exception) {}
    }

    fun getOutputDirectory(quality: DownloadQuality): File {
        val subDir = "MediaWave"
        val mediaType = if (quality == DownloadQuality.AUDIO_ONLY) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (Environment.isExternalStorageManager()) File(Environment.getExternalStoragePublicDirectory(mediaType), subDir)
            else File(context.getExternalFilesDir(mediaType), subDir)
        } else File(Environment.getExternalStoragePublicDirectory(mediaType), subDir)
    }

    private fun createTempCookieFile(content: String): File {
        val file = File(context.cacheDir, "cookies_${System.currentTimeMillis()}.txt")
        file.writeText(content)
        return file
    }

    private fun findLatestFile(directory: File): File? {
        return directory.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun updateActiveDownload(id: String, download: ActiveDownload) {
        val current = _activeDownloads.value.toMutableMap()
        current[id] = download
        _activeDownloads.value = current
    }

    private fun removeActiveDownload(id: String) {
        val current = _activeDownloads.value.toMutableMap()
        current.remove(id)
        _activeDownloads.value = current
    }
}
