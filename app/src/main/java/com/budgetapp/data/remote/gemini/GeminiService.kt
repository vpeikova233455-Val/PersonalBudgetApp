package com.budgetapp.data.remote.gemini

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interacting with Google Gemini AI
 * Handles OCR, categorization, and intelligent suggestions
 */
@Singleton
class GeminiService @Inject constructor(
    private val geminiModel: GenerativeModel
) {
    /**
     * Analyzes text and extracts transaction information
     * @param text Input text from OCR or user input
     * @return Structured transaction data
     */
    suspend fun extractTransactionFromText(text: String): TransactionExtractionResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildTransactionExtractionPrompt(text)
                val response = geminiModel.generateContent(prompt)
                parseTransactionResponse(response)
            } catch (e: Exception) {
                TransactionExtractionResult.Error(e.message ?: "Failed to extract transaction")
            }
        }
    }

    /**
     * Suggests a category for a transaction description
     * @param description Transaction description
     * @param amount Transaction amount (optional, helps with context)
     * @return Category suggestion with confidence score
     */
    suspend fun suggestCategory(
        description: String,
        amount: Double? = null
    ): CategorySuggestion {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildCategorizationPrompt(description, amount)
                val response = geminiModel.generateContent(prompt)
                parseCategoryResponse(response)
            } catch (e: Exception) {
                CategorySuggestion(
                    categoryName = "Uncategorized",
                    confidence = 0.0,
                    reasoning = "AI categorization failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Asks Gemini for clarification questions about ambiguous transactions
     */
    suspend fun generateClarificationQuestions(
        description: String,
        amount: Double
    ): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    A user has a transaction with:
                    Description: "$description"
                    Amount: $$amount

                    This transaction is ambiguous. Generate 2-3 short, clear questions to help categorize it correctly.
                    Questions should be yes/no or multiple choice when possible.

                    Format: Return only the questions, one per line.
                """.trimIndent()

                val response = geminiModel.generateContent(prompt)
                response.text?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun buildTransactionExtractionPrompt(text: String): String {
        return """
            Extract transaction information from the following text:

            "$text"

            Extract and return in this exact format:
            TYPE: [INCOME or EXPENSE]
            AMOUNT: [numeric value only]
            DESCRIPTION: [brief description]
            DATE: [YYYY-MM-DD format, or "UNKNOWN"]
            CONFIDENCE: [0.0 to 1.0]

            Rules:
            - If amount has currency symbol, remove it
            - If type is unclear, guess based on context
            - If date is missing, return "UNKNOWN"
            - Confidence should reflect how certain you are about the extraction
            - Description should be concise (max 50 characters)

            Return only the formatted data, no additional text.
        """.trimIndent()
    }

    private fun buildCategorizationPrompt(description: String, amount: Double?): String {
        val amountContext = amount?.let { " with amount $${it}" } ?: ""
        return """
            Categorize this transaction$amountContext:
            "$description"

            Available categories:
            - Salary (regular income from employment)
            - Freelance (income from freelance work)
            - Food (groceries, restaurants, dining)
            - Housing (rent, mortgage, utilities)
            - Transportation (gas, public transit, car maintenance)
            - Entertainment (movies, games, subscriptions)
            - Healthcare (medical, pharmacy, insurance)
            - Shopping (clothing, electronics, general retail)
            - Education (tuition, books, courses)
            - Travel (flights, hotels, vacation)
            - Bills (utilities, phone, internet)
            - Other (doesn't fit above categories)

            Return in this exact format:
            CATEGORY: [category name from list above]
            CONFIDENCE: [0.0 to 1.0]
            REASON: [one sentence explanation]

            Return only the formatted data, no additional text.
        """.trimIndent()
    }

    private fun parseTransactionResponse(response: GenerateContentResponse): TransactionExtractionResult {
        val text = response.text ?: return TransactionExtractionResult.Error("Empty response")

        return try {
            val lines = text.lines().map { it.trim() }
            val data = lines.associate { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else {
                    "" to ""
                }
            }

            val type = when (data["TYPE"]?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                "EXPENSE" -> TransactionType.EXPENSE
                else -> null
            }

            val amount = data["AMOUNT"]?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
            val description = data["DESCRIPTION"]
            val dateStr = data["DATE"]
            val confidence = data["CONFIDENCE"]?.toDoubleOrNull() ?: 0.5

            if (type != null && amount != null && !description.isNullOrBlank()) {
                TransactionExtractionResult.Success(
                    type = type,
                    amount = amount,
                    description = description,
                    date = if (dateStr != "UNKNOWN") dateStr else null,
                    confidence = confidence
                )
            } else {
                TransactionExtractionResult.Error("Could not parse all required fields")
            }
        } catch (e: Exception) {
            TransactionExtractionResult.Error("Failed to parse response: ${e.message}")
        }
    }

    private fun parseCategoryResponse(response: GenerateContentResponse): CategorySuggestion {
        val text = response.text ?: return CategorySuggestion(
            categoryName = "Other",
            confidence = 0.0,
            reasoning = "Empty response from AI"
        )

        return try {
            val lines = text.lines().map { it.trim() }
            val data = lines.associate { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else {
                    "" to ""
                }
            }

            CategorySuggestion(
                categoryName = data["CATEGORY"] ?: "Other",
                confidence = data["CONFIDENCE"]?.toDoubleOrNull() ?: 0.5,
                reasoning = data["REASON"] ?: "AI suggestion"
            )
        } catch (e: Exception) {
            CategorySuggestion(
                categoryName = "Other",
                confidence = 0.0,
                reasoning = "Failed to parse: ${e.message}"
            )
        }
    }
}

// Data classes
enum class TransactionType {
    INCOME, EXPENSE
}

sealed class TransactionExtractionResult {
    data class Success(
        val type: TransactionType,
        val amount: Double,
        val description: String,
        val date: String?,
        val confidence: Double
    ) : TransactionExtractionResult()

    data class Error(val message: String) : TransactionExtractionResult()
}

data class CategorySuggestion(
    val categoryName: String,
    val confidence: Double,
    val reasoning: String
)
