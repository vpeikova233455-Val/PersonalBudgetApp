package com.budgetapp.data.remote.firebase.model

import com.google.firebase.firestore.PropertyName

data class FirestoreCategory(
    @PropertyName("id") val id: Long = 0,
    @PropertyName("name") val name: String = "",
    @PropertyName("icon") val icon: String = "",
    @PropertyName("color") val color: String = "",
    @PropertyName("isCustom") val isCustom: Boolean = false,
    @PropertyName("userId") val userId: String? = null,
    @PropertyName("lastModifiedTimestamp") val lastModifiedTimestamp: Long = 0,
    @PropertyName("displayOrder") val displayOrder: Int = 0
)
