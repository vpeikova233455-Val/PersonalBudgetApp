package com.budgetapp.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptionManager {

    private var encryptedPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (encryptedPrefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun getEncryptedPreferences(context: Context): SharedPreferences {
        if (encryptedPrefs == null) {
            initialize(context)
        }
        return encryptedPrefs!!
    }

    fun saveString(context: Context, key: String, value: String) {
        getEncryptedPreferences(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return getEncryptedPreferences(context).getString(key, defaultValue)
    }

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        getEncryptedPreferences(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getEncryptedPreferences(context).getBoolean(key, defaultValue)
    }

    fun clear(context: Context) {
        getEncryptedPreferences(context).edit().clear().apply()
    }
}
