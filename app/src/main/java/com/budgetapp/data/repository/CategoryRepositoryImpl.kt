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

    companion object {
        fun defaultCategories() = listOf(
            CategoryEntity(name = "Amlot", icon = "📈", color = "#CDDC39", isCustom = false),
            CategoryEntity(name = "Arnona", icon = "🏛️", color = "#607D8B", isCustom = false),
            CategoryEntity(name = "Beauty", icon = "💄", color = "#E91E63", isCustom = false),
            CategoryEntity(name = "Bituah Leumi Kizvat Yeladim", icon = "👶", color = "#2196F3", isCustom = false),
            CategoryEntity(name = "Credit Card", icon = "💳", color = "#3F51B5", isCustom = false),
            CategoryEntity(name = "Electrical Products and Fixing", icon = "🔌", color = "#FF9800", isCustom = false),
            CategoryEntity(name = "Electricity", icon = "⚡", color = "#FFEB3B", isCustom = false),
            CategoryEntity(name = "Entertainment", icon = "🎬", color = "#9C27B0", isCustom = false),
            CategoryEntity(name = "Erica School", icon = "🏫", color = "#03A9F4", isCustom = false),
            CategoryEntity(name = "Food", icon = "🍽️", color = "#FF9800", isCustom = false),
            CategoryEntity(name = "Gaz", icon = "🔥", color = "#FF5722", isCustom = false),
            CategoryEntity(name = "Health", icon = "⚕️", color = "#F44336", isCustom = false),
            CategoryEntity(name = "Housing", icon = "🏠", color = "#795548", isCustom = false),
            CategoryEntity(name = "Income from Mom & Dad", icon = "👨‍👩‍👧", color = "#4CAF50", isCustom = false),
            CategoryEntity(name = "Insurance", icon = "🛡️", color = "#2196F3", isCustom = false),
            CategoryEntity(name = "Mashkanta", icon = "🏦", color = "#673AB7", isCustom = false),
            CategoryEntity(name = "Novakids", icon = "🧸", color = "#E91E63", isCustom = false),
            CategoryEntity(name = "Other", icon = "📋", color = "#607D8B", isCustom = false),
            CategoryEntity(name = "Refund from Anna and Kolya", icon = "💸", color = "#4CAF50", isCustom = false),
            CategoryEntity(name = "Rent", icon = "🏘️", color = "#795548", isCustom = false),
            CategoryEntity(name = "Restaurant & Fun", icon = "🍕", color = "#FF5722", isCustom = false),
            CategoryEntity(name = "Salary", icon = "💰", color = "#4CAF50", isCustom = false),
            CategoryEntity(name = "Salary (Income)", icon = "💰", color = "#8BC34A", isCustom = false),
            CategoryEntity(name = "Savings", icon = "💎", color = "#00BCD4", isCustom = false),
            CategoryEntity(name = "Savings Erica Mizrahi", icon = "💎", color = "#009688", isCustom = false),
            CategoryEntity(name = "Shopping", icon = "🛍️", color = "#E91E63", isCustom = false),
            CategoryEntity(name = "Sport", icon = "🏃", color = "#8BC34A", isCustom = false),
            CategoryEntity(name = "Superfarm", icon = "💊", color = "#F44336", isCustom = false),
            CategoryEntity(name = "Transfer to Erica's Account", icon = "💸", color = "#03A9F4", isCustom = false),
            CategoryEntity(name = "Transport", icon = "🚌", color = "#FF5722", isCustom = false),
            CategoryEntity(name = "Utilities", icon = "💡", color = "#3F51B5", isCustom = false),
            CategoryEntity(name = "Vaad Ha Bait", icon = "🏘️", color = "#607D8B", isCustom = false),
            CategoryEntity(name = "Vacation", icon = "✈️", color = "#2196F3", isCustom = false),
            CategoryEntity(name = "Water", icon = "💧", color = "#00BCD4", isCustom = false)
        )
    }

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
        if (categoryDao.getAllCategoriesSync().isNotEmpty()) return
        categoryDao.insertCategories(defaultCategories())
    }
}
