package com.example.vmmswidget.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vmmswidget.data.AuthStore
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.db.EasyShopSalesEntity
import com.example.vmmswidget.net.EasyShopRepository
import com.example.vmmswidget.widget.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class EasyShopDailySnapshotWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val auth = AuthStore(appContext)
            val id = auth.getEasyShopId().orEmpty().trim()
            val pw = auth.getEasyShopPassword().orEmpty().trim()

            if (id.isBlank() || pw.isBlank()) {
                Log.i("Vmms", "EasyShop daily snapshot skipped: empty credentials")
                WorkScheduler.scheduleEasyShopDailyRecord(appContext)
                return@withContext Result.success()
            }

            val today = LocalDate.now()
            val result = EasyShopRepository(appContext).fetchTodaySales()
            if (!result.success) {
                Log.w("Vmms", "EasyShop daily snapshot failed: ${result.message}")
                WorkScheduler.scheduleEasyShopDailyRecord(appContext)
                return@withContext Result.success()
            }

            val todayTotal = result.records
                .filterNot { it.isCanceled }
                .filter { it.transactionDate.isBlank() || it.transactionDate == today.toString() }
                .sumOf { it.amount }

            val dao = AppDatabase.get(appContext).easyShopSalesDao()
            dao.upsert(
                EasyShopSalesEntity(
                    date = today.toString(),
                    amount = todayTotal,
                    createdAt = System.currentTimeMillis()
                )
            )
            // 15일(오늘 포함)만 유지
            dao.deleteOlderThan(today.minusDays(14).toString())

            Log.i("Vmms", "EasyShop daily snapshot saved: ${today}=${todayTotal}")
            WorkScheduler.scheduleEasyShopDailyRecord(appContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("Vmms", "EasyShop daily snapshot unexpected error", e)
            WorkScheduler.scheduleEasyShopDailyRecord(appContext)
            Result.success()
        }
    }
}
