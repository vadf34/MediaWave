package com.mediawave.downloader.ui.screens.profile

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.data.model.DownloadRecord
import com.mediawave.downloader.data.model.DownloadStatus
import com.mediawave.downloader.data.repository.DownloadRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

// ─── Platform definitions ─────────────────────────────────────────────────────

data class SocialPlatform(
    val id: String,
    val name: String,
    val domain: String,
    val profileUrlTemplate: String,
    val handlePrefix: String = "",
    val iconEmoji: String = "🌐",
)

val TOP_PLATFORMS = listOf(
    SocialPlatform("instagram",  "Instagram",  "instagram.com",  "https://www.instagram.com/{handle}/",   iconEmoji = "📸"),
    SocialPlatform("tiktok",     "TikTok",     "tiktok.com",     "https://www.tiktok.com/@{handle}",      handlePrefix = "@", iconEmoji = "🎵"),
    SocialPlatform("youtube",    "YouTube",    "youtube.com",    "https://www.youtube.com/@{handle}",     handlePrefix = "@", iconEmoji = "▶️"),
    SocialPlatform("twitter",    "Twitter/X",  "x.com",          "https://x.com/{handle}",                iconEmoji = "✖️"),
    SocialPlatform("facebook",   "Facebook",   "facebook.com",   "https://www.facebook.com/{handle}",     iconEmoji = "👥"),
    SocialPlatform("vk",         "VKontakte",  "vk.com",         "https://vk.com/{handle}",               iconEmoji = "💬"),
    SocialPlatform("reddit",     "Reddit",     "reddit.com",     "https://www.reddit.com/user/{handle}",  iconEmoji = "🔴"),
    SocialPlatform("pinterest",  "Pinterest",  "pinterest.com",  "https://www.pinterest.com/{handle}/",   iconEmoji = "📌"),
    SocialPlatform("twitch",     "Twitch",     "twitch.tv",      "https://www.twitch.tv/{handle}",        iconEmoji = "🟣"),
    SocialPlatform("rumble",     "Rumble",     "rumble.com",     "https://rumble.com/c/{handle}",          iconEmoji = "🎬"),
)

// ─── Data ─────────────────────────────────────────────────────────────────────

data class ProfileMediaItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val duration: String,
    val isVideo: Boolean,
    val uploadDate: String,
    val viewCount: Long,
    var isSelected: Boolean = false,
    var downloadStatus: ProfileItemDownloadStatus = ProfileItemDownloadStatus.IDLE,
    var downloadProgress: Float = 0f,
)

enum class ProfileItemDownloadStatus { IDLE, DOWNLOADING, DONE, FAILED }
enum class ProfileParserState { IDLE, PARSING, DONE, ERROR }
enum class MediaTypeFilter { ALL, VIDEO, PHOTO }

