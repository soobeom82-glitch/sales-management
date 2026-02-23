package com.example.vmmswidget.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(id: String, password: String) {
        prefs.edit()
            .putString(KEY_ID, id)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getId(): String? = prefs.getString(KEY_ID, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_PASSWORD = "password"
    }
}
