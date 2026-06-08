package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestoreLearningRule(
    @PropertyName("id") val id: Long = 0,
    @PropertyName("userId") val userId: String = "",
    @PropertyName("merchantPattern") val merchantPattern: String = "",
    @PropertyName("categoryId") val categoryId: Long = 0,
    @PropertyName("usageCount") val usageCount: Int = 1,
    @PropertyName("lastUsedTimestamp") val lastUsedTimestamp: Long = 0,
    @PropertyName("isAutomatic") val isAutomatic: Boolean = false,
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0
)
