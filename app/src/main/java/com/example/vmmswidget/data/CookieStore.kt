package com.example.vmmswidget.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.HttpUrl

class CookieStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cookie_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCookies(url: HttpUrl, cookies: List<Cookie>) {
        val key = keyFor(url)
        val existing = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        val incomingByIdentity = cookies.associateBy { "${it.name}|${it.domain}|${it.path}" }
        val filteredExisting = existing.filterNot { encoded ->
            val parsed = Cookie.fromPersistedString(url, encoded) ?: return@filterNot false
            val id = "${parsed.name}|${parsed.domain}|${parsed.path}"
            incomingByIdentity.containsKey(id)
        }.toMutableSet()
        filteredExisting.addAll(cookies.map { it.toPersistedString() })
        prefs.edit().putStringSet(key, filteredExisting).apply()
    }

    fun loadCookies(url: HttpUrl): List<Cookie> {
        val key = keyFor(url)
        val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return set.mapNotNull { Cookie.fromPersistedString(url, it) }
            .filterNot { it.hasExpired() }
    }

    fun clear(url: HttpUrl) {
        prefs.edit().remove(keyFor(url)).apply()
    }

    fun upsertCookie(url: HttpUrl, cookie: Cookie) {
        val key = keyFor(url)
        val existing = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        existing.removeAll { encoded ->
            val parsed = Cookie.fromPersistedString(url, encoded) ?: return@removeAll false
            parsed.name == cookie.name && parsed.domain == cookie.domain && parsed.path == cookie.path
        }
        existing.add(cookie.toPersistedString())
        prefs.edit().putStringSet(key, existing).apply()
    }

    private fun keyFor(url: HttpUrl): String = "cookies_${url.host}"
}

private fun Cookie.toPersistedString(): String {
    return listOf(
        name,
        value,
        domain,
        path,
        expiresAt.toString(),
        secure.toString(),
        httpOnly.toString(),
        hostOnly.toString()
    ).joinToString("\t")
}

private fun Cookie.hasExpired(): Boolean = expiresAt < System.currentTimeMillis()

private fun Cookie.Companion.fromPersistedString(url: HttpUrl, value: String): Cookie? {
    val parts = value.split("\t")
    if (parts.size != 8) return null
    val name = parts[0]
    val v = parts[1]
    val domain = parts[2]
    val path = parts[3]
    val expiresAt = parts[4].toLongOrNull() ?: return null
    val secure = parts[5].toBooleanStrictOrNull() ?: false
    val httpOnly = parts[6].toBooleanStrictOrNull() ?: false
    val hostOnly = parts[7].toBooleanStrictOrNull() ?: false

    val builder = Cookie.Builder()
        .name(name)
        .value(v)
        .path(path)
        .expiresAt(expiresAt)

    if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
    if (secure) builder.secure()
    if (httpOnly) builder.httpOnly()

    return try {
        builder.build()
    } catch (e: IllegalArgumentException) {
        null
    }
}
