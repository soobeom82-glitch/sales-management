package com.example.vmmswidget.data

import android.content.Context

class AuthStore(context: Context) {
    private val prefs = EncryptedPrefsFactory.create(context, "auth_store")

    fun saveCredentials(id: String, password: String) {
        saveVmmsCredentials(id, password)
    }

    fun saveVmmsCredentials(id: String, password: String) {
        prefs.edit()
            .putString(KEY_VMMS_ID, id)
            .putString(KEY_VMMS_PASSWORD, password)
            // Backward-compatibility for old keys used by previous app versions.
            .putString(KEY_ID_LEGACY, id)
            .putString(KEY_PASSWORD_LEGACY, password)
            .apply()
    }

    fun saveEasyShopCredentials(id: String, password: String) {
        prefs.edit()
            .putString(KEY_EASYSHOP_ID, id)
            .putString(KEY_EASYSHOP_PASSWORD, password)
            // 로그인 계정이 바뀔 수 있으므로 member id는 재검증 시 갱신
            .putString(KEY_EASYSHOP_MEMBER_ID, null)
            .putString(KEY_EASYSHOP_AUT_ID, null)
            .apply()
    }

    fun saveEasyShopMemberId(memberId: String?) {
        prefs.edit()
            .putString(KEY_EASYSHOP_MEMBER_ID, memberId)
            .apply()
    }

    fun saveEasyShopAutId(autId: String?) {
        prefs.edit()
            .putString(KEY_EASYSHOP_AUT_ID, autId)
            .apply()
    }

    fun saveCancelCertProfile(user: String, phone: String, email: String) {
        prefs.edit()
            .putString(KEY_CANCEL_CERT_USER, user)
            .putString(KEY_CANCEL_CERT_PHONE, phone)
            .putString(KEY_CANCEL_CERT_EMAIL, email)
            .apply()
    }

    fun getId(): String? = getVmmsId()
    fun getPassword(): String? = getVmmsPassword()

    fun getVmmsId(): String? = prefs.getString(KEY_VMMS_ID, null)
        ?: prefs.getString(KEY_ID_LEGACY, null)

    fun getVmmsPassword(): String? = prefs.getString(KEY_VMMS_PASSWORD, null)
        ?: prefs.getString(KEY_PASSWORD_LEGACY, null)

    fun getEasyShopId(): String? = prefs.getString(KEY_EASYSHOP_ID, null)
    fun getEasyShopPassword(): String? = prefs.getString(KEY_EASYSHOP_PASSWORD, null)
    fun getEasyShopMemberId(): String? = prefs.getString(KEY_EASYSHOP_MEMBER_ID, null)
    fun getEasyShopAutId(): String? = prefs.getString(KEY_EASYSHOP_AUT_ID, null)
    fun getCancelCertUser(): String? = prefs.getString(KEY_CANCEL_CERT_USER, null)
    fun getCancelCertPhone(): String? = prefs.getString(KEY_CANCEL_CERT_PHONE, null)
    fun getCancelCertEmail(): String? = prefs.getString(KEY_CANCEL_CERT_EMAIL, null)

    companion object {
        private const val KEY_ID_LEGACY = "id"
        private const val KEY_PASSWORD_LEGACY = "password"
        private const val KEY_VMMS_ID = "vmms_id"
        private const val KEY_VMMS_PASSWORD = "vmms_password"
        private const val KEY_EASYSHOP_ID = "easyshop_id"
        private const val KEY_EASYSHOP_PASSWORD = "easyshop_password"
        private const val KEY_EASYSHOP_MEMBER_ID = "easyshop_member_id"
        private const val KEY_EASYSHOP_AUT_ID = "easyshop_aut_id"
        private const val KEY_CANCEL_CERT_USER = "cancel_cert_user"
        private const val KEY_CANCEL_CERT_PHONE = "cancel_cert_phone"
        private const val KEY_CANCEL_CERT_EMAIL = "cancel_cert_email"
    }
}
