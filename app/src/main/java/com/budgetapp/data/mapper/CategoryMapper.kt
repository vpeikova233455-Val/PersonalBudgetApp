package com.budgetapp.data.mapper

import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.domain.model.Category

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isCustom = isCustom
    )
}

fun Category.toEntity(userId: String? = null): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isCustom = isCustom,
        userId = userId
    )
}
