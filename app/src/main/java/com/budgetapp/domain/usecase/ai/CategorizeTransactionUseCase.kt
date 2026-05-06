package com.budgetapp.domain.usecase.ai

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.UserCategoryPreferenceDao
import com.budgetapp.data.remote.gemini.CategorySuggestion
import com.budgetapp.data.remote.gemini.GeminiService
import com.budgetapp.domain.model.Category
import javax.inject.Inject

/**
 * Hybrid AI categorization:
 * 1. Check user's learned preferences first (highest confidence)
 * 2. Apply predefined rules for common merchants
 * 3. Ask Gemini AI for unknown transactions
 * 4. Return suggestion with confidence score
 */
class CategorizeTransactionUseCase @Inject constructor(
    private val userPreferenceDao: UserCategoryPreferenceDao,
    private val categoryDao: CategoryDao,
    private val geminiService: GeminiService
) {
    /**
     * Categorize a transaction using hybrid approach
     * Returns category suggestion with confidence score
     */
    suspend operator fun invoke(
        userId: String,
        description: String,
        amount: Double? = null
    ): CategorizationResult {
        // Step 1: Check user's learned preferences (confidence: 1.0)
        val userPreference = checkUserPreferences(userId, description)
        if (userPreference != null) {
            return CategorizationResult.Success(
                categoryId = userPreference.categoryId,
                categoryName = userPreference.categoryName,
                confidence = 1.0,
                reasoning = "Based on your previous choices",
                source = CategorizationSource.USER_LEARNED
            )
        }

        // Step 2: Check predefined rules (confidence: 0.9)
        val ruleBasedCategory = checkPredefinedRules(description)
        if (ruleBasedCategory != null) {
            return CategorizationResult.Success(
                categoryId = ruleBasedCategory.id,
                categoryName = ruleBasedCategory.name,
                confidence = 0.9,
                reasoning = "Common merchant pattern recognized",
                source = CategorizationSource.RULE_BASED
            )
        }

        // Step 3: Ask Gemini AI (confidence: from AI)
        return try {
            val aiSuggestion = geminiService.suggestCategory(description, amount)
            val category = findCategoryByName(aiSuggestion.categoryName)

            if (category != null && aiSuggestion.confidence >= 0.5) {
                CategorizationResult.Success(
                    categoryId = category.id,
                    categoryName = category.name,
                    confidence = aiSuggestion.confidence,
                    reasoning = aiSuggestion.reasoning,
                    source = CategorizationSource.AI_SUGGESTION
                )
            } else {
                // Low confidence or category not found - ask user
                CategorizationResult.NeedsUserInput(
                    aiSuggestion = aiSuggestion.categoryName,
                    aiConfidence = aiSuggestion.confidence,
                    aiReasoning = aiSuggestion.reasoning
                )
            }
        } catch (e: Exception) {
            CategorizationResult.NeedsUserInput(
                aiSuggestion = null,
                aiConfidence = 0.0,
                aiReasoning = "AI categorization unavailable"
            )
        }
    }

    /**
     * Check if user has a learned preference for this merchant/description
     */
    private suspend fun checkUserPreferences(
        userId: String,
        description: String
    ): UserPreferenceMatch? {
        // Get all user preferences sorted by usage count
        val preferences = userPreferenceDao.getAllPreferencesSync(userId)

        // Check for matches (case-insensitive, partial match)
        val descriptionLower = description.lowercase()
        for (pref in preferences) {
            if (descriptionLower.contains(pref.merchantPattern.lowercase())) {
                val category = categoryDao.getCategoryById(pref.categoryId)
                if (category != null) {
                    return UserPreferenceMatch(
                        categoryId = category.id,
                        categoryName = category.name
                    )
                }
            }
        }

        return null
    }

    /**
     * Predefined rules for common merchants and patterns
     */
    private suspend fun checkPredefinedRules(description: String): Category? {
        val descriptionLower = description.lowercase()

        // Define rules (merchant patterns -> category names)
        val rules = mapOf(
            // Food & Dining
            listOf("starbucks", "coffee", "cafe", "restaurant", "pizza", "burger", "food",
                "mcdonald", "subway", "chipotle", "domino", "delivery") to "Food",

            // Shopping
            listOf("amazon", "walmart", "target", "ebay", "store", "shop", "retail") to "Shopping",

            // Transportation
            listOf("uber", "lyft", "taxi", "gas", "fuel", "parking", "metro", "transit", "bus") to "Transportation",

            // Entertainment
            listOf("netflix", "spotify", "hulu", "disney", "youtube", "movie", "theater",
                "game", "steam", "playstation", "xbox") to "Entertainment",

            // Housing
            listOf("rent", "mortgage", "landlord", "property", "utilities", "electric", "water",
                "gas bill", "internet", "cable") to "Housing",

            // Healthcare
            listOf("pharmacy", "cvs", "walgreens", "doctor", "hospital", "medical", "clinic",
                "health", "insurance") to "Healthcare",

            // Salary & Income
            listOf("salary", "payroll", "income", "payment received", "deposit", "transfer in") to "Salary",

            // Bills
            listOf("phone bill", "mobile", "verizon", "at&t", "t-mobile", "subscription") to "Bills"
        )

        // Check each rule
        for ((patterns, categoryName) in rules) {
            if (patterns.any { pattern -> descriptionLower.contains(pattern) }) {
                val category = findCategoryByName(categoryName)
                if (category != null) {
                    return category
                }
            }
        }

        return null
    }

    /**
     * Find category by name (case-insensitive)
     */
    private suspend fun findCategoryByName(name: String): Category? {
        val categories = categoryDao.getAllCategoriesSync()
        return categories.find { it.name.equals(name, ignoreCase = true) }
    }

    private data class UserPreferenceMatch(
        val categoryId: Long,
        val categoryName: String
    )
}

// Result types
sealed class CategorizationResult {
    data class Success(
        val categoryId: Long,
        val categoryName: String,
        val confidence: Double,
        val reasoning: String,
        val source: CategorizationSource
    ) : CategorizationResult()

    data class NeedsUserInput(
        val aiSuggestion: String?,
        val aiConfidence: Double,
        val aiReasoning: String
    ) : CategorizationResult()
}

enum class CategorizationSource {
    USER_LEARNED,   // From user's previous choices (confidence: 1.0)
    RULE_BASED,     // From predefined rules (confidence: 0.9)
    AI_SUGGESTION   // From Gemini AI (confidence: variable)
}
