package com.mediawave.downloader.util

import android.content.Context
import android.net.Uri
import com.mediawave.downloader.data.model.AppBackup
import com.mediawave.downloader.data.model.AppSettingsBackup
import com.mediawave.downloader.data.repository.DownloadRepository
import com.mediawave.downloader.data.repository.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val prefs: UserPreferences
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun createBackup(): String {
        val downloads = repository.allDownloads.first()
        val cookies = repository.allCookies.first()
        val settings = AppSettingsBackup(
            quality = prefs.qualityFlow.first(),
            theme = prefs.themeFlow.first(),
            autoPaste = prefs.autoPasteFlow.first(),
            askQuality = prefs.askQualityFlow.first(),
            language = prefs.languageFlow.first()
        )

        val backup = AppBackup(
            downloads = downloads,
            cookies = cookies,
            settings = settings
        )

        return json.encodeToString(backup)
    }

    suspend fun exportToFile(uri: Uri) {
        val backupJson = createBackup()
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(backupJson.toByteArray())
        }
    }

    suspend fun importFromFile(uri: Uri): Boolean {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content == null) return false

            val backup = json.decodeFromString<AppBackup>(content)

            // Restore downloads (inserting all)
            backup.downloads.forEach { record ->
                repository.insertDownload(record.copy(id = 0)) // Reset ID to let Room generate new ones or avoid conflicts
            }

            // Restore cookies
            backup.cookies.forEach { cookie ->
                repository.insertCookie(cookie.copy(id = 0))
            }

            // Restore settings
            prefs.setQuality(backup.settings.quality)
            prefs.setTheme(backup.settings.theme)
            prefs.setAutoPaste(backup.settings.autoPaste)
            prefs.setAskQuality(backup.settings.askQuality)
            prefs.setLanguage(backup.settings.language)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
