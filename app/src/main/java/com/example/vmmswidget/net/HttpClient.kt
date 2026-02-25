package com.example.vmmswidget.net

import android.util.Log
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
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.contains("smarteasyshop.kicc.co.kr")) {
                Log.i(
                    "Vmms",
                    "EasyShop HTTP ${request.method} ${request.url} Cookie=${request.header("Cookie").orEmpty()}"
                )
            }
            chain.proceed(request)
        }
        .build()

    fun cookiesFor(url: HttpUrl): List<Cookie> = cookieStore.loadCookies(url)

    fun upsertCookie(url: HttpUrl, cookie: Cookie) {
        cookieStore.upsertCookie(url, cookie)
    }

    fun clearCookies(url: HttpUrl) {
        cookieStore.clear(url)
    }
}
