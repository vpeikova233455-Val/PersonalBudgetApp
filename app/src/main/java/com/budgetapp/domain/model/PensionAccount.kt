package com.budgetapp.domain.model

import com.budgetapp.data.local.entity.RecurrenceFrequency

data class PensionAccount(
    val id: Long,
    val accountName: String,
    val provider: String,
    val currentValue: Double,
    val contributionAmount: Double,
    val employerContribution: Double?,
    val contributionFrequency: RecurrenceFrequency
) {
    val totalMonthlyContribution: Double
        get() = contributionAmount + (employerContribution ?: 0.0)
}
