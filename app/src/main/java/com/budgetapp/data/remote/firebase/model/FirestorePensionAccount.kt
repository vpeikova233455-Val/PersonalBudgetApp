package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestorePensionAccount(
    @PropertyName("id") val id: Long = 0,
    @PropertyName("userId") val userId: String = "",
    @PropertyName("accountName") val accountName: String = "",
    @PropertyName("provider") val provider: String = "",
    @PropertyName("currentValue") val currentValue: Double = 0.0,
    @PropertyName("contributionAmount") val contributionAmount: Double = 0.0,
    @PropertyName("employerContribution") val employerContribution: Double? = null,
    @PropertyName("contributionFrequency") val contributionFrequency: String = "",
    @PropertyName("accountType") val accountType: String = "PENSION",
    @PropertyName("notes") val notes: String = "",
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0
)
