package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE isCustom = 0 ORDER BY display_order ASC, name ASC")
    fun getBuiltInCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isCustom = 1 AND userId = :userId ORDER BY display_order ASC, name ASC")
    fun getCustomCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Query("UPDATE categories SET display_order = :order WHERE id = :id")
    suspend fun updateCategoryOrder(id: Long, order: Int)

    @Query("SELECT * FROM categories WHERE isCustom = 1 AND syncStatus = :status")
    suspend fun getCustomCategoriesBySyncStatus(status: SyncStatus): List<CategoryEntity>

    @Query("UPDATE categories SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)
}
