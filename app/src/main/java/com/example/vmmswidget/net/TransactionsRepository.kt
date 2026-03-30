package com.example.vmmswidget.net

import android.content.Context
import android.util.Log
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.db.ProductMappingEntity
import com.example.vmmswidget.ui.TransactionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TransactionsRepository(private val context: Context) {

    data class PageResult(
        val rows: List<TransactionRow>,
        val totalPages: Int
    )

    data class CancelResult(
        val code: String,
        val message: String,
        val description: String
    ) {
        val isSuccess: Boolean get() = code == "0000"
    }

    data class ProductMappingCandidate(
        val colNo: String,
        val product: String
    )

    data class TodayItemShare(
        val label: String,
        val amount: Int
    )

    data class TodayPieData(
        val totalAmount: Int,
        val shares: List<TodayItemShare>,
        val latestTransactionTime: String? = null
    )

    data class DailyTotal(
        val date: String,
        val amount: Int
    )

    suspend fun fetchRecentTransactions(): List<TransactionRow> = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext emptyList()

        val http = HttpClient(context)
        val client = http.client

        val end = LocalDate.now()
        val start = end.minusDays(2)
        val body = fetchList(client, http, id, password, start, end, 1) ?: return@withContext emptyList()
        val parsed = parseRows(body)
        return@withContext applyProductMappings(parsed.rows)
    }

    suspend fun fetchTransactions(start: LocalDate, end: LocalDate, pageNo: Int): PageResult = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext PageResult(emptyList(), 0)

        val http = HttpClient(context)
        val client = http.client

        val body = fetchList(client, http, id, password, start, end, pageNo) ?: return@withContext PageResult(emptyList(), 0)
        val parsed = parseRows(body)
        return@withContext PageResult(
            rows = applyProductMappings(parsed.rows),
            totalPages = parsed.totalPages
        )
    }

    suspend fun fetchDetail(transactionNo: String, terminalId: String): TransactionDetail? = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext null

        val http = HttpClient(context)
        val client = http.client
        val url = buildDetailUrl(transactionNo, terminalId)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", VmmsConfig.BASE_URL + "/sales/SalesRealTime")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            Log.i("Vmms", "GET $url -> ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext null
            if (body.trim().startsWith("<") || LoginDetector.isLoginPage(body)) {
                preflightLoginPage(client)
                val loginOk = performLogin(client, id, password)
                if (!loginOk) return@withContext null
                return@withContext fetchDetail(transactionNo, terminalId)
            }
            val detail = parseDetail(body) ?: return@withContext null
            val mapped = findMappedProduct(detail.colNo)
            if (mapped.isNullOrBlank()) detail else detail.copy(product = mapped)
        }
    }

    suspend fun getProductMappings(): List<ProductMappingEntity> = withContext(Dispatchers.IO) {
        AppDatabase.get(context).productMappingDao().getAll()
    }

    suspend fun saveProductMappings(items: List<ProductMappingEntity>) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).productMappingDao()
        for (item in items) {
            if (item.actualProduct.isBlank()) {
                dao.deleteByColNo(item.colNo)
            } else {
                dao.upsert(
                    item.copy(
                        actualProduct = item.actualProduct.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun fetchProductMappingCandidates(): List<ProductMappingCandidate> = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext emptyList()

        val http = HttpClient(context)
        val body = fetchList(
            client = http.client,
            http = http,
            id = id,
            password = password,
            start = LocalDate.now().minusDays(6),
            end = LocalDate.now(),
            pageNo = 1
        ) ?: return@withContext emptyList()

        return@withContext try {
            val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            val seen = LinkedHashSet<String>()
            val out = ArrayList<ProductMappingCandidate>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val colNo = obj.optString("col_no", "").trim()
                if (colNo.isBlank()) continue
                if (!seen.add(colNo)) continue
                val product = obj.optString("product", "-").trim()
                out.add(ProductMappingCandidate(colNo = colNo, product = product))
            }
            out
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to parse product mapping candidates", e)
            emptyList()
        }
    }

    suspend fun fetchTodayPieData(): TodayPieData {
        return fetchPieDataForDate(LocalDate.now())
    }

    suspend fun fetchPieDataForDate(targetDate: LocalDate): TodayPieData = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext TodayPieData(
            totalAmount = 0,
            shares = emptyList(),
            latestTransactionTime = null
        )

        val http = HttpClient(context)
        val client = http.client

        var pageNo = 1
        var totalPages = 1
        val amountByItem = LinkedHashMap<String, Int>()
        var latestRawDateTime: String? = null
        val mapByCol = AppDatabase.get(context).productMappingDao()
            .getAll()
            .associateBy { it.colNo }

        while (pageNo <= totalPages) {
            val body = fetchList(client, http, id, password, targetDate, targetDate, pageNo)
                ?: return@withContext TodayPieData(
                    totalAmount = 0,
                    shares = emptyList(),
                    latestTransactionTime = null
                )
            try {
                val json = JSONObject(body)
                totalPages = (json.optInt("totalPages", 1)).coerceAtLeast(1)
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val payStep = obj.optString("pay_step", "")
                    if (payStep.contains("취소")) continue
                    val txDateTime = obj.optString("transaction_date", "").trim()
                    if (txDateTime.length >= 19) {
                        val prev = latestRawDateTime
                        latestRawDateTime = if (prev == null || txDateTime > prev) txDateTime else prev
                    }
                    val colNo = obj.optString("col_no", "")
                    val rawProduct = obj.optString("product", "-")
                    val mapped = mapByCol[colNo]?.actualProduct?.takeIf { it.isNotBlank() } ?: rawProduct
                    val label = mapped.ifBlank { "-" }
                    val amount = obj.optString("amount", "0").filter { it.isDigit() }.toIntOrNull() ?: 0
                    amountByItem[label] = (amountByItem[label] ?: 0) + amount
                }
            } catch (e: Exception) {
                Log.w("Vmms", "Failed to parse today pie data", e)
                return@withContext TodayPieData(
                    totalAmount = 0,
                    shares = emptyList(),
                    latestTransactionTime = null
                )
            }
            pageNo += 1
        }

        val sorted = amountByItem.entries
            .sortedByDescending { it.value }
            .map { TodayItemShare(it.key, it.value) }
        val total = sorted.sumOf { it.amount }
        val latestTime = latestRawDateTime?.takeIf { it.length >= 19 }?.substring(11, 19)
        TodayPieData(totalAmount = total, shares = sorted, latestTransactionTime = latestTime)
    }

    suspend fun fetchLastDaysTotalsExcludingCanceled(
        days: Int,
        excludeToday: Boolean = true
    ): List<DailyTotal> = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext emptyList()
        if (days <= 0) return@withContext emptyList()

        val http = HttpClient(context)
        val client = http.client
        val end = if (excludeToday) LocalDate.now().minusDays(1) else LocalDate.now()
        val start = end.minusDays(days.toLong() - 1L)
        if (end.isBefore(start)) return@withContext emptyList()

        val result = mutableListOf<DailyTotal>()
        var date = start
        while (!date.isAfter(end)) {
            var pageNo = 1
            var totalPages = 1
            var sum = 0
            while (pageNo <= totalPages) {
                val body = fetchList(client, http, id, password, date, date, pageNo)
                    ?: return@withContext emptyList()
                try {
                    val json = JSONObject(body)
                    totalPages = (json.optInt("totalPages", 1)).coerceAtLeast(1)
                    val arr = json.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val payStep = obj.optString("pay_step", "")
                        if (payStep.contains("취소")) continue
                        val amount = obj.optString("amount", "0")
                            .filter { it.isDigit() }
                            .toIntOrNull() ?: 0
                        sum += amount
                    }
                } catch (e: Exception) {
                    Log.w("Vmms", "Failed to parse daily totals", e)
                    return@withContext emptyList()
                }
                pageNo += 1
            }
            result.add(DailyTotal(date = date.toString(), amount = sum))
            date = date.plusDays(1)
        }
        result
    }

    suspend fun fetchDailyTotalExcludingCanceled(date: LocalDate): DailyTotal? = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val id = auth.getId()
        val password = auth.getPassword()
        if (id.isNullOrBlank() || password.isNullOrBlank()) return@withContext null

        val http = HttpClient(context)
        val client = http.client
        var pageNo = 1
        var totalPages = 1
        var sum = 0

        while (pageNo <= totalPages) {
            val body = fetchList(client, http, id, password, date, date, pageNo) ?: return@withContext null
            try {
                val json = JSONObject(body)
                totalPages = (json.optInt("totalPages", 1)).coerceAtLeast(1)
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val payStep = obj.optString("pay_step", "")
                    if (payStep.contains("취소")) continue
                    val amount = obj.optString("amount", "0")
                        .filter { it.isDigit() }
                        .toIntOrNull() ?: 0
                    sum += amount
                }
            } catch (e: Exception) {
                Log.w("Vmms", "Failed to parse daily total for $date", e)
                return@withContext null
            }
            pageNo += 1
        }

        DailyTotal(date = date.toString(), amount = sum)
    }

    suspend fun requestCancelInit(): String? = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val certUser = auth.getCancelCertUser().orEmpty().trim()
        val certPhone = auth.getCancelCertPhone().orEmpty().trim()
        val certEmail = auth.getCancelCertEmail().orEmpty().trim()
        if (certUser.isBlank() || certPhone.isBlank() || certEmail.isBlank()) {
            Log.w("Vmms", "requestCancelInit skipped: cancel cert profile is empty")
            return@withContext null
        }
        val url = CERT_INIT_URL
        val payload = JSONObject().apply {
            put("service", "CERT")
            put("user", certUser)
            put("phone", certPhone)
            put("email", certEmail)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return@withContext try {
            val body = HttpClient(context).client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Log.i("Vmms", "POST $url -> ${resp.code}, body=${text.take(300)}")
                if (!resp.isSuccessful) return@use null
                text
            } ?: return@withContext null
            val json = JSONObject(body)
            val code = json.optString("code", "")
            if (code == "0000") {
                val param = json.optString("param", "")
                if (param.isBlank()) null else param
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to request cancel init", e)
            null
        }
    }

    suspend fun submitCancelCertCode(code: String, param: String?): Boolean = withContext(Dispatchers.IO) {
        if (code.length != 6 || !code.all { it.isDigit() }) return@withContext false
        val auth = AuthStore(context)
        val certUser = auth.getCancelCertUser().orEmpty().trim()
        val certPhone = auth.getCancelCertPhone().orEmpty().trim()
        val certEmail = auth.getCancelCertEmail().orEmpty().trim()
        if (certUser.isBlank() || certPhone.isBlank() || certEmail.isBlank()) {
            Log.w("Vmms", "submitCancelCertCode skipped: cancel cert profile is empty")
            return@withContext false
        }

        val http = HttpClient(context)
        val client = http.client
        val payload = JSONObject().apply {
            put("service", "CERT")
            put("user", certUser)
            put("phone", certPhone)
            put("email", certEmail)
            put("param", param ?: "")
            put("code", code)
            put("certNo", code)
            put("cert", code)
            put("otp", code)
        }.toString()

        val request = Request.Builder()
            .url(CERT_COMPARE_URL)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        try {
            val isSuccess = client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.i("Vmms", "POST $CERT_COMPARE_URL -> ${resp.code}, body=${body.take(300)}")
                resp.isSuccessful && isCertSuccess(body)
            }
            if (isSuccess) {
                applyCertCookie(http)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to submit cert code to $CERT_COMPARE_URL", e)
        }
        return@withContext false
    }

    suspend fun cancelTransaction(
        transactionNo: String,
        terminalId: String,
        userId: String,
        cancelAmount: String
    ): CancelResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CANCEL_V2_URL)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .post(
                JSONObject().apply {
                    put("transactionNo", transactionNo)
                    put("terminalId", terminalId)
                    put("userId", userId)
                    put("cancelAmount", cancelAmount)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()
        return@withContext try {
            HttpClient(context).client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.i("Vmms", "POST $CANCEL_V2_URL -> ${resp.code}, body=${body.take(500)}")
                if (!resp.isSuccessful) {
                    return@use CancelResult(
                        code = resp.code.toString(),
                        message = "취소 요청 실패",
                        description = body.take(200)
                    )
                }
                val json = JSONObject(body)
                CancelResult(
                    code = json.optString("code", ""),
                    message = json.optString("message", "응답 메시지 없음"),
                    description = json.optString("description", "")
                )
            }
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to cancel transaction", e)
            CancelResult(code = "EX", message = "네트워크 오류", description = e.message.orEmpty())
        }
    }

    private fun isCertSuccess(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            val code = json.optString("code", "")
            code == "0000"
        } catch (_: Exception) {
            false
        }
    }

    private fun applyCertCookie(http: HttpClient) {
        val vmmsUrl = VmmsConfig.BASE_URL.toHttpUrl()
        val certApiUrl = CERT_INIT_URL.toHttpUrl()
        val expiresAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        val certForVmms = Cookie.Builder()
            .name("cert")
            .value("Y")
            .domain(vmmsUrl.host)
            .path("/")
            .expiresAt(expiresAt)
            .httpOnly()
            .secure()
            .build()
        val certForApi = Cookie.Builder()
            .name("cert")
            .value("Y")
            .domain(certApiUrl.host)
            .path("/")
            .expiresAt(expiresAt)
            .httpOnly()
            .secure()
            .build()
        http.upsertCookie(vmmsUrl, certForVmms)
        http.upsertCookie(certApiUrl, certForApi)
        Log.i("Vmms", "cert cookie updated for ${vmmsUrl.host}, ${certApiUrl.host}")
    }

    fun hasCertCookie(): Boolean {
        val http = HttpClient(context)
        val vmmsUrl = VmmsConfig.BASE_URL.toHttpUrl()
        return http.cookiesFor(vmmsUrl).any { it.name == "cert" && it.value == "Y" && it.expiresAt > System.currentTimeMillis() }
    }

    private fun fetchList(
        client: okhttp3.OkHttpClient,
        http: HttpClient,
        id: String,
        password: String,
        start: LocalDate,
        end: LocalDate,
        pageNo: Int
    ): String? {
        val url = buildListUrl(start, end, pageNo)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", VmmsConfig.BASE_URL + "/sales/SalesRealTime")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            Log.i("Vmms", "GET $url -> ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            if (body.trim().startsWith("<") || LoginDetector.isLoginPage(body)) {
                preflightLoginPage(client)
                val loginOk = performLogin(client, id, password)
                if (!loginOk) return null
                return fetchList(client, http, id, password, start, end, pageNo)
            }
            return body
        }
    }

    private fun buildListUrl(start: LocalDate, end: LocalDate, pageNo: Int): String {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return (VmmsConfig.BASE_URL + "/sales/RealTime/list.do").toHttpUrl().newBuilder()
            .addQueryParameter("searchType", "01")
            .addQueryParameter("startdate", start.format(fmt))
            .addQueryParameter("enddate", end.format(fmt))
            .addQueryParameter("searchCompany", "806")
            .addQueryParameter("searchPlace", "0")
            .addQueryParameter("searchOrgan", "207368")
            .addQueryParameter("searchPayType", "01,02,07,10,11,80")
            .addQueryParameter("searchPayStep", "01,02,03,06,81,A1,21,22,28,29,99")
            .addQueryParameter("searchField", "")
            .addQueryParameter("searchValue", "")
            .addQueryParameter("type", "A")
            .addQueryParameter("pageType", "realTime")
            .addQueryParameter("pageNo", pageNo.toString())
            .addQueryParameter("pageSize", "30")
            .build()
            .toString()
    }

    private fun buildDetailUrl(transactionNo: String, terminalId: String): String {
        return (VmmsConfig.BASE_URL + "/sales/RealTime/detail").toHttpUrl().newBuilder()
            .addQueryParameter("searchCompany", "806")
            .addQueryParameter("transaction_no", transactionNo)
            .addQueryParameter("terminal_id", terminalId)
            .build()
            .toString()
    }

    private fun parseRows(body: String): PageResult {
        return try {
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: return PageResult(emptyList(), 0)
            val list = ArrayList<TransactionRow>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("transaction_no", "")
                val terminalId = obj.optString("terminal_id", "")
                val colNo = obj.optString("col_no", "")
                val timeRaw = obj.optString("transaction_date", "-")
                val time = formatTime(timeRaw)
                val baseProduct = obj.optString("product", "-")
                val amountRaw = obj.optString("amount", "0").filter { it.isDigit() }
                val amountVal = amountRaw.toIntOrNull() ?: 0
                val amount = String.format("%,d원", amountVal)
                val cardNo = maskCard(obj.optString("card_no", "-"))
                val payStep = obj.optString("pay_step", "")
                val isCanceled = payStep.contains("취소")
                list.add(
                    TransactionRow(
                        id = id,
                        terminalId = terminalId,
                        colNo = colNo,
                        time = time,
                        rawProduct = baseProduct,
                        item = formatItemLabel(colNo, baseProduct),
                        amount = amount,
                        cardNo = cardNo,
                        isCanceled = isCanceled
                    )
                )
            }
            val totalPages = json.optInt("totalPages", 1)
            PageResult(list, if (totalPages <= 0) 1 else totalPages)
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to parse transactions", e)
            PageResult(emptyList(), 0)
        }
    }

    private fun parseDetail(body: String): TransactionDetail? {
        return try {
            val json = JSONObject(body)
            val arr: JSONArray = json.optJSONArray("data") ?: return null
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            val amountRaw = obj.optString("amount", "0").filter { it.isDigit() }
            val amountVal = amountRaw.toIntOrNull() ?: 0
            TransactionDetail(
                transactionDate = obj.optString("transaction_date", "-"),
                product = obj.optString("product", "-"),
                amount = String.format("%,d원", amountVal),
                payType = obj.optString("pay_type", "-"),
                payStep = obj.optString("pay_step", "-"),
                cardNo = maskCard(obj.optString("card_no", "-")),
                approvalNo = obj.optString("approval_no", "-"),
                terminalId = obj.optString("terminal_id", "-"),
                colNo = obj.optString("col_no", "-"),
                company = obj.optString("company", "-"),
                organ = obj.optString("organ", "-"),
                place = obj.optString("place", "-")
            )
        } catch (e: Exception) {
            Log.w("Vmms", "Failed to parse detail", e)
            null
        }
    }

    private suspend fun applyProductMappings(rows: List<TransactionRow>): List<TransactionRow> {
        if (rows.isEmpty()) return rows
        val colNos = rows.map { it.colNo }.filter { it.isNotBlank() }.distinct()
        if (colNos.isEmpty()) return rows
        val mappingByCol = AppDatabase.get(context).productMappingDao()
            .getByColNos(colNos)
            .associateBy { it.colNo }
        return rows.map { row ->
            val mapped = mappingByCol[row.colNo]?.actualProduct
            if (mapped.isNullOrBlank()) row else row.copy(item = formatItemLabel(row.colNo, mapped))
        }
    }

    private suspend fun findMappedProduct(colNo: String): String? {
        if (colNo.isBlank()) return null
        return AppDatabase.get(context).productMappingDao()
            .getByColNo(colNo)
            ?.actualProduct
            ?.takeIf { it.isNotBlank() }
    }

    private fun formatItemLabel(colNo: String, product: String): String {
        val name = product.ifBlank { "-" }
        return if (colNo.isBlank()) {
            name
        } else {
            "[$colNo] $name"
        }
    }

    private fun formatTime(raw: String): String {
        // "yyyy-MM-dd HH:mm:ss" -> "M/d HH:mm"
        return if (raw.length >= 16) {
            val month = raw.substring(5, 7).toIntOrNull()
            val day = raw.substring(8, 10).toIntOrNull()
            val hhmm = raw.substring(11, 16)
            if (month != null && day != null) {
                "$month/$day $hhmm"
            } else {
                raw.substring(5, 10) + " " + hhmm
            }
        } else {
            raw
        }
    }

    private fun maskCard(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 4) return raw
        val last4 = digits.takeLast(4)
        return last4
    }

    private fun performLogin(client: okhttp3.OkHttpClient, id: String, password: String): Boolean {
        val loginUrl = VmmsConfig.BASE_URL + VmmsConfig.LOGIN_PATH
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
            if (!resp.isSuccessful) return false
            val html = resp.body?.string() ?: return false
            val stillLogin = LoginDetector.isLoginPage(html)
            Log.i("Vmms", "Login page after POST? $stillLogin")
            return !stillLogin
        }
    }

    private fun preflightLoginPage(client: okhttp3.OkHttpClient) {
        val url = VmmsConfig.BASE_URL + VmmsConfig.LOGIN_PATH
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) VMMSWidget/1.0")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            Log.i("Vmms", "GET $url -> ${resp.code}")
        }
    }

    companion object {
        private const val CERT_INIT_URL = "https://devapi.ubcn.co.kr:17881/cert/init"
        private const val CERT_COMPARE_URL = "https://devapi.ubcn.co.kr:17881/cert/compare"
        private const val CANCEL_V2_URL = "https://devapi.ubcn.co.kr:17881/cancel/v2"
    }
}
