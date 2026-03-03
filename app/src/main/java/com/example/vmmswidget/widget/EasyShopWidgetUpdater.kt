package com.example.vmmswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.vmmswidget.R
import com.example.vmmswidget.data.HolidayCalendar
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.ui.MainActivity
import java.time.LocalDate

object EasyShopWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, EasyShopWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        update(context, manager, appWidgetIds)
    }

    fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val dataStore = WidgetDataStore(context)
        val salesAmount = dataStore.getEasyShopAmount()
        val depositAmount = dataStore.getEasyShopDepositAmount()
        val updatedAt = dataStore.getEasyShopUpdatedAt()
        val refreshing = dataStore.isEasyShopRefreshing()
        val hasCanceledToday = dataStore.hasEasyShopCanceledToday()
        val canceledTodayCount = dataStore.getEasyShopCanceledTodayCount()
        val bgRes = if (hasCanceledToday) {
            R.drawable.widget_bg_cancel
        } else {
            pickBackgroundRes(salesAmount)
        }
        val history = kotlinx.coroutines.runBlocking {
            val today = LocalDate.now().toString()
            AppDatabase.get(context).easyShopSalesDao().getLastDaysExcludingToday(today, 14).asReversed()
        }
        val cancelBadgeBgRes = if (canceledTodayCount > 0) {
            R.drawable.widget_order_badge_red_bg
        } else {
            R.drawable.widget_order_badge_gray_bg
        }
        val current7 = history.takeLast(7)
        val prev7 = history.dropLast(7).takeLast(7)
        val dates = history.map { LocalDate.parse(it.date) }
        val holidays = HolidayCalendar.holidaysForYears(dates.map { it.year }.toSet())
        val chart = ChartRenderer.render(
            current7.map { LocalDate.parse(it.date) },
            current7.map { it.amount },
            holidays,
            dpToPx(context, 120),
            dpToPx(context, 48)
        )
        val avg = if (current7.isNotEmpty()) current7.sumOf { it.amount } / current7.size else 0
        val prevAvg = if (prev7.isNotEmpty()) prev7.sumOf { it.amount } / prev7.size else 0
        val (changeText, changeColor) = calcChange(avg, prevAvg)
        val avgText = "avg. ${String.format("%,d", avg)}원"

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_vmms)
            views.setTextViewText(R.id.widget_title, "EasyShop 최신")
            views.setTextViewText(R.id.widget_sales_label, "매출")
            views.setTextViewText(R.id.widget_deposit_label, "입금")
            views.setTextViewText(R.id.widget_sales_amount, "${String.format("%,d", salesAmount)}원")
            views.setTextViewText(R.id.widget_deposit_amount, "${String.format("%,d", depositAmount)}원")
            views.setTextViewText(R.id.widget_updated, updatedAt)
            views.setInt(R.id.widget_container, "setBackgroundResource", bgRes)
            views.setImageViewBitmap(R.id.widget_chart, chart)
            views.setViewVisibility(R.id.widget_content, View.GONE)
            views.setViewVisibility(R.id.widget_easyshop_amount_block, View.VISIBLE)
            views.setViewVisibility(R.id.widget_deposit_row, View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress, if (refreshing) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_avg, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_title, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_content, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_sales_label, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_sales_amount, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_deposit_label, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_deposit_amount, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_order_label, Color.parseColor("#334155"))
            views.setTextColor(R.id.widget_updated, Color.parseColor("#334155"))
            views.setTextViewText(R.id.widget_order_label, "취소")
            val spacer = "\u00A0\u00A0"
            val fullText = "$avgText$spacer$changeText"
            val spannable = android.text.SpannableStringBuilder(fullText)
            val changeStart = fullText.length - changeText.length
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(changeColor),
                changeStart,
                fullText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            views.setTextViewText(R.id.widget_avg, spannable)

            val refreshIntent = Intent(context, EasyShopWidgetProvider::class.java).apply {
                action = EasyShopWidgetProvider.ACTION_REFRESH
            }
            val refreshPending = android.app.PendingIntent.getBroadcast(
                context,
                100,
                refreshIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            val openSales = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TAB_INDEX, 0)
                putExtra(MainActivity.EXTRA_SALES_SUBTAB, MainActivity.SALES_SUBTAB_EASYSHOP)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openSalesPending = android.app.PendingIntent.getActivity(
                context,
                101,
                openSales,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_chart, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_title, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_content, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_easyshop_amount_block, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_sales_label, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_sales_amount, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_deposit_label, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_deposit_amount, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_avg, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_updated, openSalesPending)

            views.setTextViewText(R.id.widget_order_badge, canceledTodayCount.toString())
            views.setInt(R.id.widget_order_badge, "setBackgroundResource", cancelBadgeBgRes)
            views.setOnClickPendingIntent(R.id.widget_order_badge, openSalesPending)
            views.setOnClickPendingIntent(R.id.widget_order_label, openSalesPending)
            manager.updateAppWidget(id, views)
        }
    }

    private fun calcChange(avg: Int, prevAvg: Int): Pair<String, Int> {
        if (prevAvg <= 0) return "▲0.00%" to Color.parseColor("#888888")
        val pct = (avg - prevAvg).toDouble() / prevAvg.toDouble() * 100.0
        return when {
            pct > 0 -> String.format("▲%.2f%%", pct) to Color.parseColor("#E07A7A")
            pct < 0 -> String.format("▼%.2f%%", kotlin.math.abs(pct)) to Color.parseColor("#6FA3D6")
            else -> "▲0.00%" to Color.parseColor("#888888")
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun pickBackgroundRes(amount: Int): Int {
        val clamped = amount.coerceAtLeast(0).coerceAtMost(100_000)
        val step = (clamped / 20_000)
        return when (step) {
            0 -> R.drawable.widget_bg_0
            1 -> R.drawable.widget_bg_20
            2 -> R.drawable.widget_bg_40
            3 -> R.drawable.widget_bg_60
            4 -> R.drawable.widget_bg_80
            else -> R.drawable.widget_bg_100
        }
    }
}
