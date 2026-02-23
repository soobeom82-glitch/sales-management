package com.example.vmmswidget.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vmmswidget.R
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.net.TransactionsRepository
import kotlinx.coroutines.launch

class TransactionDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        val transactionNo = intent.getStringExtra(EXTRA_TRANSACTION_NO) ?: ""
        val terminalId = intent.getStringExtra(EXTRA_TERMINAL_ID) ?: ""

        val loading = findViewById<View>(R.id.detail_loading)
        val content = findViewById<View>(R.id.detail_content)
        val empty = findViewById<TextView>(R.id.detail_empty)
        val cancelButton = findViewById<android.widget.Button>(R.id.detail_cancel)
        val closeButton = findViewById<android.widget.Button>(R.id.detail_close)

        if (transactionNo.isBlank() || terminalId.isBlank()) {
            loading.visibility = View.GONE
            content.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            val detail = runCatching {
                TransactionsRepository(this@TransactionDetailActivity)
                    .fetchDetail(transactionNo, terminalId)
            }.getOrNull()

            loading.visibility = View.GONE
            if (detail == null) {
                content.visibility = View.GONE
                empty.visibility = View.VISIBLE
                return@launch
            }
            empty.visibility = View.GONE
            content.visibility = View.VISIBLE

            findViewById<TextView>(R.id.detail_date).text = detail.transactionDate
            findViewById<TextView>(R.id.detail_product).text = detail.product
            findViewById<TextView>(R.id.detail_amount).text = detail.amount
            findViewById<TextView>(R.id.detail_paytype).text = detail.payType
            findViewById<TextView>(R.id.detail_paystep).text = detail.payStep
            if (detail.payStep == "구매" || detail.payStep == "매입") {
                cancelButton.visibility = View.VISIBLE
                cancelButton.setOnClickListener {
                    val amount = detail.amount.filter { ch -> ch.isDigit() }
                    if (amount.isBlank()) {
                        android.widget.Toast.makeText(this@TransactionDetailActivity, "취소 금액 파싱 실패", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val userId = AuthStore(this@TransactionDetailActivity).getId().orEmpty()
                    if (userId.isBlank()) {
                        android.widget.Toast.makeText(this@TransactionDetailActivity, "로그인 정보가 없습니다", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    android.app.AlertDialog.Builder(this@TransactionDetailActivity)
                        .setTitle("승인취소")
                        .setMessage("해당 거래를 취소하시겠습니까?")
                        .setNegativeButton("닫기", null)
                        .setPositiveButton("승인취소") { _, _ ->
                            cancelButton.isEnabled = false
                            lifecycleScope.launch {
                                val result = TransactionsRepository(this@TransactionDetailActivity)
                                    .cancelTransaction(
                                        transactionNo = transactionNo,
                                        terminalId = detail.terminalId,
                                        userId = userId,
                                        cancelAmount = amount
                                    )
                                cancelButton.isEnabled = true
                                val msg = if (result.description.isNotBlank()) {
                                    "${result.message}\n${result.description}"
                                } else {
                                    result.message
                                }
                                android.widget.Toast.makeText(this@TransactionDetailActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                if (result.isSuccess) {
                                    finish()
                                }
                            }
                        }
                        .show()
                }
            } else {
                cancelButton.visibility = View.GONE
            }
            findViewById<TextView>(R.id.detail_card).text = detail.cardNo
            findViewById<TextView>(R.id.detail_approval).text = detail.approvalNo
            findViewById<TextView>(R.id.detail_terminal).text = detail.terminalId
            findViewById<TextView>(R.id.detail_col).text = detail.colNo
        }

        closeButton.setOnClickListener { finish() }
    }


    companion object {
        const val EXTRA_TRANSACTION_NO = "extra_transaction_no"
        const val EXTRA_TERMINAL_ID = "extra_terminal_id"
    }
}
