package com.budgetapp.domain.model

data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val isCustom: Boolean = false,
    val displayOrder: Int = 0
)
