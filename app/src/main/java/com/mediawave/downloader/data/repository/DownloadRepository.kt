package com.mediawave.downloader.data.repository

import com.mediawave.downloader.data.db.CookieDao
import com.mediawave.downloader.data.db.DownloadDao
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val cookieDao: CookieDao,
) {
    val allDownloads: Flow<List<DownloadRecord>> = downloadDao.getAllDownloads()
    val allCookies: Flow<List<CookieProfile>> = cookieDao.getAllCookies()

    suspend fun insertDownload(record: DownloadRecord): Long = downloadDao.insertDownload(record)

    suspend fun deleteDownload(record: DownloadRecord) = downloadDao.deleteDownload(record)

    suspend fun clearHistory() = downloadDao.clearAll()

    suspend fun insertCookie(profile: CookieProfile): Long = cookieDao.insertCookie(profile)

    suspend fun deleteCookie(profile: CookieProfile) = cookieDao.deleteCookie(profile)

    suspend fun getActiveCookie(): CookieProfile? = cookieDao.getActiveCookie()

    suspend fun setActiveCookie(id: Int) {
        cookieDao.deactivateAll()
        cookieDao.setActive(id)
    }

    suspend fun deactivateAllCookies() = cookieDao.deactivateAll()

    /** Toggle a single profile's isActive flag without touching others. */
    suspend fun toggleCookie(id: Int, enabled: Boolean) {
        cookieDao.setActiveById(id, enabled)
    }

    /**
     * Finds the best cookie profile for [url] by matching the stored site domain
     * against the download URL. Only enabled (isActive) profiles are considered.
     * Returns null if no active profile matches.
     */
    suspend fun getCookieForUrl(url: String): CookieProfile? {
        val allActive = cookieDao.getAllActiveCookies()
        fun domainOf(raw: String) = raw
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/').substringBefore('/')
        return allActive.firstOrNull { profile ->
            url.contains(domainOf(profile.url), ignoreCase = true)
        }
    }
}
