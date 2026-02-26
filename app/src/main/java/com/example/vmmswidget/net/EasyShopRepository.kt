package com.example.vmmswidget.net

import android.content.Context
import android.util.Log
import com.example.vmmswidget.data.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.parser.Parser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class EasyShopRepository(private val context: Context) {
    private val webUserAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

    data class LoginResult(
        val success: Boolean,
        val message: String
    )

    data class SalesRecord(
        val seqNo: Int,
        val transactionNo: String,
        val terminalNo: String,
        val status: String,
        val transactionDate: String,
        val transactionTime: String,
        val cardNo: String,
        val cardType: String,
        val issuerName: String,
        val purchaseName: String,
        val approvalNo: String,
        val amount: Int,
        val responseText: String,
        val isCanceled: Boolean
    )

    data class SalesFetchResult(
        val success: Boolean,
        val message: String,
        val records: List<SalesRecord>,
        val totalAmount: Int
    )

    private data class SessionParams(
        val clientTimeOffset: String,
        val remainTime: String,
        val latestTouch: String,
        val sessionExpiry: String
    )

    private data class AuthContext(
        val autId: String,
        val bizrNo: String,
        val tid: String
    )

    private data class LoginAttemptResult(
        val errorCode: String,
        val rowErrorCode: String,
        val errorMsg: String,
        val loginIdOut: String,
        val memberIdOut: String,
        val autIdOut: String,
        val transYn: String,
        val cardDspYn: String,
        val mbrTypeCd: String,
        val mercTypeCd: String,
        val orgId: String,
        val mid: String,
        val wasNm: String
    )

    suspend fun verifyLogin(id: String? = null, password: String? = null): LoginResult = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val userId = (id ?: auth.getEasyShopId()).orEmpty().trim()
        val userPw = (password ?: auth.getEasyShopPassword()).orEmpty().trim()
        if (userId.isBlank() || userPw.isBlank()) {
            return@withContext LoginResult(false, "EasyShop 로그인 정보가 비어 있습니다.")
        }

        val http = HttpClient(context)
        val client = http.client
        http.clearCookies(EasyShopConfig.CALL_SERVICE_URL.toHttpUrl())
        primeSession(client)
        val attempts = listOf(
            buildLoginXmlDsSearch00(userId, userPw, gubun = "0"),
            buildLoginXmlDsInData(userId, userPw),
            buildLoginXmlDsLoginData(userId, userPw, "1"),
            buildLoginXmlDsLoginData(userId, userPw, "0"),
            buildLoginXmlDsOutData(userId, userPw, "undefined", "0"),
            buildLoginXmlDsOutData(userId, userPw, "string", "32")
        )

        var lastAttempt: LoginAttemptResult? = null
        var success = false
        for ((index, xml) in attempts.withIndex()) {
            val parsed = loginAttempt(client, xml, "try${index + 1}")
            lastAttempt = parsed ?: continue
            val isOk = parsed.errorCode == "0" && (parsed.rowErrorCode.isBlank() || parsed.rowErrorCode == "0")
            if (isOk && parsed.loginIdOut.isNotBlank()) {
                success = true
                break
            }
        }

        val finalAttempt = lastAttempt
            ?: return@withContext LoginResult(false, "EasyShop login.do 호출 실패")

        if (!success) {
            val err = if (finalAttempt.errorMsg.isBlank()) {
                "${finalAttempt.errorCode}/${finalAttempt.rowErrorCode}"
            } else {
                "${finalAttempt.errorCode}/${finalAttempt.rowErrorCode}, ${finalAttempt.errorMsg}"
            }
            return@withContext LoginResult(false, "EasyShop 로그인 실패($err)")
        }

        val memberId = finalAttempt.memberIdOut.ifBlank { auth.getEasyShopMemberId().orEmpty() }
        if (memberId.isNotBlank()) {
            auth.saveEasyShopMemberId(memberId)
        }
        val autId = finalAttempt.autIdOut.ifBlank { auth.getEasyShopAutId().orEmpty() }
        if (autId.isNotBlank()) {
            auth.saveEasyShopAutId(autId)
        }

        val statusReq = Request.Builder()
            .url(EasyShopConfig.CHECK_LOGIN_STATUS_URL)
            .header("User-Agent", webUserAgent)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Referer", EasyShopConfig.LOGIN_PAGE_URL)
            .get()
            .build()

        val statusBody = client.newCall(statusReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            Log.i("Vmms", "EasyShop checkLoginStatus.do -> ${resp.code}, body=${body.take(200)}")
            if (!resp.isSuccessful) return@use null
            body
        } ?: return@withContext LoginResult(false, "EasyShop 로그인 확인 실패")

        val statusLoginId = parseCol(statusBody, "login_id")
        val statusMemberId = parseCol(statusBody, "mbr_id")
        Log.i(
            "Vmms",
            "EasyShop checkLoginStatus parsed: login_id=$statusLoginId, mbr_id=$statusMemberId"
        )
        return@withContext if (statusLoginId.isNotBlank()) {
            LoginResult(true, "EasyShop 로그인 성공")
        } else {
            // 일부 환경에서는 checkLoginStatus에서 login_id를 비워 응답할 수 있어 로그인 응답을 신뢰한다.
            LoginResult(true, "EasyShop 로그인 성공(상태 확인 응답 제한)")
        }
    }

    private fun primeSession(client: okhttp3.OkHttpClient) {
        val warmupReq = Request.Builder()
            .url(EasyShopConfig.SMART_INDEX_URL)
            .header("User-Agent", webUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", EasyShopConfig.LOGIN_PAGE_URL)
            .get()
            .build()

        client.newCall(warmupReq).execute().use { resp ->
            Log.i("Vmms", "EasyShop warmup index.jsp -> ${resp.code}")
        }

        val checkReq = Request.Builder()
            .url(EasyShopConfig.CHECK_LOGIN_STATUS_URL)
            .header("User-Agent", webUserAgent)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Referer", EasyShopConfig.SMART_INDEX_URL)
            .get()
            .build()

        client.newCall(checkReq).execute().use { resp ->
            Log.i("Vmms", "EasyShop warmup checkLoginStatus.do -> ${resp.code}")
        }
    }

    private fun loginAttempt(client: OkHttpClient, xml: String, label: String): LoginAttemptResult? {
        val loginReq = Request.Builder()
            .url(EasyShopConfig.LOGIN_API_URL)
            .header("User-Agent", webUserAgent)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Content-Type", "text/xml; charset=UTF-8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("Expires", "-1")
            .header("If-Modified-Since", "Thu, 01 Jun 1970 00:00:00 GMT")
            .header("Referer", EasyShopConfig.SMART_INDEX_URL)
            .header("Origin", EasyShopConfig.SMART_API_BASE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .post(xml.toRequestBody("text/xml; charset=UTF-8".toMediaType()))
            .build()

        val loginBody = client.newCall(loginReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            Log.i("Vmms", "EasyShop login.do[$label] -> ${resp.code}, body=${body.take(220)}")
            if (!resp.isSuccessful) return@use null
            body
        } ?: return null

        val parsed = LoginAttemptResult(
            errorCode = parseParameter(loginBody, "ErrorCode"),
            rowErrorCode = parseCol(loginBody, "errorCode"),
            errorMsg = parseParameter(loginBody, "ErrorMsg"),
            loginIdOut = parseCol(loginBody, "login_id"),
            memberIdOut = parseCol(loginBody, "mbr_id"),
            autIdOut = parseCol(loginBody, "aut_id"),
            transYn = parseCol(loginBody, "trans_yn"),
            cardDspYn = parseCol(loginBody, "card_dsp_yn"),
            mbrTypeCd = parseCol(loginBody, "mbr_typ_cd"),
            mercTypeCd = parseCol(loginBody, "merc_typ_cd"),
            orgId = parseCol(loginBody, "org_id"),
            mid = parseCol(loginBody, "mid"),
            wasNm = parseCol(loginBody, "was_nm")
        )

        Log.i(
            "Vmms",
            "EasyShop parsed[$label]: ErrorCode=${parsed.errorCode}, rowErrorCode=${parsed.rowErrorCode}, ErrorMsg=${parsed.errorMsg}, login_id=${parsed.loginIdOut}, mbr_id=${parsed.memberIdOut}, aut_id=${parsed.autIdOut}, trans_yn=${parsed.transYn}, card_dsp_yn=${parsed.cardDspYn}, mbr_typ_cd=${parsed.mbrTypeCd}, merc_typ_cd=${parsed.mercTypeCd}, org_id=${parsed.orgId}, mid=${parsed.mid}, was_nm=${parsed.wasNm}"
        )
        return parsed
    }

    private fun buildLoginXmlDsInData(id: String, password: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                <Parameters/>
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="login_id" type="string" size="256"/>
                        <Column id="pswd" type="string" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="login_id">${xmlEscape(id)}</Col>
                            <Col id="pswd">${xmlEscape(password)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    /**
     * Frame/Login.xfdl.js 의 ds_search00 구조를 그대로 맞춘 로그인 요청
     * - SvcId: TCMM100S01
     * - gubun: 일반 로그인 "0"
     */
    private fun buildLoginXmlDsSearch00(id: String, password: String, gubun: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                <Parameters/>
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="gubun" type="STRING" size="2"/>
                        <Column id="login_id" type="STRING" size="20"/>
                        <Column id="pswd" type="STRING" size="50"/>
                        <Column id="ctz_no" type="STRING" size="13"/>
                        <Column id="ip_addr" type="STRING" size="23"/>
                        <Column id="cert_dn" type="STRING" size="408"/>
                        <Column id="otp_login_cd" type="STRING" size="1"/>
                        <Column id="otpYn" type="STRING" size="256"/>
                        <Column id="txtID" type="STRING" size="256"/>
                        <Column id="txtOtp" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM100S01</Col>
                            <Col id="gubun">${xmlEscape(gubun)}</Col>
                            <Col id="login_id">${xmlEscape(id)}</Col>
                            <Col id="pswd">${xmlEscape(password)}</Col>
                            <Col id="ctz_no"></Col>
                            <Col id="ip_addr"></Col>
                            <Col id="cert_dn"></Col>
                            <Col id="otp_login_cd"></Col>
                            <Col id="otpYn"></Col>
                            <Col id="txtID"></Col>
                            <Col id="txtOtp"></Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildLoginXmlDsOutData(id: String, password: String, columnType: String, size: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                <Parameters/>
                <Dataset id="dsOutData">
                    <ColumnInfo>
                        <Column id="login_id" type="$columnType" size="$size"/>
                        <Column id="pswd" type="$columnType" size="$size"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="login_id">${xmlEscape(id)}</Col>
                            <Col id="pswd">${xmlEscape(password)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    /**
     * smart_kicc.xadl.js의 로그인 입력 Dataset( gubun/login_id/pswd/... )을 그대로 맞춘 포맷
     */
    private fun buildLoginXmlDsLoginData(id: String, password: String, gubun: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                <Parameters/>
                <Dataset id="ds_login">
                    <ColumnInfo>
                        <Column id="gubun" type="INT" size="256"/>
                        <Column id="login_id" type="STRING" size="256"/>
                        <Column id="pswd" type="STRING" size="256"/>
                        <Column id="ctz_no" type="STRING" size="256"/>
                        <Column id="ip_addr" type="STRING" size="256"/>
                        <Column id="cert_dn" type="STRING" size="256"/>
                        <Column id="otp_login_cd" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="gubun">${xmlEscape(gubun)}</Col>
                            <Col id="login_id">${xmlEscape(id)}</Col>
                            <Col id="pswd">${xmlEscape(password)}</Col>
                            <Col id="ctz_no"></Col>
                            <Col id="ip_addr"></Col>
                            <Col id="cert_dn"></Col>
                            <Col id="otp_login_cd"></Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    suspend fun fetchSales(
        from: LocalDate,
        to: LocalDate,
        fromPageNo: Int = 0,
        endPageNo: Int = 1000
    ): SalesFetchResult = withContext(Dispatchers.IO) {
        val auth = AuthStore(context)
        val userId = auth.getEasyShopId().orEmpty().trim()
        val userPw = auth.getEasyShopPassword().orEmpty().trim()
        if (userId.isBlank() || userPw.isBlank()) {
            return@withContext buildSalesFetchFailure("EasyShop 로그인 정보가 비어 있습니다.")
        }

        val loginResult = verifyLogin(userId, userPw)
        if (!loginResult.success) {
            return@withContext buildSalesFetchFailure(loginResult.message)
        }

        val http = HttpClient(context)
        val client = http.client
        val memberId = auth.getEasyShopMemberId().orEmpty()
        if (memberId.isBlank()) {
            return@withContext buildSalesFetchFailure("EasyShop 회원 식별값(mbr_id)을 확인하지 못했습니다.")
        }

        val directParsed = queryTess103Sales(
            client = client,
            http = http,
            userId = userId,
            userPw = userPw,
            memberId = memberId,
            from = from,
            to = to,
            fromPageNo = fromPageNo,
            endPageNo = endPageNo,
            label = "TESS103S01"
        )

        val rangeDays = ChronoUnit.DAYS.between(from, to).toInt() + 1
        val directDistinctDateCount = directParsed.records
            .asSequence()
            .map { it.transactionDate }
            .filter { it.isNotBlank() }
            .distinct()
            .count()

        val shouldFallbackDaily = rangeDays > 1 && (
            !directParsed.success || directDistinctDateCount <= 1
        )

        if (!shouldFallbackDaily) {
            if (!directParsed.success) {
                return@withContext buildSalesFetchFailure(directParsed.message)
            }
            val merged = mergeSalesRecords(directParsed.records)
            return@withContext SalesFetchResult(
                success = true,
                message = "EasyShop 매출 조회 성공 (${merged.size}건)",
                records = merged,
                totalAmount = merged.sumOf { it.amount }
            )
        }

        val dayRecords = ArrayList<SalesRecord>()
        var dayCursor = from
        var successDays = 0
        var failDays = 0
        var lastError = ""
        while (!dayCursor.isAfter(to)) {
            val oneDay = queryTess103Sales(
                client = client,
                http = http,
                userId = userId,
                userPw = userPw,
                memberId = memberId,
                from = dayCursor,
                to = dayCursor,
                fromPageNo = fromPageNo,
                endPageNo = endPageNo,
                label = "TESS103S01-${dayCursor}"
            )
            if (oneDay.success) {
                successDays++
                dayRecords.addAll(oneDay.records)
            } else {
                failDays++
                lastError = oneDay.message
            }
            dayCursor = dayCursor.plusDays(1)
        }

        if (successDays == 0) {
            val failMessage = if (directParsed.success) {
                "EasyShop 일자별 매출 조회 결과가 없습니다."
            } else {
                listOf(directParsed.message, lastError).firstOrNull { it.isNotBlank() }
                    ?: "EasyShop 매출 조회 실패"
            }
            return@withContext buildSalesFetchFailure(failMessage)
        }

        val merged = mergeSalesRecords(dayRecords)
        return@withContext SalesFetchResult(
            success = true,
            message = "EasyShop 일자별 매출 조회 성공 (${merged.size}건, 성공일=$successDays, 실패일=$failDays)",
            records = merged,
            totalAmount = merged.sumOf { it.amount }
        )
    }

    private fun buildSalesFetchFailure(message: String): SalesFetchResult {
        return SalesFetchResult(
            success = false,
            message = message,
            records = emptyList(),
            totalAmount = 0
        )
    }

    suspend fun fetchTodaySales(): SalesFetchResult {
        val today = LocalDate.now()
        return fetchSales(
            from = today,
            to = today,
            fromPageNo = 0,
            endPageNo = 1000
        )
    }

    private suspend fun queryTess103Sales(
        client: OkHttpClient,
        http: HttpClient,
        userId: String,
        userPw: String,
        memberId: String,
        from: LocalDate,
        to: LocalDate,
        fromPageNo: Int,
        endPageNo: Int,
        label: String
    ): ParsedSales {
        val authStore = AuthStore(context)
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        var session = ensureEasyShopSessionCookies(http, url, loadSessionParams(http))
        val loginAutId = preloadLoginFlowContext(client, memberId, userId, session)
        val menuAutId = preloadMenuContext(client, memberId, session)
        val authCtx = preloadAuthContext(client, memberId, userId, session)
        var resolvedAutId = loginAutId
            .ifBlank { authCtx.autId }
            .ifBlank { menuAutId }
            .ifBlank { authStore.getEasyShopAutId().orEmpty() }
        preloadMdiContext(client, memberId, session)
        preloadSalesScreenContext(client, memberId, resolvedAutId, session)
        val permAutId = preloadPermissionCheck(client, memberId, session)
        if (resolvedAutId.isBlank() && permAutId.isNotBlank()) {
            resolvedAutId = permAutId
        }
        if (resolvedAutId.isNotBlank()) {
            authStore.saveEasyShopAutId(resolvedAutId)
        }
        val resolvedBizrNo = authCtx.bizrNo.ifBlank { "*" }
        val resolvedTid = authCtx.tid.ifBlank { "*" }

        session = ensureEasyShopSessionCookies(http, url, session)
        val requestXml = buildTess103S01Xml(
            from = from,
            to = to,
            fromPageNo = fromPageNo,
            endPageNo = endPageNo,
            memberId = memberId,
            bizrNo = resolvedBizrNo,
            tid = resolvedTid,
            session = session
        )

        val cookieNames = http.cookiesFor(url).map { it.name }.distinct().sorted()
        Log.i("Vmms", "EasyShop cookies before $label: $cookieNames")
        Log.i("Vmms", "EasyShop TESS context[$label]: aut_id=$resolvedAutId, bizr_no=$resolvedBizrNo, tid=$resolvedTid")

        val body = executeCallService(
            client = client,
            url = url,
            bodyXml = requestXml,
            label = label
        ) ?: return ParsedSales(
            success = false,
            message = "EasyShop CallService.do 호출 실패",
            records = emptyList()
        )

        var parsed = parseTess103S01(body)
        if (!parsed.success && parsed.message.contains("ErrorCode=3")) {
            verifyLogin(userId, userPw)
            session = ensureEasyShopSessionCookies(http, url, loadSessionParams(http))

            val retryXml = buildTess103S01Xml(
                from = from,
                to = to,
                fromPageNo = fromPageNo,
                endPageNo = endPageNo,
                memberId = memberId,
                bizrNo = resolvedBizrNo,
                tid = resolvedTid,
                session = session
            )
            val retryBody = executeCallService(
                client = client,
                url = url,
                bodyXml = retryXml,
                label = "$label-Retry"
            )
            if (!retryBody.isNullOrBlank()) {
                parsed = parseTess103S01(retryBody)
            }
        }
        return parsed
    }

    private fun preloadLoginFlowContext(
        client: OkHttpClient,
        memberId: String,
        loginId: String,
        session: SessionParams
    ): String {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        executeCallService(
            client = client,
            url = url,
            bodyXml = buildTcmm100S05Xml(memberId = memberId, loginId = loginId, session = session),
            label = "TCMM100S05"
        )
        executeCallService(
            client = client,
            url = url,
            bodyXml = buildTpoe201S11Xml(memberId = memberId, loginId = loginId, session = session),
            label = "TPOE201S11"
        )
        preloadSsoContext(client, memberId, session)
        val authBody = executeCallService(
            client = client,
            url = url,
            bodyXml = buildAuthInitXml(
                memberId = memberId,
                loginId = loginId,
                groupYn = "N",
                argPgmId = "Login",
                session = session
            ),
            label = "TCMM001S02-Login"
        ).orEmpty()

        if (authBody.isNotBlank()) {
            logDatasetSnapshot(authBody, "dsOutData", "TCMM001S02-Login")
            logDatasetSnapshot(authBody, "subData", "TCMM001S02-Login")
        }
        var autId = parseCol(authBody, "aut_id")
        if (autId.isBlank()) {
            val authByMember = executeCallService(
                client = client,
                url = url,
                bodyXml = buildAuthInitXml(
                    memberId = memberId,
                    loginId = memberId,
                    groupYn = "N",
                    argPgmId = "Login",
                    session = session
                ),
                label = "TCMM001S02-LoginByMember"
            ).orEmpty()
            if (authByMember.isNotBlank()) {
                logDatasetSnapshot(authByMember, "dsOutData", "TCMM001S02-LoginByMember")
                logDatasetSnapshot(authByMember, "subData", "TCMM001S02-LoginByMember")
            }
            autId = parseCol(authByMember, "aut_id")
        }
        Log.i("Vmms", "EasyShop preload login context: aut_id=$autId")
        return autId
    }

    private fun preloadSalesScreenContext(
        client: OkHttpClient,
        memberId: String,
        autId: String,
        session: SessionParams
    ) {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        executeCallService(
            client = client,
            url = url,
            bodyXml = buildTcmm100S03Xml(memberId = memberId, session = session),
            label = "TCMM100S03"
        )

        if (autId.isBlank()) return
        val menuIds = listOf("1000007727", "1000008225")
        menuIds.forEach { menuId ->
            executeCallService(
                client = client,
                url = url,
                bodyXml = buildTcmm001S10Xml(memberId = memberId, menuId = menuId, autId = autId, session = session),
                label = "TCMM001S10-$menuId"
            )
        }
        executeCallService(
            client = client,
            url = url,
            bodyXml = buildTcmm001S03Xml(memberId = memberId, autId = autId, session = session),
            label = "TCMM001S03"
        )
    }

    private fun preloadMdiContext(
        client: OkHttpClient,
        memberId: String,
        session: SessionParams
    ) {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        executeCallService(
            client = client,
            url = url,
            bodyXml = buildTmcm990S01Xml(memberId = memberId, session = session),
            label = "TMCM990S01"
        )
    }

    private fun ensureEasyShopSessionCookies(
        http: HttpClient,
        url: okhttp3.HttpUrl,
        session: SessionParams
    ): SessionParams {
        val now = System.currentTimeMillis()
        val expires = now + (24L * 60L * 60L * 1000L)
        val currentCookies = http.cookiesFor(url)
        val loginInfo = currentCookies.firstOrNull { it.name == "LoginInfo" }?.value.orEmpty()
        val sessionExpiryLong = session.sessionExpiry
            .toLongOrNull()
            ?.takeIf { it > now }
            ?: (now + 7_200_000L)
        val latestTouch = now.toString()
        val remainTime = (sessionExpiryLong - now).coerceAtLeast(0L).toString()
        val clientOffset = session.clientTimeOffset.ifBlank { "66" }

        fun upsert(name: String, value: String) {
            val cookie = Cookie.Builder()
                .name(name)
                .value(value)
                .hostOnlyDomain(url.host)
                .path("/")
                .expiresAt(expires)
                .build()
            http.upsertCookie(url, cookie)
        }
        upsert("LoginInfo", loginInfo)
        upsert("clientTimeOffset", clientOffset)
        upsert("remainTime", remainTime)
        upsert("latestTouch", latestTouch)
        upsert("sessionExpiry", sessionExpiryLong.toString())
        return SessionParams(
            clientTimeOffset = clientOffset,
            remainTime = remainTime,
            latestTouch = latestTouch,
            sessionExpiry = sessionExpiryLong.toString()
        )
    }

    private fun mergeSalesRecords(records: List<SalesRecord>): List<SalesRecord> {
        return records.distinctBy {
            listOf(
                it.transactionNo,
                it.terminalNo,
                it.approvalNo,
                it.transactionDate,
                it.transactionTime,
                it.amount.toString(),
                it.status
            ).joinToString("|")
        }
    }

    private data class ParsedSales(
        val success: Boolean,
        val message: String,
        val records: List<SalesRecord>
    )

    private fun parseTess103S01(xml: String): ParsedSales {
        if (xml.contains("<html", ignoreCase = true)) {
            return ParsedSales(
                success = false,
                message = "EasyShop HTML 오류 응답: ${extractHtmlErrorMessage(xml)}",
                records = emptyList()
            )
        }

        val errorCode = parseParameter(xml, "ErrorCode")
        val errorMsg = parseParameter(xml, "ErrorMsg")
        if (errorCode.isNotBlank() && errorCode != "0") {
            val msg = if (errorMsg.isBlank()) {
                "EasyShop 응답 오류(ErrorCode=$errorCode)"
            } else {
                "EasyShop 응답 오류(ErrorCode=$errorCode, $errorMsg)"
            }
            return ParsedSales(success = false, message = msg, records = emptyList())
        }

        val rows = extractDatasetRows(xml, "data")
        if (rows.isEmpty()) {
            return ParsedSales(success = true, message = "조회 데이터 없음", records = emptyList())
        }

        val out = ArrayList<SalesRecord>(rows.size)
        for ((index, row) in rows.withIndex()) {
            val seqNo = parseDatasetCol(row, "seq_no").toIntOrNull() ?: (index + 1)
            val rawData = parseDatasetCol(row, "data_set")
            if (rawData.isBlank()) continue
            val fields = rawData.split("@@")
            out.add(
                SalesRecord(
                    seqNo = seqNo,
                    transactionNo = fields.getOrElse(0) { "" }.trim(),
                    terminalNo = fields.getOrElse(2) { "" }.trim(),
                    status = fields.getOrElse(3) { "" }.trim(),
                    transactionDate = parseEasyShopDate(fields.getOrElse(4) { "" }.trim()),
                    transactionTime = formatEasyShopDateTime(fields.getOrElse(4) { "" }.trim()),
                    cardNo = fields.getOrElse(6) { "" }.trim(),
                    cardType = fields.getOrElse(7) { "" }.trim(),
                    issuerName = fields.getOrElse(8) { "" }.trim(),
                    purchaseName = fields.getOrElse(9) { "" }.trim(),
                    approvalNo = fields.getOrElse(10) { "" }.trim(),
                    amount = fields.getOrElse(11) { "0" }.trim().filter { it.isDigit() }.toIntOrNull() ?: 0,
                    responseText = fields.getOrElse(17) { "" }.trim(),
                    isCanceled = fields.getOrElse(3) { "" }.contains("취소")
                )
            )
        }
        return ParsedSales(success = true, message = "OK", records = out)
    }

    private fun extractHtmlErrorMessage(html: String): String {
        val title = Regex("""<title>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val bodyText = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val clippedBody = bodyText.take(120)
        return listOf(title, clippedBody).firstOrNull { it.isNotBlank() } ?: "unknown"
    }

    private fun buildTess103S01Xml(
        from: LocalDate,
        to: LocalDate,
        fromPageNo: Int,
        endPageNo: Int,
        memberId: String,
        bizrNo: String,
        tid: String,
        session: SessionParams
    ): String {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        val fromDate = from.format(fmt)
        val toDate = to.format(fmt)
        // HAR 기준 원문(sql_con/sql_alias) 그대로 사용해서 서버 파싱 차이를 제거한다.
        val sqlConEncoded = """NVL(trx_natr_no,&#39;&#32;&#39;)&#32;as&#32;trx_natr_no,&#32;NVL(trx_can_cl_cd,&#39;&#32;&#39;)&#32;as&#32;trx_can_cl_cd,&#32;NVL(slip_no,&#39;&#32;&#39;)&#32;as&#32;slip_no,&#32;NVL(case&#32;when&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;trx_can_cl_cd&#32;not&#32;in&#32;(&#39;0&#39;,&#32;&#39;7&#39;)&#32;then&#32;&#39;통신취소&#39;&#32;when&#32;ifm_typ_cd=&#39;0100&#39;&#32;and&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;trx_can_yn=&#39;C&#39;&#32;then&#32;&#39;승인원거래&#39;&#32;when&#32;ifm_typ_cd=&#39;0200&#39;&#32;and&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;trx_can_yn=&#39;C&#39;&#32;then&#32;&#39;취소원거래&#39;&#32;when&#32;m_gd_cd=&#39;LC&#39;&#32;then&#32;&#39;조회&#39;&#32;when&#32;trx_resp_cd!=&#39;0000&#39;&#32;then&#32;&#39;거절&#39;&#32;when&#32;ifm_typ_cd=&#39;0100&#39;&#32;then&#32;&#39;승인&#39;&#32;when&#32;ifm_typ_cd=&#39;0200&#39;&#32;and&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;trx_cl_cd=&#39;TI&#39;&#32;then&#32;&#39;전화취소&#39;&#32;when&#32;ifm_typ_cd=&#39;0200&#39;&#32;and&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;trx_dt&lt;&gt;orgnl_aprv_dt&#32;then&#32;&#39;취소&#39;&#32;when&#32;ifm_typ_cd=&#39;0200&#39;&#32;then&#32;&#39;취소&#39;&#32;when&#32;ifm_typ_cd&#32;is&#32;null&#32;then&#32;&#39;&#32;&#39;&#32;end,&#39;&#32;&#39;)&#32;as&#32;ifm_typ_cd,&#32;NVL(trx_dt||trx_tm,&#39;&#32;&#39;)&#32;as&#32;trx_dtm,&#32;NVL(nvl(tid,&#32;&#39;&#32;&#39;),&#39;&#32;&#39;)&#32;as&#32;tid,&#32;NVL(cardno,&#39;&#32;&#39;)&#32;as&#32;cardno,&#32;NVL(decode(nvl(card_typ_flag,&#39;N&#39;),&#32;&#39;Y&#39;,&#32;&#39;체크&#39;,&#39;G&#39;,&#39;기프트&#39;,&#39;신용&#39;),&#39;&#32;&#39;)&#32;as&#32;card_typ_cd,&#32;NVL(iss_fm_nm,&#39;&#32;&#39;)&#32;as&#32;iss_fm_nm,&#32;NVL(GET_FIN_ORG_NM(&#39;F01&#39;,&#32;purch_fm_cd),&#39;&#32;&#39;)&#32;as&#32;purch_fm_nm,&#32;NVL(jo_shop_no,&#39;&#32;&#39;)&#32;as&#32;jo_shop_no,&#32;case&#32;when&#32;ifm_typ_cd=&#39;0200&#39;&#32;and&#32;trx_resp_cd=&#39;0000&#39;&#32;and&#32;aut_yn&#32;=&#32;&#39;Y&#39;&#32;then&#32;nvl(-tot_trx_amt,0)&#32;else&#32;nvl(tot_trx_amt,0)&#32;end&#32;as&#32;tot_trx_amt,&#32;NVL(case&#32;when&#32;alot_months_cnt=&#39;0&#39;&#32;then&#32;&#39;일시불&#39;&#32;when&#32;to_char(alot_months_cnt)&#32;&lt;&gt;&#32;&#39;0&#39;&#32;then&#32;alot_months_cnt||&#39;개월&#39;&#32;else&#32;to_char(alot_months_cnt)&#32;end,&#39;&#32;&#39;)&#32;as&#32;alot_months_cnt,&#32;NVL(aprv_no,&#39;&#32;&#39;)&#32;as&#32;aprv_no,&#32;NVL(decode(trx_mthd_cd,&#32;&#39;2&#39;,&#32;&#39;Y&#39;,&#32;&#39;K&#39;,&#32;&#39;Y&#39;,&#32;&#39;N&#39;),&#39;&#32;&#39;)&#32;as&#32;trx_mthd_cd,&#32;NVL(decode(trim(orgnl_aprv_dt),&#32;&#39;20&#39;,&#32;&#39;&#39;,&#32;&#39;&#39;,&#32;&#39;&#39;,&#32;&#39;000000&#39;,&#32;&#39;&#39;,&#32;&#39;00000000&#39;,&#32;&#39;&#39;,&#32;&#39;20000000&#39;,&#32;&#39;&#39;,&#32;decode(substr(orgnl_aprv_dt,&#32;7,&#32;1),&#32;&#39;&#39;,&#32;&#39;20&#39;||orgnl_aprv_dt,&#32;orgnl_aprv_dt)),&#39;&#32;&#39;)&#32;as&#32;orgnl_aprv_dt,&#32;NVL(DECODE(HNDL_ST_DTL_CD,&#39;60&#39;,PAY_PLAN_DT,&#39;63&#39;,PAY_PLAN_DT,&#39;66&#39;,PAY_PLAN_DT,&#39;67&#39;,PAY_PLAN_DT,NULL),&#39;&#32;&#39;)&#32;as&#32;pay_plan_dt,&#32;NVL(get_com_cd_nm(&#39;TRN_C00002&#39;,&#32;trx_resp_cd),&#39;&#32;&#39;)&#32;as&#32;trx_resp_cd,&#32;NVL(decode(btr_sign_chk_2(mkr_cd,&#32;etc_sign_flag,&#32;purch_fm_cd,&#32;trx_resp_cd,&#32;tat_ddc_flag,&#32;tat_edc_flag,&#32;tat_dcc_rgst_cd),&#32;0,&#32;&#39;Y&#39;,&#32;DECODE&#32;(trm_typ_cd,&#32;&#39;MS&#39;,&#32;&#39;Y&#39;,&#39;N&#39;)),&#39;&#32;&#39;)&#32;as&#32;sign_yn,&#32;decode(bizr_no,&#39;1168119948&#39;,&#39;0&#39;,req_fee)&#32;as&#32;req_fee,&#32;NVL(req_inv_yn,&#39;&#32;&#39;)&#32;as&#32;req_inv_yn,&#32;NVL(req_ret_yn,&#39;&#32;&#39;)&#32;as&#32;req_ret_yn,&#32;decode(bizr_no,&#39;1168119948&#39;,&#39;0&#39;,req_pay_plan_amt)&#32;as&#32;req_pay_plan_amt,&#32;NVL(req_pur_yn,&#39;&#32;&#39;)&#32;as&#32;req_pur_yn,&#32;NVL(es_can_yn,&#39;&#32;&#39;)&#32;as&#32;es_can_yn,&#32;NVL(can_dt,&#39;&#32;&#39;)&#32;as&#32;can_dt,&#32;NVL(GET_COM_CD_NM(&#39;TRN_C00226&#39;,CL),&#39;&#32;&#39;)&#32;as&#32;simp_pay_cl_nm"""
        val sqlAliasEncoded = """trx_natr_no||&#39;@@&#39;||trx_can_cl_cd||&#39;@@&#39;||slip_no||&#39;@@&#39;||ifm_typ_cd||&#39;@@&#39;||trx_dtm||&#39;@@&#39;||tid||&#39;@@&#39;||cardno||&#39;@@&#39;||card_typ_cd||&#39;@@&#39;||iss_fm_nm||&#39;@@&#39;||purch_fm_nm||&#39;@@&#39;||jo_shop_no||&#39;@@&#39;||tot_trx_amt||&#39;@@&#39;||alot_months_cnt||&#39;@@&#39;||aprv_no||&#39;@@&#39;||trx_mthd_cd||&#39;@@&#39;||orgnl_aprv_dt||&#39;@@&#39;||pay_plan_dt||&#39;@@&#39;||trx_resp_cd||&#39;@@&#39;||sign_yn||&#39;@@&#39;||req_fee||&#39;@@&#39;||req_inv_yn||&#39;@@&#39;||req_ret_yn||&#39;@@&#39;||req_pay_plan_amt||&#39;@@&#39;||req_pur_yn||&#39;@@&#39;||es_can_yn||&#39;@@&#39;||can_dt||&#39;@@&#39;||simp_pay_cl_nm"""

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "div_Work",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="user_id" type="STRING" size="256"/>
                        <Column id="aut_id" type="STRING" size="256"/>
                        <Column id="func_cd" type="STRING" size="256"/>
                        <Column id="gubun" type="STRING" size="256"/>
                        <Column id="retrv_dt01" type="STRING" size="256"/>
                        <Column id="retrv_dt02" type="STRING" size="256"/>
                        <Column id="bizr_no" type="STRING" size="256"/>
                        <Column id="tid" type="STRING" size="256"/>
                        <Column id="aprv_no" type="STRING" size="256"/>
                        <Column id="cardno" type="STRING" size="256"/>
                        <Column id="iss_fm_nm" type="STRING" size="256"/>
                        <Column id="purch_fm_nm" type="STRING" size="256"/>
                        <Column id="fin_org_cd" type="STRING" size="256"/>
                        <Column id="jo_shop_no" type="STRING" size="256"/>
                        <Column id="tot_trx_amt" type="STRING" size="256"/>
                        <Column id="tot_trx_amt2" type="STRING" size="256"/>
                        <Column id="s_alot_months_cnt" type="STRING" size="256"/>
                        <Column id="s_alot_months_cnt2" type="STRING" size="256"/>
                        <Column id="trx_tm" type="STRING" size="256"/>
                        <Column id="trx_tm2" type="STRING" size="256"/>
                        <Column id="trx_can_cl_cd" type="STRING" size="256"/>
                        <Column id="trx_resp_cd" type="STRING" size="256"/>
                        <Column id="oil" type="STRING" size="256"/>
                        <Column id="fromPageNo" type="STRING" size="256"/>
                        <Column id="endPageNo" type="STRING" size="256"/>
                        <Column id="remk" type="STRING" size="256"/>
                        <Column id="remk2" type="STRING" size="256"/>
                        <Column id="remk_1" type="STRING" size="256"/>
                        <Column id="remk_2" type="STRING" size="256"/>
                        <Column id="trx_typ" type="STRING" size="256"/>
                        <Column id="cardno2" type="STRING" size="256"/>
                        <Column id="max_no" type="STRING" size="256"/>
                        <Column id="itm_tit2" type="STRING" size="256"/>
                        <Column id="itm_sz" type="STRING" size="256"/>
                        <Column id="itm_fmt" type="STRING" size="256"/>
                        <Column id="itm_mode" type="STRING" size="256"/>
                        <Column id="itm_align" type="STRING" size="256"/>
                        <Column id="sql_con" type="STRING" size="256"/>
                        <Column id="sql_alias" type="STRING" size="256"/>
                        <Column id="gid" type="STRING" size="256"/>
                        <Column id="card_typ_flag" type="STRING" size="256"/>
                        <Column id="excp_yn" type="STRING" size="256"/>
                        <Column id="trx_dt" type="STRING" size="256"/>
                        <Column id="aply_yn" type="STRING" size="256"/>
                        <Column id="rowCnt" type="BIGDECIMAL" size="12"/>
                        <Column id="rowCnt02" type="BIGDECIMAL" size="12"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TESS103S01</Col>
                            <Col id="func_cd">3</Col>
                            <Col id="gubun">0</Col>
                            <Col id="retrv_dt01">$fromDate</Col>
                            <Col id="retrv_dt02">$toDate</Col>
                            <Col id="bizr_no">${xmlEscape(bizrNo)}</Col>
                            <Col id="tid">${xmlEscape(tid)}</Col>
                            <Col id="trx_resp_cd">0000</Col>
                            <Col id="fromPageNo">$fromPageNo</Col>
                            <Col id="endPageNo">$endPageNo</Col>
                            <Col id="cardno2" />
                            <Col id="sql_con">$sqlConEncoded</Col>
                            <Col id="sql_alias">$sqlAliasEncoded</Col>
                            <Col id="excp_yn">0</Col>
                            <Col id="aply_yn">0</Col>
                            <Col id="rowCnt">0</Col>
                            <Col id="rowCnt02">0</Col>
                        </Row>
                    </Rows>
                </Dataset>
                <Dataset id="purch">
                    <ColumnInfo>
                        <Column id="purch_fm_cd" type="STRING" size="6"/>
                    </ColumnInfo>
                    <Rows>
                    </Rows>
                </Dataset>
                <Dataset id="cardTyp">
                    <ColumnInfo>
                        <Column id="card_typ" type="STRING" size="1"/>
                    </ColumnInfo>
                    <Rows>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTcmm100S05Xml(
        memberId: String,
        loginId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "Login",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="gubun" type="STRING" size="256"/>
                        <Column id="login_id" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM100S05</Col>
                            <Col id="gubun">0</Col>
                            <Col id="login_id">${xmlEscape(loginId)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTpoe201S11Xml(
        memberId: String,
        loginId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "Login",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="func_cd" type="STRING" size="256"/>
                        <Column id="login_id" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TPOE201S11</Col>
                            <Col id="func_cd">0</Col>
                            <Col id="login_id">${xmlEscape(loginId)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTcmm100S03Xml(
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "div_Work",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="rowCnt" type="STRING" size="256"/>
                        <Column id="func_cd" type="STRING" size="256"/>
                        <Column id="mbr_id" type="STRING" size="256"/>
                        <Column id="pgm_id" type="STRING" size="256"/>
                        <Column id="fst_rgtr_id" type="STRING" size="256"/>
                        <Column id="lst_updr_id" type="STRING" size="256"/>
                        <Column id="url_path" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM100S03</Col>
                            <Col id="rowCnt">1</Col>
                            <Col id="func_cd">1</Col>
                            <Col id="mbr_id">${xmlEscape(memberId)}</Col>
                            <Col id="pgm_id">WESS102T01</Col>
                            <Col id="fst_rgtr_id">${xmlEscape(memberId)}</Col>
                            <Col id="lst_updr_id">${xmlEscape(memberId)}</Col>
                            <Col id="url_path">SEO</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTcmm001S10Xml(
        memberId: String,
        menuId: String,
        autId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "div_Work",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="menu_id" type="STRING" size="256"/>
                        <Column id="aut_id" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM001S10</Col>
                            <Col id="menu_id">${xmlEscape(menuId)}</Col>
                            <Col id="aut_id">${xmlEscape(autId)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTcmm001S03Xml(
        memberId: String,
        autId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "div_Work",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="aut_id" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM001S03</Col>
                            <Col id="aut_id">${xmlEscape(autId)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildTmcm990S01Xml(
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "MDIFrame",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="login_id" type="STRING" size="10"/>
                        <Column id="menu_id" type="STRING" size="20"/>
                        <Column id="aply_sta_dtm" type="STRING" size="14"/>
                        <Column id="aply_end_dtm" type="STRING" size="14"/>
                        <Column id="login_typ_cd" type="STRING" size="3"/>
                        <Column id="fst_rgtr_id" type="STRING" size="10"/>
                        <Column id="login_mthd_cd" type="STRING" size="1"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TMCM990S01</Col>
                            <Col id="login_id">${xmlEscape(memberId)}</Col>
                            <Col id="menu_id">1000007726</Col>
                            <Col id="login_mthd_cd">5</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }


    private fun buildMenuInitXml(
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "LeftFrame",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="gubun" type="STRING" size="256"/>
                        <Column id="mbr_id" type="STRING" size="256"/>
                        <Column id="url_path" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM100S02</Col>
                            <Col id="gubun">1</Col>
                            <Col id="mbr_id">${xmlEscape(memberId)}</Col>
                            <Col id="url_path">SEO</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildAuthInitXml(
        memberId: String,
        loginId: String,
        groupYn: String,
        argPgmId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = argPgmId,
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="login_id" type="STRING" size="256"/>
                        <Column id="group_yn" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM001S02</Col>
                            <Col id="login_id">${xmlEscape(loginId)}</Col>
                            <Col id="group_yn">${xmlEscape(groupYn)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun buildSsoInitXml(
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "Login",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="mbr_id" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM100S08</Col>
                            <Col id="mbr_id">${xmlEscape(memberId)}</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun preloadSsoContext(
        client: OkHttpClient,
        memberId: String,
        session: SessionParams
    ) {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        val body = executeCallService(
            client = client,
            url = url,
            bodyXml = buildSsoInitXml(memberId, session),
            label = "TCMM100S08"
        ) ?: return

        logDatasetSnapshot(body, "dsOutData", "TCMM100S08")
        val errorCode = parseParameter(body, "ErrorCode")
        val errorMsg = parseParameter(body, "ErrorMsg")
        Log.i("Vmms", "EasyShop preload sso result: ErrorCode=$errorCode, ErrorMsg=$errorMsg")
    }

    private fun preloadAuthContext(
        client: OkHttpClient,
        memberId: String,
        loginId: String,
        session: SessionParams
    ): AuthContext {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        val pgmIds = listOf("Login", "div_Work", "LeftFrame", "MainFrame")
        var best = AuthContext(autId = "", bizrNo = "", tid = "")

        for (pgmId in pgmIds) {
            val label = "TCMM001S02-$pgmId"
            val body = executeCallService(
                client = client,
                url = url,
                bodyXml = buildAuthInitXml(
                    memberId = memberId,
                    loginId = loginId,
                    groupYn = "N",
                    argPgmId = pgmId,
                    session = session
                ),
                label = label
            ) ?: continue

            val dsOutRows = extractDatasetRows(body, "dsOutData")
            val dsOutRow = dsOutRows.firstOrNull().orEmpty()
            val autId = parseDatasetCol(dsOutRow, "aut_id").ifBlank { parseCol(body, "aut_id") }
            val bizrYn = parseDatasetCol(dsOutRow, "bizr_yn")
            val tidYn = parseDatasetCol(dsOutRow, "tid_yn")
            logDatasetSnapshot(body, "dsOutData", label)
            logDatasetSnapshot(body, "subData", label)

            val subRows = extractDatasetRows(body, "subData")
            val bizrNo = subRows.firstNotNullOfOrNull { row ->
                val cdId = parseDatasetCol(row, "com_cd_id").uppercase()
                if (cdId == "BIZR_NO") parseDatasetCol(row, "com_cd_val") else null
            }.orEmpty()
            val tid = subRows.firstNotNullOfOrNull { row ->
                val cdId = parseDatasetCol(row, "com_cd_id").uppercase()
                if (cdId == "TID") parseDatasetCol(row, "com_cd_val") else null
            }.orEmpty()
            val sampleSubCodes = subRows.take(8).map { row ->
                val k = parseDatasetCol(row, "com_cd_id")
                val v = parseDatasetCol(row, "com_cd_val")
                "$k=$v"
            }.joinToString(",")
            Log.i(
                "Vmms",
                "EasyShop preload auth[$pgmId]: aut_id=$autId, bizr_yn=$bizrYn, tid_yn=$tidYn, bizr_no=$bizrNo, tid=$tid, subDataSample=[$sampleSubCodes]"
            )

            val current = AuthContext(autId = autId, bizrNo = bizrNo, tid = tid)
            val currentScore = listOf(current.autId, current.bizrNo, current.tid).count { it.isNotBlank() }
            val bestScore = listOf(best.autId, best.bizrNo, best.tid).count { it.isNotBlank() }
            if (currentScore > bestScore) {
                best = current
            }
            if (current.autId.isNotBlank() && current.bizrNo.isNotBlank() && current.tid.isNotBlank()) {
                break
            }
        }
        return best
    }

    private fun buildPermissionCheckXml(
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                ${buildCommonParametersXml(
            argPgmId = "div_Work",
            memberId = memberId,
            session = session
        )}
                <Dataset id="dsInData">
                    <ColumnInfo>
                        <Column id="SvcId" type="STRING" size="256"/>
                        <Column id="key_value" type="STRING" size="256"/>
                        <Column id="gubun" type="STRING" size="256"/>
                    </ColumnInfo>
                    <Rows>
                        <Row>
                            <Col id="SvcId">TCMM116S01</Col>
                            <Col id="key_value">MCM_B00002</Col>
                            <Col id="gubun">10</Col>
                        </Row>
                    </Rows>
                </Dataset>
            </Root>
        """.trimIndent()
    }

    private fun preloadPermissionCheck(
        client: OkHttpClient,
        memberId: String,
        session: SessionParams
    ): String {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        val body = executeCallService(
            client = client,
            url = url,
            bodyXml = buildPermissionCheckXml(memberId, session),
            label = "TCMM116S01"
        ) ?: return ""

        logDatasetSnapshot(body, "dsOutData", "TCMM116S01")
        logDatasetSnapshot(body, "AUT_GRID", "TCMM116S01")
        val errorCode = parseParameter(body, "ErrorCode")
        val errorMsg = parseParameter(body, "ErrorMsg")
        val cnt = parseCol(body, "cnt")
        val autId = parseCol(body, "aut_id")
        Log.i("Vmms", "EasyShop preload perm result: ErrorCode=$errorCode, ErrorMsg=$errorMsg, cnt=$cnt, aut_id=$autId")
        return autId
    }

    private fun preloadMenuContext(
        client: OkHttpClient,
        memberId: String,
        session: SessionParams
    ): String {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        val body = executeCallService(
            client = client,
            url = url,
            bodyXml = buildMenuInitXml(memberId, session),
            label = "TCMM100S02"
        )
        if (!body.isNullOrBlank()) {
            val errorCode = parseParameter(body, "ErrorCode")
            val errorMsg = parseParameter(body, "ErrorMsg")
            val autId = parseCol(body, "aut_id")
            logDatasetSnapshot(body, "dsOutData", "TCMM100S02")
            logDatasetSnapshot(body, "top_menu", "TCMM100S02")
            Log.i("Vmms", "EasyShop preload menu result: ErrorCode=$errorCode, ErrorMsg=$errorMsg, aut_id=$autId")
            return autId
        }
        return ""
    }

    private fun loadSessionParams(http: HttpClient): SessionParams {
        val url = EasyShopConfig.CALL_SERVICE_URL.toHttpUrl()
        val cookies = http.cookiesFor(url)
        val byName = cookies.associateBy({ it.name }, { it.value })
        val latestTouch = byName["latestTouch"].orEmpty().ifBlank { System.currentTimeMillis().toString() }
        val sessionExpiry = byName["sessionExpiry"].orEmpty().ifBlank { (System.currentTimeMillis() + 7_200_000L).toString() }
        val remainTime = byName["remainTime"].orEmpty().ifBlank {
            val diff = sessionExpiry.toLongOrNull()?.minus(System.currentTimeMillis()) ?: 7_200_000L
            diff.coerceAtLeast(0L).toString()
        }
        return SessionParams(
            clientTimeOffset = byName["clientTimeOffset"].orEmpty().ifBlank { "66" },
            remainTime = remainTime,
            latestTouch = latestTouch,
            sessionExpiry = sessionExpiry
        )
    }

    private fun buildCommonParametersXml(
        argPgmId: String,
        memberId: String,
        session: SessionParams
    ): String {
        return """
            <Parameters>
                <Parameter id="LoginInfo" />
                <Parameter id="clientTimeOffset">${xmlEscape(session.clientTimeOffset)}</Parameter>
                <Parameter id="remainTime">${xmlEscape(session.remainTime)}</Parameter>
                <Parameter id="latestTouch">${xmlEscape(session.latestTouch)}</Parameter>
                <Parameter id="sessionExpiry">${xmlEscape(session.sessionExpiry)}</Parameter>
                <Parameter id="arg_pgmId">${xmlEscape(argPgmId)}</Parameter>
                <Parameter id="nMbr_id">${xmlEscape(memberId)}</Parameter>
            </Parameters>
        """.trimIndent()
    }

    private fun executeCallService(
        client: OkHttpClient,
        url: okhttp3.HttpUrl,
        bodyXml: String,
        label: String
    ): String? {
        val normalizedBody = bodyXml.trimStart()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", webUserAgent)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Content-Type", "text/xml")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("Expires", "-1")
            .header("If-Modified-Since", "Thu, 01 Jun 1970 00:00:00 GMT")
            .header("Referer", EasyShopConfig.SMART_INDEX_URL)
            .header("Origin", EasyShopConfig.SMART_API_BASE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .post(normalizedBody.toRequestBody("text/xml".toMediaType()))
            .build()

        if (label.startsWith("TESS103S01")) {
            Log.i("Vmms", "EasyShop CallService.do[$label] request len=${normalizedBody.length}, url=$url")
            Log.i("Vmms", "EasyShop CallService.do[$label] request head: ${normalizedBody.take(320)}")
        }

        return client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val errCode = parseParameter(text, "ErrorCode")
            val errMsg = parseParameter(text, "ErrorMsg")
            Log.i("Vmms", "EasyShop CallService.do[$label] -> ${resp.code}, ErrorCode=$errCode, ErrorMsg=$errMsg")
            if (label.startsWith("TESS103S01") || label.startsWith("TCMM")) {
                val hasDataDataset = text.contains("Dataset id=\"data\"", ignoreCase = true)
                val isHtml = text.contains("<html", ignoreCase = true)
                val dataRows = extractDatasetRows(text, "data").size
                Log.i(
                    "Vmms",
                    "EasyShop CallService.do[$label] body meta: len=${text.length}, hasDataDataset=$hasDataDataset, dataRows=$dataRows, isHtml=$isHtml"
                )
                if (dataRows == 0 || errCode.isBlank()) {
                    Log.i("Vmms", "EasyShop CallService.do[$label] body head: ${text.take(260)}")
                    if (isHtml) {
                        Log.i("Vmms", "EasyShop CallService.do[$label] html msg: ${extractHtmlErrorMessage(text)}")
                    }
                }
            }
            if (!resp.isSuccessful) return@use null
            text
        }
    }

    private fun formatEasyShopDateTime(raw: String): String {
        // yyyyMMddHHmmss -> M/d HH:mm
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 12) return raw
        val month = digits.substring(4, 6).toIntOrNull() ?: return raw
        val day = digits.substring(6, 8).toIntOrNull() ?: return raw
        val hh = digits.substring(8, 10)
        val mm = digits.substring(10, 12)
        return "$month/$day $hh:$mm"
    }

    private fun parseEasyShopDate(raw: String): String {
        // yyyyMMddHHmmss -> yyyy-MM-dd
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return ""
        val y = digits.substring(0, 4)
        val m = digits.substring(4, 6)
        val d = digits.substring(6, 8)
        return "$y-$m-$d"
    }

    private fun extractDatasetRows(xml: String, datasetId: String): List<String> {
        val datasetRegex = Regex(
            """<Dataset\s+id="$datasetId"[^>]*>(.*?)</Dataset>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val datasetBody = datasetRegex.find(xml)?.groupValues?.getOrNull(1).orEmpty()
        if (datasetBody.isBlank()) return emptyList()

        val rowRegex = Regex(
            """<Row>(.*?)</Row>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return rowRegex.findAll(datasetBody).map { it.groupValues[1] }.toList()
    }

    private fun extractDatasetColumnIds(xml: String, datasetId: String): List<String> {
        val datasetRegex = Regex(
            """<Dataset\s+id="$datasetId"[^>]*>(.*?)</Dataset>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val datasetBody = datasetRegex.find(xml)?.groupValues?.getOrNull(1).orEmpty()
        if (datasetBody.isBlank()) return emptyList()

        val colRegex = Regex(
            """<Column\s+id="([^"]+)"""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return colRegex.findAll(datasetBody).map { it.groupValues[1] }.toList()
    }

    private fun logDatasetSnapshot(xml: String, datasetId: String, label: String) {
        val columnIds = extractDatasetColumnIds(xml, datasetId)
        if (columnIds.isEmpty()) return
        val row = extractDatasetRows(xml, datasetId).firstOrNull().orEmpty()

        if (row.isBlank()) {
            Log.i("Vmms", "EasyShop $label/$datasetId columns=${columnIds.joinToString(",")}")
            return
        }

        val nonEmpty = columnIds.mapNotNull { colId ->
            val value = parseDatasetCol(row, colId)
            if (value.isBlank()) null else "$colId=$value"
        }
        val preview = nonEmpty.take(16).joinToString(",")
        Log.i(
            "Vmms",
            "EasyShop $label/$datasetId firstRow nonEmpty=[$preview] totalNonEmpty=${nonEmpty.size}"
        )
    }

    private fun parseDatasetCol(rowXml: String, id: String): String {
        val regex = Regex(
            """<Col\s+id="$id"\s*>(.*?)</Col>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = regex.find(rowXml)?.groupValues?.getOrNull(1).orEmpty()
        return Parser.unescapeEntities(raw, false).trim()
    }

    private fun parseParameter(xml: String, id: String): String {
        val regex = Regex(
            """<Parameter\s+id="$id"[^>]*>(.*?)</Parameter>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = regex.find(xml)?.groupValues?.getOrNull(1).orEmpty()
        return Parser.unescapeEntities(raw, false).trim()
    }

    private fun parseCol(xml: String, id: String): String {
        val regex = Regex(
            """<Col\s+id="$id"\s*>(.*?)</Col>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = regex.find(xml)?.groupValues?.getOrNull(1).orEmpty()
        return Parser.unescapeEntities(raw, false).trim()
    }

    private fun xmlEscape(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
