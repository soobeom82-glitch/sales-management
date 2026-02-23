package com.example.vmmswidget.net

data class TransactionDetail(
    val transactionDate: String,
    val product: String,
    val amount: String,
    val payType: String,
    val payStep: String,
    val cardNo: String,
    val approvalNo: String,
    val terminalId: String,
    val colNo: String,
    val company: String,
    val organ: String,
    val place: String
)
