package com.mediawave.downloader.ui.screens.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediawave.downloader.data.model.*
import com.mediawave.downloader.util.LocaleHelper
import com.mediawave.downloader.data.repository.DownloadRepository
import com.mediawave.downloader.data.repository.UserPreferences
import com.mediawave.downloader.download.DownloadManager
import com.mediawave.downloader.download.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val urlInput: String = "",
    val isDownloading: Boolean = false,
    val showQualityDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val activeDownload: ActiveDownload? = null,
    val ytdlpVersion: String = "Unknown",
    val isUpdatingYtdlp: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val repository: DownloadRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val activeDownloads = downloadManager.activeDownloads
    val downloads = repository.allDownloads
    val cookies = repository.allCookies
    val settings = prefs

    init {
        viewModelScope.launch {
            val version = downloadManager.getYtdlpVersion()
            _uiState.update { it.copy(ytdlpVersion = version) }
        }
        // Auto-update yt-dlp on first launch
        viewModelScope.launch(Dispatchers.IO) {
            val isFirstLaunch = prefs.isFirstLaunchFlow.first()
            if (isFirstLaunch) {
                _uiState.update { it.copy(isUpdatingYtdlp = true) }
                downloadManager.updateYtdlp { newVersion ->
                    viewModelScope.launch { prefs.setYtdlpVersion(newVersion) }
                }
                prefs.setFirstLaunch(false)
                val version = downloadManager.getYtdlpVersion()
                _uiState.update { it.copy(isUpdatingYtdlp = false, ytdlpVersion = version) }
            }
        }
    }

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(urlInput = url) }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        if (clip.startsWith("http://") || clip.startsWith("https://")) {
            _uiState.update { it.copy(urlInput = clip) }
        }
    }

    fun clearUrl() {
        _uiState.update { it.copy(urlInput = "") }
    }

    fun showQualityDialog() {
        if (_uiState.value.urlInput.isNotBlank()) {
            _uiState.update { it.copy(showQualityDialog = true) }
        }
    }

    fun dismissQualityDialog() {
        _uiState.update { it.copy(showQualityDialog = false) }
    }

    fun startDownload(quality: DownloadQuality) {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        _uiState.update { it.copy(showQualityDialog = false, isDownloading = true) }

        // Start foreground service so download survives when app is backgrounded
        DownloadService.start(context)

        viewModelScope.launch(Dispatchers.IO) {
            val activeCookie = repository.getCookieForUrl(url)

            downloadManager.startDownload(
                url = url,
                quality = quality,
                cookieProfile = activeCookie,
                onProgress = { download ->
                    _uiState.update { it.copy(activeDownload = download) }
                },
                onComplete = { filePath, title, author, thumbnail, extractor ->
                    viewModelScope.launch {
                        repository.insertDownload(
                            DownloadRecord(
                                title = title.ifBlank { url.substringAfterLast("/") },
                                author = author.ifBlank { "Unknown" },
                                sourceUrl = url,
                                thumbnailUrl = thumbnail,
                                filePath = filePath,
                                quality = quality.label,
                                extractor = extractor,
                                status = DownloadStatus.COMPLETED,
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            activeDownload = null,
                            urlInput = "",
                            snackbarMessage = "✅ Завантаження завершено!",
                        )
                    }
                    DownloadService.stop(context)
                },
                onError = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            activeDownload = null,
                            snackbarMessage = "❌ $error",
                        )
                    }
                    DownloadService.stop(context)
                },
            )
        }
    }

    fun quickDownload() {
        viewModelScope.launch {
            val askQuality = prefs.askQualityFlow.first()
            if (askQuality) {
                showQualityDialog()
            } else {
                val qualityStr = prefs.qualityFlow.first()
                val quality = when (qualityStr) {
                    "SD" -> DownloadQuality.SD
                    "AUDIO" -> DownloadQuality.AUDIO_ONLY
                    else -> DownloadQuality.HD
                }
                startDownload(quality)
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
        _uiState.update { it.copy(isDownloading = false, activeDownload = null) }
        DownloadService.stop(context)
    }

    fun deleteDownload(record: DownloadRecord) {
        viewModelScope.launch {
            repository.deleteDownload(record)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun addCookie(name: String, url: String, content: String) {
        viewModelScope.launch {
            repository.insertCookie(CookieProfile(name = name, url = url, content = content))
        }
    }

    fun deleteCookie(profile: CookieProfile) {
        viewModelScope.launch {
            repository.deleteCookie(profile)
        }
    }

    fun setActiveCookie(id: Int) {
        viewModelScope.launch {
            repository.setActiveCookie(id)
        }
    }

    fun deactivateAllCookies() {
        viewModelScope.launch {
            repository.deactivateAllCookies()
        }
    }

    /**
     * Toggle a single cookie profile on/off independently.
     * Multiple profiles can be active at the same time; the downloader
     * picks the one whose URL domain matches the download URL.
     */
    fun toggleCookie(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleCookie(id, enabled)
        }
    }

    fun updateYtdlp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingYtdlp = true) }
            val version = downloadManager.updateYtdlp { newVersion ->
                prefs.let { viewModelScope.launch { it.setYtdlpVersion(newVersion) } }
            }
            _uiState.update {
                it.copy(
                    isUpdatingYtdlp = false,
                    ytdlpVersion = version,
                    snackbarMessage = "yt-dlp оновлено: $version",
                )
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun setTheme(theme: Int) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch { prefs.setQuality(quality) }
    }

    fun setAutoPaste(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoPaste(enabled) }
    }

    fun setAskQuality(enabled: Boolean) {
        viewModelScope.launch { prefs.setAskQuality(enabled) }
    }

    fun setLanguage(code: String) {
        // Save to DataStore (for display in Settings)
        viewModelScope.launch { prefs.setLanguage(code) }
        // Also save to SharedPreferences so attachBaseContext can read it on next launch
        LocaleHelper.saveLang(context, code)
    }
}
