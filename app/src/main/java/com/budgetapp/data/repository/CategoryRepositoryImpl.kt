package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.data.mapper.toEntity
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBuiltInCategories(): Flow<List<Category>> {
        return categoryDao.getBuiltInCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCustomCategories(userId: String): Flow<List<Category>> {
        return categoryDao.getCustomCategories(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(categoryId: Long): Category? {
        return categoryDao.getCategoryById(categoryId)?.toDomain()
    }

    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategoryById(category.id)
    }

    override suspend fun seedBuiltInCategories() {
        val builtInCategories = listOf(
            CategoryEntity(name = "Salary", icon = "💰", color = "#4CAF50", isCustom = false),
            CategoryEntity(name = "Freelance", icon = "💼", color = "#8BC34A", isCustom = false),
            CategoryEntity(name = "Investment", icon = "📈", color = "#CDDC39", isCustom = false),
            CategoryEntity(name = "Housing", icon = "🏠", color = "#F44336", isCustom = false),
            CategoryEntity(name = "Food & Dining", icon = "🍽️", color = "#FF9800", isCustom = false),
            CategoryEntity(name = "Transportation", icon = "🚗", color = "#FF5722", isCustom = false),
            CategoryEntity(name = "Shopping", icon = "🛍️", color = "#E91E63", isCustom = false),
            CategoryEntity(name = "Entertainment", icon = "🎬", color = "#9C27B0", isCustom = false),
            CategoryEntity(name = "Healthcare", icon = "⚕️", color = "#673AB7", isCustom = false),
            CategoryEntity(name = "Utilities", icon = "💡", color = "#3F51B5", isCustom = false),
            CategoryEntity(name = "Insurance", icon = "🛡️", color = "#2196F3", isCustom = false),
            CategoryEntity(name = "Education", icon = "📚", color = "#03A9F4", isCustom = false),
            CategoryEntity(name = "Savings", icon = "💎", color = "#00BCD4", isCustom = false),
            CategoryEntity(name = "Other", icon = "📋", color = "#607D8B", isCustom = false)
        )
        categoryDao.insertCategories(builtInCategories)
    }
}
