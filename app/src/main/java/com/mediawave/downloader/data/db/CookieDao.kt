package com.mediawave.downloader.data.db

import androidx.room.*
import com.mediawave.downloader.data.model.CookieProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookie_profiles ORDER BY createdAt DESC")
    fun getAllCookies(): Flow<List<CookieProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCookie(profile: CookieProfile): Long

    @Delete
    suspend fun deleteCookie(profile: CookieProfile)

    @Update
    suspend fun updateCookie(profile: CookieProfile)

    @Query("SELECT * FROM cookie_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveCookie(): CookieProfile?

    /** Returns ALL profiles that have isActive = 1 (for URL-based matching). */
    @Query("SELECT * FROM cookie_profiles WHERE isActive = 1")
    suspend fun getAllActiveCookies(): List<CookieProfile>

    @Query("UPDATE cookie_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE cookie_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Int)

    /** Toggle a single profile without affecting others. */
    @Query("UPDATE cookie_profiles SET isActive = :active WHERE id = :id")
    suspend fun setActiveById(id: Int, active: Boolean)
}
