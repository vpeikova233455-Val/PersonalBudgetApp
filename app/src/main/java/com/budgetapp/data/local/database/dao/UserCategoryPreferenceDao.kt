package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.UserCategoryPreference
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCategoryPreferenceDao {

    @Query("SELECT * FROM user_category_preferences WHERE userId = :userId ORDER BY usageCount DESC, lastUsedTimestamp DESC")
    fun getAllPreferences(userId: String): Flow<List<UserCategoryPreference>>

    @Query("SELECT * FROM user_category_preferences WHERE userId = :userId ORDER BY usageCount DESC, lastUsedTimestamp DESC")
    suspend fun getAllPreferencesSync(userId: String): List<UserCategoryPreference>

    @Query("SELECT * FROM user_category_preferences WHERE userId = :userId AND merchantPattern LIKE :pattern ORDER BY usageCount DESC LIMIT 1")
    suspend fun getPreferenceByPattern(userId: String, pattern: String): UserCategoryPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserCategoryPreference)

    @Update
    suspend fun updatePreference(preference: UserCategoryPreference)

    @Query("UPDATE user_category_preferences SET usageCount = usageCount + 1, lastUsedTimestamp = :timestamp WHERE id = :preferenceId")
    suspend fun incrementUsageCount(preferenceId: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deletePreference(preference: UserCategoryPreference)
}
