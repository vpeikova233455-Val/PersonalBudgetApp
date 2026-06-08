package com.budgetapp.data.mapper

import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id           = id,
    name         = name,
    icon         = icon,
    color        = color,
    isCustom     = isCustom,
    displayOrder = displayOrder
)

fun Category.toEntity(userId: String? = null): CategoryEntity = CategoryEntity(
    id           = id,
    name         = name,
    icon         = icon,
    color        = color,
    isCustom     = isCustom,
    userId       = userId,
    displayOrder = displayOrder
)
