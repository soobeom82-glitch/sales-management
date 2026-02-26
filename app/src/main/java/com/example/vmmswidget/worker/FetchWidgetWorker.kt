package com.example.vmmswidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.net.HttpClient
import com.example.vmmswidget.net.LoginDetector
import com.example.vmmswidget.net.TransactionsRepository
import com.example.vmmswidget.net.VmmsConfig
import com.example.vmmswidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.vmmswidget.widget.WorkScheduler
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.db.SalesEntity

class FetchWidgetWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("Vmms", "Worker start")
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        if (!forceRefresh && isQuietHours()) {
            Log.i("Vmms", "Quiet hours: skipping auto refresh")
            return@withContext Result.success()
        }
        val auth = AuthStore(appContext)
        val id = auth.getId()
        val password = auth.getPassword()

        if (id.isNullOrBlank() || password.isNullOrBlank()) {
            saveAndUpdate("로그인 정보가 없습니다. 앱에서 입력하세요.", 0)
            return@withContext Result.success()
        }

        val http = HttpClient(appContext)
        val client = http.client
        val targetUrl = VmmsConfig.BASE_URL + VmmsConfig.TARGET_PATH

        val initialHtml = fetchHtml(client, targetUrl)
        val needsLogin = initialHtml != null && LoginDetector.isLoginPage(initialHtml)
        Log.i("Vmms", "Initial fetch: ${if (initialHtml == null) "null" else "len=" + initialHtml.length}, needsLogin=$needsLogin")

        val html = if (needsLogin) {
            val loginOk = performLogin(client, id, password)
            if (!loginOk) {
                saveAndUpdate("로그인 실패: 계정 확인 필요", 0)
                Log.w("Vmms", "Login failed")
                return@withContext Result.success()
            }
            val cookies = http.cookiesFor(targetUrl.toHttpUrl())
            val cookieInfo = cookies.joinToString { "${it.name}(${it.path})" }
            Log.i("Vmms", "Cookies for index: $cookieInfo")
            fetchHtml(client, targetUrl)
        } else {
            initialHtml
        }

        if (html.isNullOrBlank()) {
            saveAndUpdate("데이터 불러오기 실패", 0)
            Log.w("Vmms", "Target fetch failed")
            return@withContext Result.success()
        }

        val sales = fetchTodaySalesExcludingCanceled()
        val display = if (sales != null) {
            "매출 ${sales.amountLabel}"
        } else {
            logHtmlSnippet(html)
            parseDisplay(html)
        }
        Log.i("Vmms", "Parsed display: $display")
        val amountValue = sales?.amountValue ?: 0
        val db = AppDatabase.get(appContext)
        if (sales != null) {
            val today = LocalDate.now()
            db.salesDao().upsert(
                SalesEntity(
                    date = today.toString(),
                    amount = amountValue,
                    createdAt = System.currentTimeMillis()
                )
            )
            // 1년(오늘 포함)만 유지
            db.salesDao().deleteOlderThan(today.minusDays(365).toString())
        }
        logLast7(db)
        logMissingDays(db)
        saveAndUpdate(display, amountValue)
        WorkScheduler.scheduleDailyRecord(appContext)
        return@withContext Result.success()
    }

    private fun fetchHtml(client: okhttp3.OkHttpClient, url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", VmmsConfig.BASE_URL + "/")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            Log.i("Vmms", "GET $url -> ${resp.code}")
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private suspend fun fetchTodaySalesExcludingCanceled(): TodaySales? {
        return try {
            val total = TransactionsRepository(appContext).fetchTodayPieData().totalAmount
            TodaySales(
                amountLabel = "${String.format("%,d", total)}원",
                amountValue = total
            )
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to fetch today sales excluding canceled", e)
            null
        }
    }

    private fun performLogin(client: okhttp3.OkHttpClient, id: String, password: String): Boolean {
        val loginUrl = VmmsConfig.BASE_URL + VmmsConfig.LOGIN_PATH
        // Login endpoint expects query parameters: /user/login?id=...&pass=...
        val actionUrl = (VmmsConfig.BASE_URL + "/user/login")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter(VmmsConfig.LOGIN_ID_FIELD, id)
            .addQueryParameter(VmmsConfig.LOGIN_PASSWORD_FIELD, password)
            .build()

        val request = Request.Builder()
            .url(actionUrl)
            .header("Referer", loginUrl)
            .header("Origin", VmmsConfig.BASE_URL)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            Log.i("Vmms", "GET $actionUrl -> ${resp.code}")
            val setCookies = resp.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                val parsed = setCookies.mapNotNull { okhttp3.Cookie.parse(actionUrl, it) }
                val info = parsed.joinToString { "${it.name}(${it.path})" }
                Log.i("Vmms", "Set-Cookie: $info")
            }
            if (!resp.isSuccessful) return false
            val html = resp.body?.string() ?: return false
            val stillLogin = LoginDetector.isLoginPage(html)
            Log.i("Vmms", "Login page after POST? $stillLogin")
            return !stillLogin
        }
    }

    private fun parseDisplay(html: String): String {
        val doc = Jsoup.parse(html)
        val hasCnt = html.contains("total_cnt1")
        val hasAmount = html.contains("total_amount1")
        Log.i("Vmms", "Contains total_cnt1? $hasCnt, total_amount1? $hasAmount")
        val cnt = doc.selectFirst("#total_cnt1")?.text()?.trim().orEmpty()
        val amount = doc.selectFirst("#total_amount1")?.text()?.trim().orEmpty()
        val cntFallback = if (cnt.isBlank()) extractById(html, "total_cnt1") else ""
        val amountFallback = if (amount.isBlank()) extractById(html, "total_amount1") else ""
        if (cnt.isBlank() || amount.isBlank()) {
            Log.i("Vmms", "Fallback cnt='$cntFallback', amount='$amountFallback'")
            logHtmlAroundId(html, "total_cnt1")
            logHtmlAroundId(html, "total_amount1")
        }
        val finalCnt = cnt.ifBlank { cntFallback }
        val finalAmount = amount.ifBlank { amountFallback }

        return when {
            finalAmount.isNotBlank() -> "매출 $finalAmount"
            finalCnt.isNotBlank() -> "매출 $finalCnt"
            else -> "매출 데이터 없음"
        }
    }

    private fun extractById(html: String, id: String): String {
        val regex = Regex("id\\s*=\\s*\"${id}\"[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
        val match = regex.find(html) ?: return ""
        return match.groupValues.getOrNull(1)?.trim().orEmpty()
    }

    private fun logHtmlAroundId(html: String, id: String) {
        val idx = html.indexOf(id)
        if (idx < 0) return
        val start = (idx - 120).coerceAtLeast(0)
        val end = (idx + 200).coerceAtMost(html.length)
        val snippet = html.substring(start, end)
            .replace(Regex("\\d"), "*")
            .replace(Regex("\\s+"), " ")
        Log.i("Vmms", "Around $id: $snippet")
    }

    private fun logHtmlSnippet(html: String) {
        val snippet = html
            .take(800)
            .replace(Regex("\\d"), "*")
            .replace(Regex("\\s+"), " ")
        Log.i("Vmms", "HTML snippet: $snippet")
    }

    private fun isQuietHours(): Boolean {
        val now = LocalTime.now()
        val start = LocalTime.of(0, 0)
        val end = LocalTime.of(6, 0)
        return !now.isBefore(start) && now.isBefore(end)
    }

    private fun saveAndUpdate(text: String, amountValue: Int) {
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        WidgetDataStore(appContext).saveDisplayText(text)
        WidgetDataStore(appContext).saveAmount(amountValue)
        WidgetDataStore(appContext).saveUpdatedAt(now)
        WidgetDataStore(appContext).saveRefreshing(false)
        WidgetUpdater.updateAll(appContext)
    }

    private suspend fun logLast7(db: AppDatabase) {
        val today = LocalDate.now().toString()
        val last7 = db.salesDao().getLastDaysExcludingToday(today, 7).asReversed()
        val text = last7.joinToString(" | ") { "${it.date}:${it.amount}" }
        Log.i("Vmms", "DB last7: $text")
    }

    private suspend fun logMissingDays(db: AppDatabase) {
        val today = LocalDate.now()
        val from = today.minusDays(15)
        val all = db.salesDao().getAll()
        val existing = all.map { it.date }.toSet()
        val missing = mutableListOf<String>()
        var d = from
        while (d.isBefore(today)) {
            if (!existing.contains(d.toString())) {
                missing.add(d.toString())
            }
            d = d.plusDays(1)
        }
        Log.i("Vmms", "DB missing last15 (exclude today): ${missing.joinToString(",")}")
    }

    companion object {
        const val KEY_FORCE_REFRESH = "force_refresh"
    }
}

private data class TodaySales(
    val amountLabel: String,
    val amountValue: Int
)
