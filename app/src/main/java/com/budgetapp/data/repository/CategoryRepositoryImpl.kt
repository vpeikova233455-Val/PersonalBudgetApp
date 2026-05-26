package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.budgetapp.data.local.entity.*
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.data.mapper.toEntity
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val changeLogDao: ChangeLogDao
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

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { it.map { e -> e.toDomain() } }

    override fun getBuiltInCategories(): Flow<List<Category>> =
        categoryDao.getBuiltInCategories().map { it.map { e -> e.toDomain() } }

    override fun getCustomCategories(userId: String): Flow<List<Category>> =
        categoryDao.getCustomCategories(userId).map { it.map { e -> e.toDomain() } }

    override suspend fun getCategoryById(categoryId: Long): Category? =
        categoryDao.getCategoryById(categoryId)?.toDomain()

    override suspend fun insertCategory(category: Category): Long {
        val id = categoryDao.insertCategory(category.toEntity())
        logChange(ChangeAction.CREATE, id.toString(), category.toDisplayName(), category.toEntity().copy(id = id).toSnapshot())
        return id
    }

    override suspend fun updateCategory(category: Category) {
        val oldEntity = categoryDao.getCategoryById(category.id)
        categoryDao.updateCategory(category.toEntity())
        val snapshot = if (oldEntity != null) buildUpdateSnapshot(oldEntity.toSnapshot(), category.toEntity().toSnapshot())
                       else category.toEntity().toSnapshot()
        logChange(ChangeAction.UPDATE, category.id.toString(), category.toDisplayName(), snapshot)
    }

    override suspend fun deleteCategory(category: Category) {
        logChange(ChangeAction.DELETE, category.id.toString(), category.toDisplayName(), category.toEntity().toSnapshot())
        categoryDao.deleteCategoryById(category.id)
    }

    override suspend fun seedBuiltInCategories() {
        if (categoryDao.getAllCategoriesSync().isNotEmpty()) return
        categoryDao.insertCategories(defaultCategories())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun logChange(action: ChangeAction, id: String, displayName: String, snapshot: String) {
        runCatching {
            changeLogDao.insert(
                ChangeLogEntity(action = action.name, entityType = HistoryEntityType.CATEGORY.name,
                    entityId = id, displayName = displayName, snapshot = snapshot)
            )
        }
    }

    private fun Category.toDisplayName() = "${icon} $name"

    private fun CategoryEntity.toSnapshot() = JSONObject().apply {
        put("id", id); put("name", name); put("icon", icon); put("color", color)
        put("isCustom", isCustom); put("userId", userId)
    }.toString()

    private fun buildUpdateSnapshot(old: String, new: String) =
        JSONObject().put("old", old).put("new", new).toString()
}
