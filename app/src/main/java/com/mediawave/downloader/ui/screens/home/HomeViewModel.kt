package com.mediawave.downloader.ui.screens.home

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediawave.downloader.R
import com.mediawave.downloader.data.model.*
import com.mediawave.downloader.util.BackupManager
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
    val showQualityDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val ytdlpVersion: String = "Unknown",
    val isUpdatingYtdlp: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val repository: DownloadRepository,
    private val prefs: UserPreferences,
    private val backupManager: BackupManager,
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
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return
        
        // Prevent starting the same URL if it's already downloading
        val isAlreadyDownloading = activeDownloads.value.values.any { it.url == url }
        if (isAlreadyDownloading) {
            _uiState.update { it.copy(snackbarMessage = "⚠️ Це посилання вже завантажується") }
            return
        }
        
        _uiState.update { it.copy(showQualityDialog = true) }
    }

    fun dismissQualityDialog() {
        _uiState.update { it.copy(showQualityDialog = false) }
    }

    fun startDownload(quality: DownloadQuality) {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        // Final duplicate check
        if (activeDownloads.value.values.any { it.url == url }) return

        // Clear input immediately to allow next one
        _uiState.update { it.copy(showQualityDialog = false, urlInput = "") }

        DownloadService.start(context)

        viewModelScope.launch(Dispatchers.IO) {
            val pendingRecordId = repository.insertDownload(
                DownloadRecord(
                    title = url.substringAfterLast("/"),
                    author = "",
                    sourceUrl = url,
                    thumbnailUrl = "",
                    filePath = "",
                    quality = quality.label,
                    extractor = "",
                    status = DownloadStatus.DOWNLOADING,
                )
            )
            val activeCookie = repository.getCookieForUrl(url)

            downloadManager.startDownload(
                url = url,
                quality = quality,
                cookieProfile = activeCookie,
                dbRecordId = pendingRecordId,
                onProgress = { },
                onComplete = { filePath, title, author, thumbnail, extractor ->
                    viewModelScope.launch {
                        repository.updateRecord(
                            id = pendingRecordId,
                            title = title,
                            author = author,
                            thumbnailUrl = thumbnail,
                            filePath = filePath,
                            extractor = extractor,
                            status = DownloadStatus.COMPLETED,
                        )
                    }
                    _uiState.update { it.copy(snackbarMessage = context.getString(R.string.msg_download_complete)) }
                    if (downloadManager.activeDownloads.value.isEmpty()) DownloadService.stop(context)
                },
                onError = { error ->
                    viewModelScope.launch { repository.updateStatus(pendingRecordId, DownloadStatus.FAILED) }
                    _uiState.update { it.copy(snackbarMessage = "❌ $error") }
                    if (downloadManager.activeDownloads.value.isEmpty()) DownloadService.stop(context)
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
                
                // Duplicate check before start
                val url = _uiState.value.urlInput.trim()
                if (activeDownloads.value.values.any { it.url == url }) {
                    _uiState.update { it.copy(snackbarMessage = "⚠️ Це посилання вже завантажується") }
                    return@launch
                }
                
                startDownload(quality)
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
        if (downloadManager.activeDownloads.value.isEmpty()) DownloadService.stop(context)
    }

    fun deleteDownload(record: DownloadRecord) {
        viewModelScope.launch { repository.deleteDownload(record) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun addCookie(name: String, url: String, content: String) {
        viewModelScope.launch { repository.insertCookie(CookieProfile(name = name, url = url, content = content)) }
    }

    fun deleteCookie(profile: CookieProfile) {
        viewModelScope.launch { repository.deleteCookie(profile) }
    }

    fun setActiveCookie(id: Int) {
        viewModelScope.launch { repository.setActiveCookie(id) }
    }

    fun deactivateAllCookies() {
        viewModelScope.launch { repository.deactivateAllCookies() }
    }

    fun toggleCookie(id: Int, enabled: Boolean) {
        viewModelScope.launch { repository.toggleCookie(id, enabled) }
    }

    fun updateYtdlp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingYtdlp = true) }
            val version = downloadManager.updateYtdlp { newVersion ->
                viewModelScope.launch { prefs.setYtdlpVersion(newVersion) }
            }
            _uiState.update {
                it.copy(
                    isUpdatingYtdlp = false,
                    ytdlpVersion = version,
                    snackbarMessage = context.getString(R.string.ytdlp_updated, version),
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
        viewModelScope.launch { prefs.setLanguage(code) }
        LocaleHelper.saveLang(context, code)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.exportToFile(uri)
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.msg_backup_saved)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.msg_backup_export_error, e.message)) }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = backupManager.importFromFile(uri)
            if (success) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.msg_backup_restored)) }
            } else {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.msg_backup_import_error)) }
            }
        }
    }
}
