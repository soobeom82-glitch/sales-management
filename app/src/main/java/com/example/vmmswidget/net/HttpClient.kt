package com.example.vmmswidget.net

import android.content.Context
import com.example.vmmswidget.data.CookieStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class HttpClient(context: Context) {
    private val cookieStore = CookieStore(context)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore.saveCookies(url, cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore.loadCookies(url)
            }
        })
        .build()

    fun cookiesFor(url: HttpUrl): List<Cookie> = cookieStore.loadCookies(url)

    fun upsertCookie(url: HttpUrl, cookie: Cookie) {
        cookieStore.upsertCookie(url, cookie)
    }
}
