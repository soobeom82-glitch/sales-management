package com.example.vmmswidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.vmmswidget.R
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.widget.WorkScheduler
import com.example.vmmswidget.worker.FetchWidgetWorker

class LoginFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inputId = view.findViewById<EditText>(R.id.input_id)
        val inputPassword = view.findViewById<EditText>(R.id.input_password)
        val btnSave = view.findViewById<Button>(R.id.btn_save)
        val status = view.findViewById<TextView>(R.id.text_status)

        val auth = AuthStore(requireContext())
        val dataStore = WidgetDataStore(requireContext())

        inputId.setText(auth.getId() ?: "")
        inputPassword.setText(auth.getPassword() ?: "")
        status.text = "상태: ${dataStore.getDisplayText()}"

        btnSave.setOnClickListener {
            val id = inputId.text?.toString()?.trim().orEmpty()
            val pw = inputPassword.text?.toString()?.trim().orEmpty()
            auth.saveCredentials(id, pw)

            WorkScheduler.schedulePeriodic(requireContext())
            WorkScheduler.scheduleDailyRecord(requireContext())
            val req = OneTimeWorkRequestBuilder<FetchWidgetWorker>()
                .setInputData(workDataOf(FetchWidgetWorker.KEY_FORCE_REFRESH to true))
                .build()
            WorkManager.getInstance(requireContext()).enqueue(req)
            status.text = "상태: 새로고침 요청됨"
        }
    }
}
