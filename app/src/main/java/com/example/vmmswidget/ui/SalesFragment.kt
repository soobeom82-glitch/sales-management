package com.example.vmmswidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vmmswidget.R
import com.example.vmmswidget.net.TransactionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vmmswidget.data.db.AppDatabase

class SalesFragment : Fragment() {
    private var lastDates: List<LocalDate> = emptyList()
    private var lastValues: List<Int> = emptyList()
    private var lastDiff: Int = 0
    private var lastTodayTotal: Int = 0
    private var lastTodayShares: List<TransactionsRepository.TodayItemShare> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sales, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val chart = view.findViewById<SalesChartView>(R.id.sales_chart)
        val pieChart = view.findViewById<TodayPieChartView>(R.id.today_pie_chart)
        val rangeText = view.findViewById<android.widget.TextView>(R.id.text_range)
        val todayPieDate = view.findViewById<android.widget.TextView>(R.id.text_today_pie_date)
        val totalText = view.findViewById<android.widget.TextView>(R.id.text_total)
        todayPieDate.text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        chart.setOnClickListener { showDailySalesDialog() }
        fun playAnimation() {
            if (lastDates.isEmpty() || lastValues.isEmpty()) return
            val total = lastValues.sum()
            chart.setData(lastDates, lastValues, LocalDate.now())
            chart.setAnimationProgress(0f)
            pieChart.setData(lastTodayTotal, lastTodayShares)
            pieChart.setAnimationProgress(0f)

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    chart.setAnimationProgress(f)
                    pieChart.setAnimationProgress(f)

                    val animatedTotal = (total * f).toInt()
                    val animatedDiff = (kotlin.math.abs(lastDiff) * f).toInt()
                    val changeText: String
                    val changeColor: Int
                    if (lastDiff > 0) {
                        changeText = "▲${String.format("%,d", animatedDiff)}원"
                        changeColor = Color.parseColor("#E07A7A")
                    } else if (lastDiff < 0) {
                        changeText = "▼${String.format("%,d", animatedDiff)}원"
                        changeColor = Color.parseColor("#6FA3D6")
                    } else {
                        changeText = "▲0원"
                        changeColor = Color.parseColor("#888888")
                    }
                    val totalTextStr = "총 ${String.format("%,d", animatedTotal)} 원"
                    val spacer = "\u00A0\u00A0"
                    val full = "$totalTextStr$spacer$changeText"
                    val spannable = SpannableStringBuilder(full)
                    val changeStart = full.length - changeText.length
                    spannable.setSpan(
                        ForegroundColorSpan(changeColor),
                        changeStart,
                        full.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RelativeSizeSpan(0.7f),
                        changeStart,
                        full.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    totalText.setTextColor(Color.parseColor("#333333"))
                    totalText.text = spannable
                }
            }
            animator.start()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (allRecords, pieData) = withContext(Dispatchers.IO) {
                val repo = TransactionsRepository(requireContext())
                val dao = AppDatabase.get(requireContext()).salesDao()
                val dbAll = dao.getAll()
                val pie = repo.fetchTodayPieData()
                Pair(dbAll, pie)
            }
            val amountByDate = allRecords.associate { it.date to it.amount }
            val today = LocalDate.now()
            val currentDates = (7L downTo 1L).map { today.minusDays(it) }
            val prevDates = (14L downTo 8L).map { today.minusDays(it) }
            val dates = currentDates
            val values = currentDates.map { d -> amountByDate[d.toString()] ?: 0 }

            if (dates.isNotEmpty()) {
                val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                val range = "${dates.first().format(fmt)} - ${dates.last().format(fmt)}"
                rangeText.text = range
                val total = values.sum()
                val prevTotal = prevDates.sumOf { d -> amountByDate[d.toString()] ?: 0 }
                val diff = total - prevTotal
                lastDates = dates
                lastValues = values
                lastDiff = diff
                lastTodayTotal = pieData.totalAmount
                lastTodayShares = pieData.shares
                playAnimation()
            } else {
                rangeText.text = "--"
                totalText.text = "총 0 원"
                pieChart.setData(pieData.totalAmount, pieData.shares)
                lastTodayTotal = pieData.totalAmount
                lastTodayShares = pieData.shares
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val chart = view?.findViewById<SalesChartView>(R.id.sales_chart) ?: return
        val pieChart = view?.findViewById<TodayPieChartView>(R.id.today_pie_chart) ?: return
        val totalText = view?.findViewById<android.widget.TextView>(R.id.text_total) ?: return
        // Replay animation with last data when returning to tab.
        if (lastDates.isNotEmpty() && lastValues.isNotEmpty()) {
            val total = lastValues.sum()
            chart.setData(lastDates, lastValues, LocalDate.now())
            chart.setAnimationProgress(0f)
            pieChart.setData(lastTodayTotal, lastTodayShares)
            pieChart.setAnimationProgress(0f)
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    chart.setAnimationProgress(f)
                    pieChart.setAnimationProgress(f)
                    val animatedTotal = (total * f).toInt()
                    val animatedDiff = (kotlin.math.abs(lastDiff) * f).toInt()
                    val changeText: String
                    val changeColor: Int
                    if (lastDiff > 0) {
                        changeText = "▲${String.format("%,d", animatedDiff)}원"
                        changeColor = Color.parseColor("#E07A7A")
                    } else if (lastDiff < 0) {
                        changeText = "▼${String.format("%,d", animatedDiff)}원"
                        changeColor = Color.parseColor("#6FA3D6")
                    } else {
                        changeText = "▲0원"
                        changeColor = Color.parseColor("#888888")
                    }
                    val totalTextStr = "총 ${String.format("%,d", animatedTotal)} 원"
                    val spacer = "\u00A0\u00A0"
                    val full = "$totalTextStr$spacer$changeText"
                    val spannable = SpannableStringBuilder(full)
                    val changeStart = full.length - changeText.length
                    spannable.setSpan(
                        ForegroundColorSpan(changeColor),
                        changeStart,
                        full.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RelativeSizeSpan(0.7f),
                        changeStart,
                        full.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    totalText.setTextColor(Color.parseColor("#333333"))
                    totalText.text = spannable
                }
            }
            animator.start()
        }
    }

    private fun showDailySalesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_sales, null, false)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_daily_sales)
        val emptyText = dialogView.findViewById<android.widget.TextView>(R.id.text_daily_sales_empty)
        val close = dialogView.findViewById<android.widget.Button>(R.id.button_daily_sales_close)
        val adapter = DailySalesAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        close.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).salesDao().getAll()
            }.sortedByDescending { it.date }
            adapter.submit(all)
            emptyText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        }
        dialog.show()
    }
}
