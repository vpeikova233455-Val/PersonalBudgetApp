package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestoreRecurringTransaction(
    @PropertyName("id") val id: String = "",
    @PropertyName("userId") val userId: String = "",
    @PropertyName("type") val type: String = "",
    @PropertyName("amount") val amount: Double = 0.0,
    @PropertyName("description") val description: String = "",
    @PropertyName("categoryId") val categoryId: Long = 0,
    @PropertyName("frequency") val frequency: String = "",
    @PropertyName("startDate") val startDate: Long = 0,
    @PropertyName("endDate") val endDate: Long? = null,
    @PropertyName("isActive") val isActive: Boolean = true,
    @PropertyName("lastGeneratedDate") val lastGeneratedDate: Long? = null,
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0
)
