package com.example.vmmswidget.ui

import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.vmmswidget.R
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.net.EasyShopRepository
import com.example.vmmswidget.widget.WorkScheduler
import com.example.vmmswidget.worker.FetchEasyShopWidgetWorker
import com.example.vmmswidget.worker.FetchWidgetWorker
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inputVmmsId = view.findViewById<EditText>(R.id.input_vmms_id)
        val inputVmmsPassword = view.findViewById<EditText>(R.id.input_vmms_password)
        val inputEasyShopId = view.findViewById<EditText>(R.id.input_easyshop_id)
        val inputEasyShopPassword = view.findViewById<EditText>(R.id.input_easyshop_password)
        val inputCertUser = view.findViewById<EditText>(R.id.input_cert_user)
        val inputCertPhone = view.findViewById<EditText>(R.id.input_cert_phone)
        val inputCertEmail = view.findViewById<EditText>(R.id.input_cert_email)
        val btnSave = view.findViewById<Button>(R.id.btn_save)
        val btnEasyShopVerify = view.findViewById<Button>(R.id.btn_easyshop_verify)
        val status = view.findViewById<TextView>(R.id.text_status)

        val auth = AuthStore(requireContext())
        val dataStore = WidgetDataStore(requireContext())

        inputVmmsId.setText(auth.getVmmsId() ?: "")
        inputVmmsPassword.setText(auth.getVmmsPassword() ?: "")
        inputEasyShopId.setText(auth.getEasyShopId() ?: "")
        inputEasyShopPassword.setText(auth.getEasyShopPassword() ?: "")
        inputCertUser.setText(auth.getCancelCertUser() ?: "")
        inputCertPhone.setText(auth.getCancelCertPhone() ?: "")
        inputCertEmail.setText(auth.getCancelCertEmail() ?: "")
        status.text = "상태: ${dataStore.getDisplayText()}"

        val autoSaveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                auth.saveVmmsCredentials(
                    inputVmmsId.text?.toString()?.trim().orEmpty(),
                    inputVmmsPassword.text?.toString()?.trim().orEmpty()
                )
                auth.saveEasyShopCredentials(
                    inputEasyShopId.text?.toString()?.trim().orEmpty(),
                    inputEasyShopPassword.text?.toString()?.trim().orEmpty()
                )
                auth.saveCancelCertProfile(
                    inputCertUser.text?.toString()?.trim().orEmpty(),
                    inputCertPhone.text?.toString()?.trim().orEmpty(),
                    inputCertEmail.text?.toString()?.trim().orEmpty()
                )
            }
        }
        inputVmmsId.addTextChangedListener(autoSaveWatcher)
        inputVmmsPassword.addTextChangedListener(autoSaveWatcher)
        inputEasyShopId.addTextChangedListener(autoSaveWatcher)
        inputEasyShopPassword.addTextChangedListener(autoSaveWatcher)
        inputCertUser.addTextChangedListener(autoSaveWatcher)
        inputCertPhone.addTextChangedListener(autoSaveWatcher)
        inputCertEmail.addTextChangedListener(autoSaveWatcher)

        btnEasyShopVerify.setOnClickListener {
            val id = inputEasyShopId.text?.toString()?.trim().orEmpty()
            val pw = inputEasyShopPassword.text?.toString()?.trim().orEmpty()
            auth.saveEasyShopCredentials(id, pw)
            if (id.isBlank() || pw.isBlank()) {
                status.text = "상태: EasyShop ID/PW를 입력하세요."
                return@setOnClickListener
            }
            btnEasyShopVerify.isEnabled = false
            status.text = "상태: EasyShop 로그인 확인 중..."
            viewLifecycleOwner.lifecycleScope.launch {
                val result = EasyShopRepository(requireContext()).verifyLogin(id, pw)
                btnEasyShopVerify.isEnabled = true
                status.text = "상태: ${result.message}"
            }
        }

        btnSave.setOnClickListener {
            val vmmsId = inputVmmsId.text?.toString()?.trim().orEmpty()
            val vmmsPw = inputVmmsPassword.text?.toString()?.trim().orEmpty()
            val easyShopId = inputEasyShopId.text?.toString()?.trim().orEmpty()
            val easyShopPw = inputEasyShopPassword.text?.toString()?.trim().orEmpty()
            val certUser = inputCertUser.text?.toString()?.trim().orEmpty()
            val certPhone = inputCertPhone.text?.toString()?.trim().orEmpty()
            val certEmail = inputCertEmail.text?.toString()?.trim().orEmpty()

            auth.saveVmmsCredentials(vmmsId, vmmsPw)
            auth.saveEasyShopCredentials(easyShopId, easyShopPw)
            auth.saveCancelCertProfile(certUser, certPhone, certEmail)

            WorkScheduler.schedulePeriodic(requireContext())
            WorkScheduler.scheduleEasyShopPeriodic(requireContext())
            val req = OneTimeWorkRequestBuilder<FetchWidgetWorker>()
                .setInputData(workDataOf(FetchWidgetWorker.KEY_FORCE_REFRESH to true))
                .build()
            WorkManager.getInstance(requireContext()).enqueue(req)
            val easyReq = OneTimeWorkRequestBuilder<FetchEasyShopWidgetWorker>()
                .setInputData(workDataOf(FetchEasyShopWidgetWorker.KEY_FORCE_REFRESH to true))
                .build()
            WorkManager.getInstance(requireContext()).enqueue(easyReq)
            status.text = "상태: 로그인 정보 저장 및 새로고침 요청됨"
        }
    }
}
