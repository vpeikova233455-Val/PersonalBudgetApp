package com.budgetapp.domain.usecase.ai

import com.budgetapp.data.local.database.dao.UserCategoryPreferenceDao
import com.budgetapp.data.local.entity.UserCategoryPreference
import javax.inject.Inject

/**
 * Learning mechanism that stores user's categorization choices
 * Improves future AI suggestions by learning from corrections
 */
class LearnFromUserUseCase @Inject constructor(
    private val userPreferenceDao: UserCategoryPreferenceDao
) {
    /**
     * Learn from user's category choice for a transaction
     * Extracts merchant pattern and stores/updates preference
     */
    suspend operator fun invoke(
        userId: String,
        description: String,
        selectedCategoryId: Long
    ) {
        // Extract merchant pattern from description
        val merchantPattern = extractMerchantPattern(description)

        if (merchantPattern.isNullOrBlank()) {
            // Can't learn from generic descriptions
            return
        }

        // Check if preference already exists
        val existingPreference = userPreferenceDao.getPreferenceByPattern(
            userId = userId,
            pattern = merchantPattern
        )

        if (existingPreference != null) {
            // Update existing preference
            if (existingPreference.categoryId == selectedCategoryId) {
                // Same category - increment usage count (reinforcement)
                userPreferenceDao.updatePreference(
                    existingPreference.copy(
                        usageCount = existingPreference.usageCount + 1,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                )
            } else {
                // Different category - user changed their mind, update preference
                userPreferenceDao.updatePreference(
                    existingPreference.copy(
                        categoryId = selectedCategoryId,
                        usageCount = 1, // Reset count for new category
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        } else {
            // Create new preference
            userPreferenceDao.insertPreference(
                UserCategoryPreference(
                    userId = userId,
                    merchantPattern = merchantPattern,
                    categoryId = selectedCategoryId,
                    usageCount = 1,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Learn from multiple transactions at once (bulk import)
     */
    suspend fun learnBulk(
        userId: String,
        transactions: List<Pair<String, Long>> // description to categoryId
    ) {
        transactions.forEach { (description, categoryId) ->
            invoke(userId, description, categoryId)
        }
    }

    /**
     * Extract merchant/pattern from transaction description
     * Returns the key part that can be reused for matching
     */
    private fun extractMerchantPattern(description: String): String? {
        val cleaned = description.trim().lowercase()

        if (cleaned.length < 3) {
            return null // Too short to be useful
        }

        // Remove common noise words
        val noiseWords = setOf(
            "payment", "purchase", "transaction", "debit", "credit",
            "online", "pos", "card", "ending", "auth", "pending",
            "recurring", "automatic", "transfer", "deposit", "withdrawal",
            "at", "in", "on", "from", "to", "the", "and", "or"
        )

        val words = cleaned.split(Regex("\\s+"))
        val meaningfulWords = words.filter { word ->
            word.length >= 3 && !noiseWords.contains(word) && !word.matches(Regex("\\d+"))
        }

        if (meaningfulWords.isEmpty()) {
            // Try to extract first meaningful part
            val parts = cleaned.split(Regex("[^a-z0-9]"))
            return parts.firstOrNull { it.length >= 3 }
        }

        // Use first 1-2 meaningful words as pattern
        return meaningfulWords.take(2).joinToString(" ")
    }

    /**
     * Get learning statistics for user (optional - for UI display)
     */
    suspend fun getLearningStats(userId: String): LearningStats {
        val preferences = userPreferenceDao.getAllPreferencesSync(userId)

        return LearningStats(
            totalPatterns = preferences.size,
            mostUsedPattern = preferences.maxByOrNull { it.usageCount },
            recentlyLearned = preferences.sortedByDescending { it.lastUsedTimestamp }.take(5)
        )
    }
}

data class LearningStats(
    val totalPatterns: Int,
    val mostUsedPattern: UserCategoryPreference?,
    val recentlyLearned: List<UserCategoryPreference>
)