data class ProfileUiState(
    val inputText: String = "",
    val parserState: ProfileParserState = ProfileParserState.IDLE,
    val platformDetected: String = "",
    val usernameDetected: String = "",
    val items: List<ProfileMediaItem> = emptyList(),
    val errorMessage: String? = null,
    val isDownloading: Boolean = false,
    val downloadedCount: Int = 0,
    val totalToDownload: Int = 0,
    val snackbarMessage: String? = null,
    val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val showSelectAll: Boolean = false,
    // Platform picker
    val showPlatformPicker: Boolean = false,
    val customPlatforms: List<SocialPlatform> = emptyList(),
    // Cookie status
    val activeCookieName: String? = null,
    val cookieChecked: Boolean = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileParserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val allPlatforms: List<SocialPlatform>
        get() = TOP_PLATFORMS + _uiState.value.customPlatforms

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun clearInput() = _uiState.update { ProfileUiState() }

    fun setFilter(filter: MediaTypeFilter) = _uiState.update { it.copy(mediaTypeFilter = filter) }

    fun toggleItem(id: String) = _uiState.update { state ->
        state.copy(items = state.items.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it })
    }

    fun selectAll() = _uiState.update { state ->
        state.copy(items = state.items.map { it.copy(isSelected = true) })
    }

    fun deselectAll() = _uiState.update { state ->
        state.copy(items = state.items.map { it.copy(isSelected = false) })
    }

    fun dismissSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    // ── Platform picker ───────────────────────────────────────────────────────

    fun showPlatformPicker() = _uiState.update { it.copy(showPlatformPicker = true) }

    fun dismissPlatformPicker() = _uiState.update { it.copy(showPlatformPicker = false) }

    fun onPlatformSelected(platform: SocialPlatform) {
        val handle = _uiState.value.inputText.trim().removePrefix("@").trim()
        val url = platform.profileUrlTemplate.replace("{handle}", handle)
        _uiState.update {
            it.copy(showPlatformPicker = false, platformDetected = platform.name, usernameDetected = handle)
        }
        startParsing(url, platform.name, handle)
    }

    fun addCustomPlatform(name: String, domain: String) {
        if (name.isBlank() || domain.isBlank()) return
        val clean = domain.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val platform = SocialPlatform(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            domain = clean,
            profileUrlTemplate = "https://$clean/{handle}",
            iconEmoji = "🌐",
        )
        _uiState.update { it.copy(customPlatforms = it.customPlatforms + platform) }
    }

    // ── Input resolution ──────────────────────────────────────────────────────

    private fun resolveInput(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Pair(trimmed, detectPlatform(trimmed))
        }
        val known = (allPlatforms.map { it.domain } + listOf(
            "instagram.com","tiktok.com","twitter.com","x.com","youtube.com","youtu.be",
            "facebook.com","fb.com","vk.com","reddit.com","pinterest.com","twitch.tv","rumble.com",
        )).distinct()
        for (domain in known) {
            if (trimmed.startsWith(domain) || trimmed.startsWith("www.$domain")) {
                val full = "https://$trimmed"
                return Pair(full, detectPlatform(full))
            }
        }
        return Pair("", "")   // bare nick → show picker
    }

    private fun detectPlatform(url: String): String {
        allPlatforms.forEach { p -> if (url.contains(p.domain, ignoreCase = true)) return p.name }
        return "Unknown"
    }

    private fun extractUsername(url: String, platform: String): String {
        return try {
            when (platform) {
                "Instagram"  -> url.substringAfter("instagram.com/").substringBefore("/").removePrefix("@")
                "TikTok"     -> url.substringAfter("tiktok.com/@").substringBefore("/").substringBefore("?")
                "Twitter/X"  -> url.substringAfter(".com/").substringBefore("/").removePrefix("@")
                "YouTube"    -> url.substringAfter("/@").substringBefore("/").ifBlank {
                    url.substringAfter("/user/").substringBefore("/").ifBlank {
                        url.substringAfter("/channel/").substringBefore("/")
                    }
                }
                "VKontakte"  -> url.substringAfter("vk.com/").substringBefore("/").substringBefore("?")
                else -> url.substringAfterLast("/").substringBefore("?").ifBlank { "unknown" }
            }
        } catch (e: Exception) { "unknown" }
    }

    // ── Parse trigger ─────────────────────────────────────────────────────────

    fun parseProfile() {
        val raw = _uiState.value.inputText.trim()
        if (raw.isBlank()) return
        val (resolvedUrl, platform) = resolveInput(raw)
        if (resolvedUrl.isBlank()) {
            _uiState.update { it.copy(showPlatformPicker = true) }
            return
        }
        startParsing(resolvedUrl, platform, extractUsername(resolvedUrl, platform))
    }

    private fun startParsing(url: String, platform: String, username: String) {
        _uiState.update {
            it.copy(
                parserState = ProfileParserState.PARSING,
                platformDetected = platform,
                usernameDetected = username,
                items = emptyList(),
                errorMessage = null,
                activeCookieName = null,
                cookieChecked = false,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = repository.getCookieForUrl(url)
                _uiState.update { it.copy(activeCookieName = cookie?.name, cookieChecked = true) }
                val items = fetchProfileMedia(url, platform, cookie)
                _uiState.update {
                    it.copy(
                        parserState = ProfileParserState.DONE,
                        items = items,
                        snackbarMessage = "✅ Знайдено ${items.size} медіафайлів",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(parserState = ProfileParserState.ERROR, errorMessage = e.message ?: "Помилка парсингу")
                }
            }
        }
    }

    // ── Fetch media ───────────────────────────────────────────────────────────

    private suspend fun fetchProfileMedia(
        url: String,
        platform: String,
        cookieProfile: CookieProfile?,
    ): List<ProfileMediaItem> = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-json")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--playlist-end", "200")
            cookieProfile?.let { cookie ->
                val cookieFile = createTempCookieFile(cookie.content)
                addOption("--cookies", cookieFile.absolutePath)
            }
        }
        val outputLines = mutableListOf<String>()
        YoutubeDL.getInstance().execute(request, null) { _, _, line ->
            if (line.trim().startsWith("{")) outputLines.add(line.trim())
        }
        val items = mutableListOf<ProfileMediaItem>()
        for (line in outputLines) {
            try {
                val json = JSONObject(line)
                val id = json.optString("id", ""); if (id.isBlank()) continue
                val title = json.optString("title", "").ifBlank { json.optString("fulltitle", "Без назви") }
                val entryUrl = json.optString("url", "").ifBlank {
                    json.optString("webpage_url", "").ifBlank { buildEntryUrl(url, platform, id) }
                }
                val thumbnail = run {
                    val d = json.optString("thumbnail", "")
                    if (d.isNotBlank()) d else {
                        val arr = json.optJSONArray("thumbnails")
                        arr?.let { if (it.length() > 0) it.getJSONObject(it.length()-1).optString("url","") else "" } ?: ""
                    }
                }
                val dur = json.optLong("duration", 0L)
                val ext = json.optString("ext", "")
                val vcodec = json.optString("vcodec", "")
                val isVideo = dur > 0 || json.optString("_type","") == "url" ||
                        ext in listOf("mp4","webm","mov","avi","mkv") || (vcodec.isNotBlank() && vcodec != "none")
                items.add(ProfileMediaItem(
                    id = id, title = title, url = entryUrl, thumbnailUrl = thumbnail,
                    duration = if (dur > 0) formatDuration(dur) else "",
                    isVideo = isVideo,
                    uploadDate = formatDate(json.optString("upload_date", "")),
                    viewCount = json.optLong("view_count", 0L),
                ))
            } catch (_: Exception) {}
        }
        items
    }

    private fun buildEntryUrl(profileUrl: String, platform: String, id: String) = when (platform) {
        "Instagram" -> "https://www.instagram.com/p/$id/"
        "TikTok"    -> "https://www.tiktok.com/video/$id"
        "Twitter/X" -> "https://x.com/i/status/$id"
        "YouTube"   -> "https://www.youtube.com/watch?v=$id"
        else        -> "${profileUrl.trimEnd('/')}/$id"
    }

    private fun formatDuration(secs: Long): String {
        val h = secs/3600; val m = (secs%3600)/60; val s = secs%60
        return if (h > 0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)
    }

    private fun formatDate(raw: String) =
        if (raw.length == 8) "${raw.substring(6,8)}.${raw.substring(4,6)}.${raw.substring(0,4)}" else raw

    // ── Download selected ─────────────────────────────────────────────────────

    fun downloadSelected(quality: DownloadQuality) {
        val state = _uiState.value
        val selected = state.items.filter { it.isSelected }
        if (selected.isEmpty()) { _uiState.update { it.copy(snackbarMessage = "⚠️ Виберіть хоча б один файл") }; return }
        val username = state.usernameDetected.ifBlank { "Unknown" }
        val platform = state.platformDetected.ifBlank { "Media" }
        _uiState.update { it.copy(isDownloading = true, downloadedCount = 0, totalToDownload = selected.size) }
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedUrl = resolveInput(state.inputText).first
            val cookieProfile = if (resolvedUrl.isNotBlank()) repository.getCookieForUrl(resolvedUrl) else null
            var doneCount = 0
            for (item in selected) {
                updateItemStatus(item.id, ProfileItemDownloadStatus.DOWNLOADING, 0f)
                try {
                    val outputDir = getProfileOutputDir(username, platform, quality)
                    outputDir.mkdirs()
                    val request = YoutubeDLRequest(item.url).apply {
                        when (quality) {
                            DownloadQuality.HD -> addOption("-f","bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                            DownloadQuality.SD -> addOption("-f","bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                            DownloadQuality.AUDIO_ONLY -> { addOption("-f","bestaudio/best"); addOption("-x"); addOption("--audio-format","mp3") }
                        }
                        addOption("-o","${outputDir.absolutePath}/%(title)s.%(ext)s")
                        if (quality != DownloadQuality.AUDIO_ONLY) addOption("--merge-output-format","mp4")
                        addOption("--add-metadata"); addOption("--no-warnings"); addOption("--no-playlist")
                        addOption("--downloader","aria2c"); addOption("--downloader-args","aria2c:-x 16 -s 16 -k 1M")
                        cookieProfile?.let { cookie ->
                            addOption("--cookies", createTempCookieFile(cookie.content).absolutePath)
                        }
                    }
                    YoutubeDL.getInstance().execute(request, null) { p, _, _ ->
                        updateItemStatus(item.id, ProfileItemDownloadStatus.DOWNLOADING, p/100f)
                    }
                    val file = outputDir.listFiles()
                        ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                        ?.maxByOrNull { it.lastModified() }
                    if (file != null) {
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                            ?: if (quality == DownloadQuality.AUDIO_ONLY) "audio/mpeg" else "video/mp4"
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
                        repository.insertDownload(DownloadRecord(
                            title = item.title.ifBlank { file.nameWithoutExtension }, author = username,
                            sourceUrl = item.url, thumbnailUrl = item.thumbnailUrl,
                            filePath = file.absolutePath, quality = quality.label,
                            extractor = platform, status = DownloadStatus.COMPLETED,
                        ))
                        updateItemStatus(item.id, ProfileItemDownloadStatus.DONE, 1f)
                    } else updateItemStatus(item.id, ProfileItemDownloadStatus.FAILED, 0f)
                    doneCount++; _uiState.update { it.copy(downloadedCount = doneCount) }
                } catch (e: Exception) {
                    updateItemStatus(item.id, ProfileItemDownloadStatus.FAILED, 0f)
                    doneCount++; _uiState.update { it.copy(downloadedCount = doneCount) }
                }
            }
            val ok = _uiState.value.items.count { it.downloadStatus == ProfileItemDownloadStatus.DONE }
            _uiState.update { it.copy(isDownloading = false, snackbarMessage = "✅ Завантажено $ok з ${selected.size} файлів") }
        }
    }

    private fun updateItemStatus(id: String, status: ProfileItemDownloadStatus, progress: Float) =
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(downloadStatus = status, downloadProgress = progress) else it })
        }

    private fun getProfileOutputDir(username: String, platform: String, quality: DownloadQuality): File {
        val safe = username.replace(Regex("[^a-zA-Z0-9_\\-а-яА-ЯіІїЇєЄ]"), "_")
        val plat = platform.replace("/","_").replace(" ","")
        val mediaType = if (quality == DownloadQuality.AUDIO_ONLY) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            File(Environment.getExternalStoragePublicDirectory(mediaType), "MediaWave/${plat}_$safe")
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            File(context.getExternalFilesDir(mediaType), "${plat}_$safe")
        else File(Environment.getExternalStoragePublicDirectory(mediaType), "MediaWave/${plat}_$safe")
    }

    private fun createTempCookieFile(content: String): File {
        val file = File(context.cacheDir, "cookies_${System.currentTimeMillis()}.txt")
        file.writeText(content)
        return file
    }
}
