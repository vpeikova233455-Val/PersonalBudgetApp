package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestoreBudget(
    @PropertyName("id") val id: Long = 0,
    @PropertyName("userId") val userId: String = "",
    @PropertyName("categoryId") val categoryId: Long = 0,
    @PropertyName("monthlyLimit") val monthlyLimit: Double = 0.0,
    @PropertyName("alertThreshold") val alertThreshold: Double = 0.8,
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0
)
