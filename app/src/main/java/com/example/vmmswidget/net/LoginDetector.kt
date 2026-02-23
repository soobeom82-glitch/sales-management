package com.example.vmmswidget.net

object LoginDetector {
    fun isLoginPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("password") && lower.contains("login")
    }
}
