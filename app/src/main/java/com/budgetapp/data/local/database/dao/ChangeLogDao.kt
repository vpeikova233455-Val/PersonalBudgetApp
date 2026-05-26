package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.ChangeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeLogDao {

    @Query("SELECT * FROM change_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ChangeLogEntity>>

    @Query("SELECT * FROM change_log WHERE entityType = :type ORDER BY timestamp DESC")
    fun getByType(type: String): Flow<List<ChangeLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ChangeLogEntity)

    @Query("DELETE FROM change_log WHERE id NOT IN (SELECT id FROM change_log ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimToLimit(keepCount: Int)

    @Query("DELETE FROM change_log")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM change_log")
    suspend fun count(): Int
}
