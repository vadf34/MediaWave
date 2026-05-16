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
     * Finds the best cookie profile for [url] by matching the stored site's root domain
     * against the download URL. Only enabled (isActive) profiles are considered.
     *
     * Root domain matching (e.g. "tiktok.com") is used instead of full subdomain matching
     * so that short-link domains like "vt.tiktok.com" are correctly matched against a
     * cookie profile stored as "https://www.tiktok.com".
     *
     * Returns null if no active profile matches.
     */
    suspend fun getCookieForUrl(url: String): CookieProfile? {
        val allActive = cookieDao.getAllActiveCookies()
        return allActive.firstOrNull { profile ->
            rootDomainOf(profile.url).let { root ->
                root.isNotEmpty() && url.contains(root, ignoreCase = true)
            }
        }
    }

    companion object {
        /**
         * Extracts the root domain (last two labels) from a URL string.
         * Examples:
         *   "https://www.tiktok.com/..."  → "tiktok.com"
         *   "https://vt.tiktok.com/..."   → "tiktok.com"
         *   "https://youtube.com/..."     → "youtube.com"
         *   "https://youtu.be/..."        → "youtu.be"
         */
        fun rootDomainOf(raw: String): String {
            val host = raw
                .removePrefix("https://").removePrefix("http://")
                .trimEnd('/').substringBefore('/')
                .substringBefore('?')
            val labels = host.split('.')
            return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
        }
    }
}
