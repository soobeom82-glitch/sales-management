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

    fun saveEasyShopDisplayText(text: String) {
        prefs.edit().putString(KEY_EASYSHOP_TEXT, text).apply()
    }

    fun getEasyShopDisplayText(): String = prefs.getString(KEY_EASYSHOP_TEXT, "데이터 없음") ?: "데이터 없음"

    fun saveEasyShopAmount(amount: Int) {
        prefs.edit().putInt(KEY_EASYSHOP_AMOUNT, amount).apply()
    }

    fun getEasyShopAmount(): Int = prefs.getInt(KEY_EASYSHOP_AMOUNT, 0)

    fun saveEasyShopDepositAmount(amount: Int) {
        prefs.edit().putInt(KEY_EASYSHOP_DEPOSIT_AMOUNT, amount).apply()
    }

    fun getEasyShopDepositAmount(): Int = prefs.getInt(KEY_EASYSHOP_DEPOSIT_AMOUNT, 0)

    fun saveEasyShopUpdatedAt(text: String) {
        prefs.edit().putString(KEY_EASYSHOP_UPDATED_AT, text).apply()
    }

    fun getEasyShopUpdatedAt(): String = prefs.getString(KEY_EASYSHOP_UPDATED_AT, "--:--:--") ?: "--:--:--"

    fun saveEasyShopRefreshing(value: Boolean) {
        prefs.edit().putBoolean(KEY_EASYSHOP_REFRESHING, value).apply()
    }

    fun isEasyShopRefreshing(): Boolean = prefs.getBoolean(KEY_EASYSHOP_REFRESHING, false)

    fun saveEasyShopHasCanceledToday(value: Boolean) {
        prefs.edit().putBoolean(KEY_EASYSHOP_HAS_CANCELED_TODAY, value).apply()
    }

    fun hasEasyShopCanceledToday(): Boolean = prefs.getBoolean(KEY_EASYSHOP_HAS_CANCELED_TODAY, false)

    fun saveEasyShopCanceledTodayCount(count: Int) {
        prefs.edit().putInt(KEY_EASYSHOP_CANCELED_TODAY_COUNT, count.coerceAtLeast(0)).apply()
    }

    fun getEasyShopCanceledTodayCount(): Int = prefs.getInt(KEY_EASYSHOP_CANCELED_TODAY_COUNT, 0)

    companion object {
        private const val KEY_TEXT = "display_text"
        private const val KEY_AMOUNT = "amount_value"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_REFRESHING = "refreshing"
        private const val KEY_EASYSHOP_TEXT = "easyshop_display_text"
        private const val KEY_EASYSHOP_AMOUNT = "easyshop_amount_value"
        private const val KEY_EASYSHOP_DEPOSIT_AMOUNT = "easyshop_deposit_amount_value"
        private const val KEY_EASYSHOP_UPDATED_AT = "easyshop_updated_at"
        private const val KEY_EASYSHOP_REFRESHING = "easyshop_refreshing"
        private const val KEY_EASYSHOP_HAS_CANCELED_TODAY = "easyshop_has_canceled_today"
        private const val KEY_EASYSHOP_CANCELED_TODAY_COUNT = "easyshop_canceled_today_count"
    }
}
