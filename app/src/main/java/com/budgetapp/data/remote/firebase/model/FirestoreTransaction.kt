package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestoreTransaction(
    @PropertyName("id") val id: String = "",
    @PropertyName("userId") val userId: String = "",
    @PropertyName("type") val type: String = "",
    @PropertyName("amount") val amount: Double = 0.0,
    @PropertyName("description") val description: String = "",
    @PropertyName("categoryId") val categoryId: Long = 0,
    @PropertyName("date") val date: Long = 0,
    @PropertyName("isRecurring") val isRecurring: Boolean = false,
    @PropertyName("recurringId") val recurringId: String? = null,
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0,
    @PropertyName("deviceId") val deviceId: String = "",
    @PropertyName("notes") val notes: String? = null
)
