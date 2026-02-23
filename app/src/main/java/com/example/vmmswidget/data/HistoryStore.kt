package com.example.vmmswidget.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class DailySales(val date: LocalDate, val amount: Int)

class HistoryStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "history_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun upsert(date: LocalDate, amount: Int) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.date == date }
        if (idx >= 0) {
            list[idx] = DailySales(date, amount)
        } else {
            list.add(DailySales(date, amount))
        }
        saveAll(list)
    }

    fun getLastDays(days: Int): List<DailySales> {
        return getAll()
            .sortedBy { it.date }
            .takeLast(days)
    }

    fun isEmpty(): Boolean = getAll().isEmpty()

    fun seedIfEmpty(values: List<DailySales>) {
        if (!isEmpty()) return
        saveAll(values)
    }

    private fun getAll(): List<DailySales> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<DailySales>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val dateStr = obj.optString("date", "")
            val amount = obj.optInt("amount", 0)
            if (dateStr.isNotBlank()) {
                out.add(DailySales(LocalDate.parse(dateStr), amount))
            }
        }
        return out
    }

    private fun saveAll(values: List<DailySales>) {
        val arr = JSONArray()
        values.sortedBy { it.date }.forEach {
            arr.put(JSONObject().apply {
                put("date", it.date.toString())
                put("amount", it.amount)
            })
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history"
    }
}
