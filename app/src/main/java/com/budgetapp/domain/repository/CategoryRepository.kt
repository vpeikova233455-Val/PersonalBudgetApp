package com.budgetapp.domain.repository

import com.budgetapp.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    fun getBuiltInCategories(): Flow<List<Category>>
    fun getCustomCategories(userId: String): Flow<List<Category>>
    suspend fun getCategoryById(categoryId: Long): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun seedBuiltInCategories()
}
