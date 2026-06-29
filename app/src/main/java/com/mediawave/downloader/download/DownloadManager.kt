package com.mediawave.downloader.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import com.mediawave.downloader.data.model.ActiveDownload
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.data.model.DownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    private var isInitialized = false

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
                    YoutubeDL.UpdateStatus.DONE -> {
                        val version = getYtdlpVersion()
                        onProgress(version)
                        version
                    }
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
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
        onProgress: (ActiveDownload) -> Unit,
        onComplete: (filePath: String, title: String, author: String, thumbnail: String, extractor: String) -> Unit,
        onError: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()

        val outputDir = getOutputDirectory(quality)
        val dirCreated = outputDir.mkdirs() || outputDir.exists()

        if (!dirCreated || !outputDir.canWrite()) {
            onError("Немає доступу до сховища. Надайте дозвіл у налаштуваннях.")
            return@withContext
        }

        // Use absolute path directly in -o (most reliable with youtubedl-android)
        val outputTemplate = "${outputDir.absolutePath}/%(uploader)s - %(title)s.%(ext)s"

        val request = YoutubeDLRequest(url).apply {
            // Format selection
            when (quality) {
                DownloadQuality.HD -> addOption(
                    "-f",
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best",
                )
                DownloadQuality.SD -> addOption(
                    "-f",
                    "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best",
                )
                DownloadQuality.AUDIO_ONLY -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
            }

            // Output — absolute path in template (most stable approach)
            addOption("-o", outputTemplate)

            // Merge output to mp4 for video
            if (quality != DownloadQuality.AUDIO_ONLY) {
                addOption("--merge-output-format", "mp4")
            }

            // Metadata
            addOption("--add-metadata")
            addOption("--no-playlist")

            // Aria2c for faster parallel downloading (simple args, no shell quoting)
            addOption("--downloader", "aria2c")
            addOption("--downloader-args", "aria2c:-x 16 -s 16 -k 1M")

            // Cookies support
            cookieProfile?.let { cookie ->
                val cookieFile = createTempCookieFile(cookie.content)
                addOption("--cookies", cookieFile.absolutePath)
            }

            // Retries
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")

            // Suppress non-fatal warnings in output parsing
            addOption("--no-warnings")
            addOption("--print-json")
        }

        val initialDownload = ActiveDownload(
            id = downloadId,
            url = url,
            title = "Отримуємо інформацію...",
            progress = 0f,
            speed = "",
            eta = "",
            status = DownloadStatus.DOWNLOADING,
            quality = quality,
        )

        updateActiveDownload(downloadId, initialDownload)
        onProgress(initialDownload)

        try {
            var lastTitle = ""
            var lastAuthor = ""
            var lastThumbnail = ""
            var lastExtractor = ""

            YoutubeDL.getInstance().execute(
                request,
                downloadId,
            ) { progress, etaInSeconds, line ->
                // Parse title/author from Destination line
                if (line.contains("[download]") && line.contains("Destination:")) {
                    val fileName = line.substringAfterLast("/").substringBeforeLast(".")
                    if (fileName.contains(" - ")) {
                        lastAuthor = fileName.substringBefore(" - ").trim()
                        lastTitle = fileName.substringAfter(" - ").trim()
                    } else {
                        lastTitle = fileName.trim()
                    }
                }

                // Parse thumbnail and extractor from JSON metadata line
                if (line.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(line.trim())
                        if (lastThumbnail.isBlank()) {
                            val thumb = json.optString("thumbnail", "")
                            if (thumb.isNotBlank()) {
                                lastThumbnail = thumb
                            } else {
                                val arr = json.optJSONArray("thumbnails")
                                if (arr != null && arr.length() > 0) {
                                    lastThumbnail = arr.getJSONObject(arr.length() - 1).optString("url", "")
                                }
                            }
                        }
                        if (lastExtractor.isBlank()) {
                            lastExtractor = json.optString("extractor_key", json.optString("extractor", ""))
                        }
                        if (lastTitle.isBlank()) {
                            lastTitle = json.optString("title", json.optString("fulltitle", ""))
                        }
                        if (lastAuthor.isBlank()) {
                            lastAuthor = json.optString("uploader", json.optString("channel", ""))
                        }
                    } catch (_: Exception) {}
                }

                val speedRegex = Regex("""([\d.]+\s*[KMGk]iB/s)""")
                val speed = speedRegex.find(line)?.groupValues?.get(1) ?: ""

                val eta = if (etaInSeconds > 0) {
                    val m = etaInSeconds / 60
                    val s = etaInSeconds % 60
                    if (m > 0) "${m}хв ${s}с" else "${s}с"
                } else ""

                val displayTitle = when {
                    lastTitle.isNotBlank() -> lastTitle
                    line.contains("Extracting URL") || line.contains("Downloading webpage") -> "Отримуємо посилання..."
                    line.contains("[download]") -> "Завантажуємо..."
                    else -> "Завантажуємо..."
                }

                val activeDownload = ActiveDownload(
                    id = downloadId,
                    url = url,
                    title = displayTitle,
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    speed = speed,
                    eta = eta,
                    status = DownloadStatus.DOWNLOADING,
                    quality = quality,
                )
                updateActiveDownload(downloadId, activeDownload)
                onProgress(activeDownload)
            }

            // Find the downloaded file (most recently modified in dir)
            val downloadedFile = findLatestFile(outputDir)
            removeActiveDownload(downloadId)

            if (downloadedFile != null) {
                // Notify MediaStore so file appears in Gallery immediately
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(downloadedFile.extension)
                    ?: if (quality == DownloadQuality.AUDIO_ONLY) "audio/mpeg" else "video/mp4"
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(downloadedFile.absolutePath),
                    arrayOf(mimeType),
                    null,
                )
                onComplete(
                    downloadedFile.absolutePath,
                    lastTitle.ifBlank { downloadedFile.nameWithoutExtension },
                    lastAuthor.ifBlank { "Unknown" },
                    lastThumbnail,
                    lastExtractor,
                )
            } else {
                onError("Файл не знайдено після завантаження")
            }
        } catch (e: Exception) {
            removeActiveDownload(downloadId)
            val msg = e.message ?: "Помилка завантаження"
            when {
                msg.contains("Errno 1") || msg.contains("Operation not permitted") ->
                    onError("Немає доступу до сховища. Перевірте дозволи у налаштуваннях.")
                msg.contains("ERROR:") ->
                    onError(msg.substringAfter("ERROR:").trim())
                else ->
                    onError(msg)
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(downloadId)
            removeActiveDownload(downloadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns writable output directory.
     * Android 11+: uses public dir if MANAGE_EXTERNAL_STORAGE granted, else app-specific.
     * Android 10 and below: always public dir.
     */
    fun getOutputDirectory(quality: DownloadQuality): File {
        val subDir = "MediaWave"
        val mediaType = if (quality == DownloadQuality.AUDIO_ONLY)
            Environment.DIRECTORY_MUSIC
        else
            Environment.DIRECTORY_MOVIES

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                File(Environment.getExternalStoragePublicDirectory(mediaType), subDir)
            } else {
                // Always writable without extra permissions
                File(context.getExternalFilesDir(mediaType), subDir)
            }
        } else {
            File(Environment.getExternalStoragePublicDirectory(mediaType), subDir)
        }
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
