package com.budgetapp.domain.usecase.transaction

import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Transaction
import java.util.Calendar
import kotlin.math.abs

/**
 * Pure-Kotlin analytics helpers for detecting recurring transactions and likely
 * duplicates. Kept stateless so they can be called from any layer and exercised
 * directly in tests.
 */
object TransactionAnalytics {

    enum class Cadence(val days: Int, val displayName: String) {
        WEEKLY(7, "Weekly"),
        BIWEEKLY(14, "Every 2 weeks"),
        MONTHLY(30, "Monthly"),
        BIMONTHLY(60, "Every 2 months"),
        QUARTERLY(90, "Quarterly"),
        YEARLY(365, "Yearly")
    }

    data class RecurringPattern(
        val descriptionKey: String,      // normalized description used for grouping
        val sampleDescription: String,    // representative original description
        val type: TransactionType,
        val avgAmount: Double,
        val amountVariationPct: Double,   // 0.0 = identical, 1.0 = highly variable
        val cadence: Cadence,
        val occurrences: Int,
        val lastDate: Long,
        val nextExpectedDate: Long
    )

    data class DuplicateCandidate(
        val pendingId: Long,
        val existingId: String,
        val existingDescription: String,
        val score: Double,                // 0.0–1.0; >= 0.7 ≈ likely duplicate
        val reasons: List<String>
    )

    /**
     * Detect recurring transactions by grouping on normalized description and
     * checking that the dates form a roughly regular cadence with stable amounts.
     */
    fun detectRecurring(transactions: List<Transaction>): List<RecurringPattern> {
        if (transactions.isEmpty()) return emptyList()
        val groups = transactions.groupBy { normalizeDescription(it.description) }
        val patterns = mutableListOf<RecurringPattern>()

        for ((key, txs) in groups) {
            if (txs.size < 2) continue
            // Collapse same-day same-amount duplicates (e.g. a bank issuing two
            // identical 732 refunds on the same date) so they don't break the
            // interval calculation with zero-day gaps.
            val sorted = txs
                .sortedBy { it.date }
                .distinctBy { (it.date / DAY_MS) to it.amount }
            if (sorted.size < 2) continue
            val intervals = sorted.zipWithNext { a, b -> (b.date - a.date) / DAY_MS }
            if (intervals.isEmpty()) continue
            val medianInterval = intervals.sorted().let { it[it.size / 2] }
            val cadence = matchCadence(medianInterval) ?: continue
            // Reject if intervals are too irregular (>40% of intervals deviate >50% from median)
            val deviants = intervals.count { abs(it - medianInterval) > medianInterval * 0.5 }
            if (deviants.toDouble() / intervals.size > 0.4) continue

            val avg = sorted.sumOf { it.amount } / sorted.size
            val variation = if (avg > 0) sorted.maxOf { abs(it.amount - avg) } / avg else 0.0
            // Detected patterns must have stable amounts (max <50% deviation from mean)
            if (variation > 0.5) continue

            val nextExpected = sorted.last().date + cadence.days * DAY_MS
            patterns += RecurringPattern(
                descriptionKey = key,
                sampleDescription = sorted.last().description,
                type = sorted.last().type,
                avgAmount = avg,
                amountVariationPct = variation,
                cadence = cadence,
                occurrences = sorted.size,
                lastDate = sorted.last().date,
                nextExpectedDate = nextExpected
            )
        }
        return patterns.sortedByDescending { it.avgAmount }
    }

    /**
     * Identify pending transactions that are likely duplicates of already-approved
     * transactions. Match on amount (exact), date proximity, and description
     * similarity. Returns one candidate per pending row that scores >= 0.7.
     */
    fun detectDuplicates(
        pendingList: List<PendingForDup>,
        existingList: List<Transaction>
    ): List<DuplicateCandidate> {
        if (pendingList.isEmpty() || existingList.isEmpty()) return emptyList()
        val candidates = mutableListOf<DuplicateCandidate>()
        for (p in pendingList) {
            val pDate = p.date ?: continue
            val pAmount = p.amount ?: continue
            val pDesc = p.description ?: continue
            var best: DuplicateCandidate? = null
            for (e in existingList) {
                if (e.type != p.type) continue
                if (abs(e.amount - pAmount) > 0.005) continue  // exact-amount match
                val daysDiff = abs(e.date - pDate) / DAY_MS
                if (daysDiff > 3) continue  // ±3 day window
                val sim = descriptionSimilarity(e.description, pDesc)
                val reasons = mutableListOf<String>()
                reasons += "same amount (${"%.2f".format(pAmount)})"
                if (daysDiff == 0L) reasons += "same date"
                else reasons += "date within $daysDiff day${if (daysDiff == 1L) "" else "s"}"
                if (sim >= 0.6) reasons += "description ${"%.0f".format(sim * 100)}% similar"
                val score = 0.5 + (1 - daysDiff / 4.0) * 0.2 + sim * 0.3  // 0..1
                if (sim >= 0.4 && (best == null || score > best.score)) {
                    best = DuplicateCandidate(p.id, e.id, e.description, score.coerceIn(0.0, 1.0), reasons)
                }
            }
            if (best != null && best.score >= 0.7) candidates += best
        }
        return candidates
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private const val DAY_MS = 86_400_000L

    private fun matchCadence(days: Long): Cadence? = when (days) {
        in 5..9     -> Cadence.WEEKLY
        in 12..17   -> Cadence.BIWEEKLY
        in 26..35   -> Cadence.MONTHLY
        in 55..65   -> Cadence.BIMONTHLY
        in 85..95   -> Cadence.QUARTERLY
        in 355..375 -> Cadence.YEARLY
        else        -> null
    }

    /** Drop digits, parenthetical suffixes, and bank prefixes that distract grouping. */
    fun normalizeDescription(d: String): String {
        var s = d.lowercase().trim()
        // Strip Hebrew "(י)" suffix and similar
        s = s.replace(Regex("\\([\\u05d0-\\u05ea]?\\)\\s*$"), "")
        // Strip leading transfer prefixes
        s = s.replace(Regex("^(זיכוי|חיוב|העברה|כספומט)\\s*-?\\s*"), "")
        // Collapse whitespace and trim digits at the end
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("\\s+\\d+\\s*$"), "")
        return s
    }

    /** Jaccard similarity over whitespace-separated tokens, after normalization. */
    fun descriptionSimilarity(a: String, b: String): Double {
        val na = normalizeDescription(a)
        val nb = normalizeDescription(b)
        if (na == nb) return 1.0
        val aTokens = na.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        val bTokens = nb.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
        val intersect = aTokens.intersect(bTokens).size
        val union = aTokens.union(bTokens).size
        return intersect.toDouble() / union
    }

    /** Pending transaction projection — keeps domain layer independent of the entity layer. */
    data class PendingForDup(
        val id: Long,
        val description: String?,
        val amount: Double?,
        val date: Long?,
        val type: TransactionType?
    )
}

/** True if [this] timestamp falls between [start] (inclusive) and [end] (exclusive). */
fun Long.isWithin(start: Long, end: Long) = this in start until end

/** Returns the [year, month] interval bounds in milliseconds, in the system timezone. */
fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year); set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}
