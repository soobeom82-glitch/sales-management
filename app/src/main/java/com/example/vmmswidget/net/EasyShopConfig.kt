package com.example.vmmswidget.net

object EasyShopConfig {
    const val WEB_BASE_URL = "https://www.easyshop.co.kr"
    const val SMART_API_BASE_URL = "https://smarteasyshop.kicc.co.kr"

    const val LOGIN_PAGE_PATH = "/taxLogn/taxLognLogin.kicc"
    val LOGIN_PAGE_URL: String = WEB_BASE_URL + LOGIN_PAGE_PATH
    val SMART_INDEX_URL: String = SMART_API_BASE_URL + "/smart_kicc/index.jsp"

    val LOGIN_API_URL: String = SMART_API_BASE_URL + "/login.do"
    val CHECK_LOGIN_STATUS_URL: String = SMART_API_BASE_URL + "/checkLoginStatus.do"
    val CALL_SERVICE_URL: String = SMART_API_BASE_URL + "/CallService.do"
}
