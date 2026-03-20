package com.example.vmmswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.vmmswidget.R
import com.example.vmmswidget.data.HolidayCalendar
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.WidgetDataStore
import com.example.vmmswidget.ui.MainActivity

object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, VmmsWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        update(context, manager, appWidgetIds)
    }

    fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val dataStore = WidgetDataStore(context)
        val text = dataStore.getDisplayText()
        val amount = dataStore.getAmount()
        val updatedAt = formatUpdatedAt(dataStore.getUpdatedAt())
        val refreshing = dataStore.isRefreshing()
        val bgRes = pickBackgroundRes(amount)
        val history = kotlinx.coroutines.runBlocking {
            val today = java.time.LocalDate.now().toString()
            AppDatabase.get(context).salesDao().getLastDaysExcludingToday(today, 14).asReversed()
        }
        val orderCount = kotlinx.coroutines.runBlocking {
            AppDatabase.get(context).orderDao().getPlannedItems().size
        }
        val orderBadgeBgRes = if (isOrderCriticalWindow(java.time.LocalDateTime.now())) {
            R.drawable.widget_order_badge_red_bg
        } else {
            R.drawable.widget_order_badge_gray_bg
        }
        val current7 = history.takeLast(7)
        val prev7 = history.dropLast(7).takeLast(7)
        val dates = history.map { java.time.LocalDate.parse(it.date) }
        val holidays = HolidayCalendar.holidaysForYears(dates.map { it.year }.toSet())
        val chart = ChartRenderer.render(
            current7.map { java.time.LocalDate.parse(it.date) },
            current7.map { it.amount },
            holidays,
            dpToPx(context, 120),
            dpToPx(context, 48)
        )
        val avg = if (current7.isNotEmpty()) current7.sumOf { it.amount } / current7.size else 0
        val prevAvg = if (prev7.isNotEmpty()) prev7.sumOf { it.amount } / prev7.size else 0
        val changeText: String
        val changeColor: Int
        if (prevAvg > 0) {
            val pct = (avg - prevAvg).toDouble() / prevAvg.toDouble() * 100.0
            changeText = if (pct > 0) {
                String.format("▲%.2f%%", pct)
            } else if (pct < 0) {
                String.format("▼%.2f%%", kotlin.math.abs(pct))
            } else {
                "▲0.00%"
            }
            changeColor = if (pct > 0) {
                Color.parseColor("#E07A7A")
            } else if (pct < 0) {
                Color.parseColor("#6FA3D6")
            } else {
                Color.parseColor("#888888")
            }
        } else {
            changeText = "▲0.00%"
            changeColor = Color.parseColor("#888888")
        }
        val avgText = "avg. ${String.format("%,d", avg)}원"

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_vmms)
            views.setTextViewText(R.id.widget_title, "VMMS 최신")
            views.setTextViewText(R.id.widget_content, text)
            views.setTextViewText(R.id.widget_sales_label, "매출")
            views.setTextViewText(R.id.widget_deposit_label, "입금")
            views.setTextViewText(R.id.widget_sales_amount, "${String.format("%,d", amount)}원")
            views.setTextViewText(R.id.widget_deposit_amount, "-")
            views.setTextViewText(R.id.widget_updated, updatedAt)
            views.setInt(R.id.widget_container, "setBackgroundResource", bgRes)
            views.setImageViewBitmap(R.id.widget_chart, chart)
            views.setViewVisibility(R.id.widget_content, View.GONE)
            views.setViewVisibility(R.id.widget_easyshop_amount_block, View.VISIBLE)
            views.setViewVisibility(R.id.widget_deposit_row, View.GONE)
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
            views.setTextColor(R.id.widget_avg, Color.parseColor("#334155"))
            views.setTextViewText(R.id.widget_avg, spannable)

            val refreshIntent = Intent(context, VmmsWidgetProvider::class.java).apply {
                action = VmmsWidgetProvider.ACTION_REFRESH
            }

            val pending = android.app.PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_refresh, pending)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TAB_INDEX, 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = android.app.PendingIntent.getActivity(
                context,
                1,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_chart, openPending)

            val openTransactions = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TAB_INDEX, 1)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openTransactionsPending = android.app.PendingIntent.getActivity(
                context,
                2,
                openTransactions,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_content, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_easyshop_amount_block, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_sales_label, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_sales_amount, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_deposit_label, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_deposit_amount, openTransactionsPending)
            views.setOnClickPendingIntent(R.id.widget_title, openTransactionsPending)

            val openOrder = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TAB_INDEX, 2)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openOrderPending = android.app.PendingIntent.getActivity(
                context,
                3,
                openOrder,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setTextViewText(R.id.widget_order_badge, orderCount.toString())
            views.setInt(R.id.widget_order_badge, "setBackgroundResource", orderBadgeBgRes)
            views.setOnClickPendingIntent(R.id.widget_order_badge, openOrderPending)
            views.setOnClickPendingIntent(R.id.widget_order_label, openOrderPending)
            manager.updateAppWidget(id, views)
        }
    }

    private fun isOrderCriticalWindow(now: java.time.LocalDateTime): Boolean {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        return (day == java.time.DayOfWeek.MONDAY && !time.isBefore(java.time.LocalTime.of(19, 0))) ||
            (day == java.time.DayOfWeek.TUESDAY && time.isBefore(java.time.LocalTime.of(10, 0))) ||
            (day == java.time.DayOfWeek.FRIDAY && !time.isBefore(java.time.LocalTime.of(19, 0))) ||
            (day == java.time.DayOfWeek.SATURDAY && time.isBefore(java.time.LocalTime.of(10, 0)))
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun formatUpdatedAt(raw: String): String {
        val value = raw.trim()
        if (value.length >= 5 && value[2] == ':') {
            return value.substring(0, 5)
        }
        return value
    }

    // no debug footer text

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
