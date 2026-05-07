package com.budgetapp.core.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun Double.toCurrency(): String {
    val format = NumberFormat.getCurrencyInstance()
    format.currency = java.util.Currency.getInstance("ILS")
    return format.format(this)
}

fun Long.toDateString(pattern: String = "MMM dd, yyyy"): String {
    val date = Date(this)
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(date)
}

fun String.toTimestamp(): Long {
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        formatter.parse(this)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

fun generateUUID(): String {
    return UUID.randomUUID().toString()
}

fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun Double.formatPercentage(): String {
    return "${(this * 100).toInt()}%"
}
