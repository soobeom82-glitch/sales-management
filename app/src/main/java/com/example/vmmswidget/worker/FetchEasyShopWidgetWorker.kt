package com.example.vmmswidget.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.db.EasyShopSalesEntity
import com.example.vmmswidget.net.EasyShopRepository
import com.example.vmmswidget.widget.EasyShopWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class FetchEasyShopWidgetWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        if (!forceRefresh && isQuietHours()) {
            return@withContext Result.success()
        }

        val auth = AuthStore(appContext)
        val id = auth.getEasyShopId().orEmpty().trim()
        val password = auth.getEasyShopPassword().orEmpty().trim()
        if (id.isBlank() || password.isBlank()) {
            saveAndUpdate(
                text = "로그인 정보가 없습니다. 앱에서 입력하세요.",
                salesAmount = 0,
                depositAmount = 0,
                hasCanceledToday = false,
                canceledTodayCount = 0
            )
            return@withContext Result.success()
        }

        return@withContext try {
            val today = LocalDate.now().toString()
            val repo = EasyShopRepository(appContext)
            val result = repo.fetchTodaySales()
            if (!result.success) {
                saveAndUpdate(
                    text = "조회 실패: ${result.message}",
                    salesAmount = 0,
                    depositAmount = 0,
                    hasCanceledToday = false,
                    canceledTodayCount = 0
                )
                Result.success()
            } else {
                val canceledTodayCount = result.records.count {
                    it.isCanceled && (it.transactionDate.isBlank() || it.transactionDate == today)
                }
                val canceledTodayExists = canceledTodayCount > 0
                val nonCanceled = result.records.filterNot { it.isCanceled }
                val todayRows = nonCanceled.filter {
                    it.transactionDate.isBlank() || it.transactionDate == today
                }
                val sourceRows = if (todayRows.isEmpty()) nonCanceled else todayRows
                val total = sourceRows.sumOf { it.amount }
                val depositResult = repo.fetchTodayDepositAmount()
                val todayDate = LocalDate.parse(today)
                val depositAmount = upsertTodaySnapshot(
                    date = todayDate,
                    salesAmount = total,
                    depositAmountFromApi = if (depositResult.success) depositResult.amount else null
                )
                saveAndUpdate(
                    text = "매출 ${String.format("%,d", total)}원\n입금 ${String.format("%,d", depositAmount)}원",
                    salesAmount = total,
                    depositAmount = depositAmount,
                    hasCanceledToday = canceledTodayExists,
                    canceledTodayCount = canceledTodayCount
                )
                Result.success()
            }
        } catch (e: Exception) {
            Log.w("Vmms", "EasyShop widget fetch failed", e)
            saveAndUpdate(
                text = "조회 실패",
                salesAmount = 0,
                depositAmount = 0,
                hasCanceledToday = false,
                canceledTodayCount = 0
            )
            Result.success()
        }
    }

    private fun saveAndUpdate(
        text: String,
        salesAmount: Int,
        depositAmount: Int,
        hasCanceledToday: Boolean,
        canceledTodayCount: Int
    ) {
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        WidgetDataStore(appContext).apply {
            saveEasyShopDisplayText(text)
            saveEasyShopAmount(salesAmount)
            saveEasyShopDepositAmount(depositAmount)
            saveEasyShopUpdatedAt(now)
            saveEasyShopRefreshing(false)
            saveEasyShopHasCanceledToday(hasCanceledToday)
            saveEasyShopCanceledTodayCount(canceledTodayCount)
        }
        EasyShopWidgetUpdater.updateAll(appContext)
    }

    private fun isQuietHours(): Boolean {
        val now = LocalTime.now()
        val start = LocalTime.of(0, 0)
        val end = LocalTime.of(6, 0)
        return !now.isBefore(start) && now.isBefore(end)
    }

    private suspend fun upsertTodaySnapshot(
        date: LocalDate,
        salesAmount: Int,
        depositAmountFromApi: Int?
    ): Int {
        val dao = AppDatabase.get(appContext).easyShopSalesDao()
        val key = date.toString()
        val existing = dao.getByDate(key)
        val finalDeposit = depositAmountFromApi ?: existing?.depositAmount ?: 0

        dao.upsert(
            EasyShopSalesEntity(
                date = key,
                amount = salesAmount,
                depositAmount = finalDeposit,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.deleteOlderThan(date.minusDays(365).toString())

        Log.i(
            "Vmms",
            "EasyShop widget snapshot upsert: date=$key, sales=$salesAmount, deposit=$finalDeposit, depositFromApi=${depositAmountFromApi != null}"
        )

        return finalDeposit
    }

    companion object {
        const val KEY_FORCE_REFRESH = "force_refresh"
    }
}
