package com.example.vmmswidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.example.vmmswidget.worker.FetchWidgetWorker
import com.example.vmmswidget.worker.EasyShopDailySnapshotWorker
import com.example.vmmswidget.worker.FetchEasyShopWidgetWorker
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

object WorkScheduler {
    private const val PERIODIC_WORK_NAME = "vmms_periodic_refresh"
    private const val EASYSHOP_PERIODIC_WORK_NAME = "easyshop_periodic_refresh"
    private const val DAILY_WORK_NAME = "vmms_daily_record"
    private const val EASYSHOP_DAILY_WORK_NAME = "easyshop_daily_record"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FetchWidgetWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(FetchWidgetWorker.KEY_FORCE_REFRESH to false))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleEasyShopPeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FetchEasyShopWidgetWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(FetchEasyShopWidgetWorker.KEY_FORCE_REFRESH to false))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EASYSHOP_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleDailyRecord(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val delay = nextDelayTo(LocalTime.of(23, 59, 59))
        val request = OneTimeWorkRequestBuilder<FetchWidgetWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FetchWidgetWorker.KEY_FORCE_REFRESH to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        scheduleEasyShopDailyRecord(context)
    }

    fun scheduleEasyShopDailyRecord(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val delay = nextDelayTo(LocalTime.of(23, 59, 59))
        val request = OneTimeWorkRequestBuilder<EasyShopDailySnapshotWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EASYSHOP_DAILY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun nextDelayTo(target: LocalTime): Duration {
        val now = LocalDateTime.now()
        val todayTarget = now.toLocalDate().atTime(target)
        val next = if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
        return Duration.between(now, next)
    }
}
