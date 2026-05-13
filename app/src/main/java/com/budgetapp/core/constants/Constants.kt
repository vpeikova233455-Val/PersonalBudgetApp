package com.budgetapp.core.constants

object Constants {
    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_TRANSACTIONS = "transactions"
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_BUDGETS = "budgets"
    const val COLLECTION_RECURRING = "recurring_transactions"
    const val COLLECTION_PENSIONS = "pension_accounts"

    // Shared Preferences
    const val PREFS_NAME = "secure_prefs"
    const val KEY_USER_ID = "user_id"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_GITHUB_TOKEN = "github_access_token"
    const val KEY_GITHUB_OWNER = "github_owner"
    const val KEY_GITHUB_REPO = "github_repo"

    // AI
    const val GEMINI_MODEL = "gemini-1.5-flash"
    const val AI_CONFIDENCE_THRESHOLD = 0.7
    const val MAX_AI_RETRIES = 3

    // Sync
    const val SYNC_INTERVAL_MINUTES = 15L
    const val SYNC_BATCH_SIZE = 50

    // Date Format
    const val DATE_FORMAT_DISPLAY = "MMM dd, yyyy"
    const val DATE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    // Budget Alerts
    const val DEFAULT_ALERT_THRESHOLD = 0.8 // 80%

    // Pagination
    const val PAGE_SIZE = 20
}
