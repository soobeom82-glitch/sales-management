package com.example.vmmswidget.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class WidgetDataStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "widget_data",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveDisplayText(text: String) {
        prefs.edit().putString(KEY_TEXT, text).apply()
    }

    fun getDisplayText(): String = prefs.getString(KEY_TEXT, "데이터 없음") ?: "데이터 없음"

    fun saveAmount(amount: Int) {
        prefs.edit().putInt(KEY_AMOUNT, amount).apply()
    }

    fun getAmount(): Int = prefs.getInt(KEY_AMOUNT, 0)

    fun saveUpdatedAt(text: String) {
        prefs.edit().putString(KEY_UPDATED_AT, text).apply()
    }

    fun getUpdatedAt(): String = prefs.getString(KEY_UPDATED_AT, "--:--:--") ?: "--:--:--"

    fun saveRefreshing(value: Boolean) {
        prefs.edit().putBoolean(KEY_REFRESHING, value).apply()
    }

    fun isRefreshing(): Boolean = prefs.getBoolean(KEY_REFRESHING, false)

    companion object {
        private const val KEY_TEXT = "display_text"
        private const val KEY_AMOUNT = "amount_value"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_REFRESHING = "refreshing"
    }
}
