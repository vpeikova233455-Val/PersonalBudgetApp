package com.budgetapp.data.remote.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for OCR (Optical Character Recognition) using Gemini Vision API
 * Extracts transaction data from bank statement screenshots
 */
@Singleton
class GeminiOcrService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiModel: GenerativeModel
) {
    /**
     * Extract transactions from a bank statement screenshot
     * @param imageUri URI of the screenshot
     * @return List of extracted transactions
     */
    suspend fun extractTransactionsFromImage(imageUri: Uri): OcrResult {
        return withContext(Dispatchers.IO) {
            try {
                // Load and compress image
                val bitmap = loadBitmap(imageUri)
                    ?: return@withContext OcrResult.Error("Failed to load image")

                // Generate content with vision prompt
                val prompt = buildOcrPrompt()
                val response = geminiModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val extractedText = response.text
                    ?: return@withContext OcrResult.Error("No text extracted from image")

                // Parse the response into structured transactions
                parseOcrResponse(extractedText)

            } catch (e: Exception) {
                OcrResult.Error("OCR failed: ${e.message}")
            }
        }
    }

    /**
     * Extract text only from image (for general OCR)
     */
    suspend fun extractTextFromImage(imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(imageUri) ?: return@withContext null

                val response = geminiModel.generateContent(
                    content {
                        image(bitmap)
                        text("Extract and return all visible text from this image. Return the raw text only.")
                    }
                )

                response.text
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null

            // Decode with size limit to avoid memory issues
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size to limit dimensions
            val maxDimension = 2048
            var scale = 1
            while (options.outWidth / scale > maxDimension ||
                options.outHeight / scale > maxDimension
            ) {
                scale *= 2
            }

            // Decode actual bitmap
            val inputStream2 = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2?.close()

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun buildOcrPrompt(): String {
        return """
            This is a screenshot of a bank statement or transaction list.

            Extract ALL transactions visible in the image. For each transaction, identify:
            1. Date (if visible)
            2. Description/Merchant name
            3. Amount
            4. Whether it's income (+) or expense (-)

            Format your response as follows for EACH transaction:
            ---
            DATE: [YYYY-MM-DD or UNKNOWN]
            DESCRIPTION: [merchant/description]
            AMOUNT: [numeric value only, no currency symbols]
            TYPE: [INCOME or EXPENSE]
            CONFIDENCE: [0.0 to 1.0 - how confident are you about this extraction]
            ---

            Rules:
            - Extract every transaction you can see
            - If date is not visible, use "UNKNOWN"
            - Remove all currency symbols from amounts
            - EXPENSE is money going out (negative, debits, purchases)
            - INCOME is money coming in (positive, credits, deposits)
            - Be conservative with confidence scores
            - If you can't read something clearly, note it in the description and lower confidence

            Return ONLY the formatted transaction data, no additional text.
        """.trimIndent()
    }

    private fun parseOcrResponse(text: String): OcrResult {
        val transactions = mutableListOf<ParsedTransaction>()

        // Split by transaction separator
        val transactionBlocks = text.split("---").filter { it.trim().isNotEmpty() }

        for (block in transactionBlocks) {
            try {
                val lines = block.trim().lines().map { it.trim() }
                val data = lines.associate { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim() to parts[1].trim()
                    } else {
                        "" to ""
                    }
                }

                val date = data["DATE"]?.takeIf { it != "UNKNOWN" }
                val description = data["DESCRIPTION"] ?: continue
                val amountStr = data["AMOUNT"] ?: continue
                val typeStr = data["TYPE"] ?: continue
                val confidence = data["CONFIDENCE"]?.toDoubleOrNull() ?: 0.5

                val amount = amountStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: continue
                val type = when (typeStr.uppercase()) {
                    "INCOME" -> TransactionType.INCOME
                    "EXPENSE" -> TransactionType.EXPENSE
                    else -> continue
                }

                transactions.add(
                    ParsedTransaction(
                        description = description,
                        amount = amount,
                        date = date,
                        type = type,
                        rawData = block
                    )
                )
            } catch (e: Exception) {
                // Skip invalid transaction blocks
                continue
            }
        }

        return if (transactions.isEmpty()) {
            OcrResult.Error("No valid transactions found in image")
        } else {
            OcrResult.Success(transactions)
        }
    }
}

sealed class OcrResult {
    data class Success(val transactions: List<ParsedTransaction>) : OcrResult()
    data class Error(val message: String) : OcrResult()
}
