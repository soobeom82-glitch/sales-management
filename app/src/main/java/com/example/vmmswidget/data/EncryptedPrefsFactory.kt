package com.example.vmmswidget.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

internal object EncryptedPrefsFactory {
    fun create(context: Context, name: String): SharedPreferences {
        return tryCreate(context, name, clearOnFailure = true)
    }

    private fun tryCreate(
        context: Context,
        name: String,
        clearOnFailure: Boolean
    ): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                name,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (!clearOnFailure) throw e
            Log.w("Vmms", "EncryptedSharedPreferences create failed: $name. clearing and recreating.", e)
            clearSharedPreferencesFile(context, name)
            tryCreate(context, name, clearOnFailure = false)
        }
    }

    private fun clearSharedPreferencesFile(context: Context, name: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
                if (file.exists()) {
                    file.delete()
                }
            }
            Unit
        }.onFailure {
            Log.w("Vmms", "Failed to clear shared preferences: $name", it)
        }
    }
}
