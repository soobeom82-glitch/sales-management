package com.example.vmmswidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.worker.FetchEasyShopWidgetWorker

class EasyShopWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        EasyShopWidgetUpdater.update(context, appWidgetManager, appWidgetIds)
        WorkScheduler.scheduleEasyShopPeriodic(context)
        WorkScheduler.scheduleEasyShopDailyRecord(context)
        enqueueRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetDataStore(context).saveEasyShopRefreshing(true)
            EasyShopWidgetUpdater.updateAll(context)
            WorkScheduler.scheduleEasyShopPeriodic(context)
            WorkScheduler.scheduleEasyShopDailyRecord(context)
            enqueueRefresh(context)
        }
    }

    private fun enqueueRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<FetchEasyShopWidgetWorker>()
            .setInputData(workDataOf(FetchEasyShopWidgetWorker.KEY_FORCE_REFRESH to true))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val ACTION_REFRESH = "com.example.vmmswidget.ACTION_EASYSHOP_REFRESH"
    }
}
