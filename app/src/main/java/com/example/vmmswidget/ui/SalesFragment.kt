package com.example.vmmswidget.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.net.EasyShopRepository
import com.example.vmmswidget.net.TransactionsRepository
import com.example.vmmswidget.widget.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SalesFragment : Fragment() {
    private var isVmmsTabSelected: Boolean = true

    // VMMS state
    private var vmmsDates: List<LocalDate> = emptyList()
    private var vmmsValues: List<Int> = emptyList()
    private var vmmsDiff: Int = 0
    private var vmmsTodayTotal: Int = 0
    private var vmmsTodayShares: List<TransactionsRepository.TodayItemShare> = emptyList()
    private var vmmsAnimator: ValueAnimator? = null
    private var dailySalesDialog: AlertDialog? = null
    private var dailySalesLoadJob: Job? = null

    // EasyShop state
    private var easyDates: List<LocalDate> = emptyList()
    private var easyValues: List<Int> = emptyList()
    private var easyDiff: Int = 0
    private var easyTodayTotal: Int = 0
    private var easyTodayShares: List<TransactionsRepository.TodayItemShare> = emptyList()
    private var easyAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sales, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindSubTabs(view)
        initHeaderDates(view)
        initClickActions(view)
        WorkScheduler.scheduleEasyShopDailyRecord(requireContext())
        loadVmmsData(view)
        loadEasyShopData(view)
    }

    private fun initHeaderDates(root: View) {
        val todayText = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        root.findViewById<TextView>(R.id.text_today_pie_date).text = todayText
        root.findViewById<TextView>(R.id.text_easyshop_pie_date).text = todayText
    }

    private fun initClickActions(root: View) {
        root.findViewById<SalesChartView>(R.id.sales_chart).setOnClickListener {
            showDailySalesDialog()
        }
    }

    private fun loadVmmsData(root: View) {
        val chart = root.findViewById<SalesChartView>(R.id.sales_chart)
        val pie = root.findViewById<TodayPieChartView>(R.id.today_pie_chart)
        val range = root.findViewById<TextView>(R.id.text_range)
        val total = root.findViewById<TextView>(R.id.text_total)

        viewLifecycleOwner.lifecycleScope.launch {
            val (allRecords, pieData) = withContext(Dispatchers.IO) {
                val repo = TransactionsRepository(requireContext())
                val dao = AppDatabase.get(requireContext()).salesDao()
                Pair(dao.getAll(), repo.fetchTodayPieData())
            }
            if (!isAdded) return@launch

            val amountByDate = allRecords.associate { it.date to it.amount }
            val today = LocalDate.now()
            vmmsDates = (7L downTo 1L).map { today.minusDays(it) }
            val prevDates = (14L downTo 8L).map { today.minusDays(it) }
            vmmsValues = vmmsDates.map { d -> amountByDate[d.toString()] ?: 0 }
            val vmmsTotal = vmmsValues.sum()
            val prevTotal = prevDates.sumOf { d -> amountByDate[d.toString()] ?: 0 }
            vmmsDiff = vmmsTotal - prevTotal
            vmmsTodayTotal = pieData.totalAmount
            vmmsTodayShares = pieData.shares

            if (vmmsDates.isNotEmpty()) {
                val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                range.text = "${vmmsDates.first().format(fmt)} - ${vmmsDates.last().format(fmt)}"
            } else {
                range.text = "--"
            }
            chart.setData(vmmsDates, vmmsValues, LocalDate.now())
            chart.setAnimationProgress(0f)
            pie.setData(vmmsTodayTotal, vmmsTodayShares)
            pie.setAnimationProgress(0f)
            vmmsAnimator?.cancel()
            vmmsAnimator = startAnimation(
                chart = chart,
                pieChart = pie,
                totalText = total,
                totalAmount = vmmsTotal,
                diff = vmmsDiff
            )
        }
    }

    private fun loadEasyShopData(root: View) {
        val chart = root.findViewById<SalesChartView>(R.id.easyshop_sales_chart)
        val pie = root.findViewById<TodayPieChartView>(R.id.easyshop_pie_chart)
        val range = root.findViewById<TextView>(R.id.text_easyshop_range)
        val total = root.findViewById<TextView>(R.id.text_easyshop_total)
        val empty = root.findViewById<TextView>(R.id.text_easyshop_empty)

        viewLifecycleOwner.lifecycleScope.launch {
            val today = LocalDate.now()
            val appContext = requireContext().applicationContext
            val (historyRows, todayResult) = withContext(Dispatchers.IO) {
                val repo = EasyShopRepository(appContext)
                val dao = AppDatabase.get(appContext).easyShopSalesDao()
                val keepFrom = today.minusDays(14).toString()

                // 초기 비어있는 구간은 한 번에 보정해 15일(오늘 제외) 데이터를 채운다.
                var history = dao.getAll()
                if (history.size < 14) {
                    val backfillResult = repo.fetchSales(
                        from = today.minusDays(14),
                        to = today.minusDays(1)
                    )
                    if (backfillResult.success) {
                        val amountByDate = backfillResult.records
                            .filterNot { it.isCanceled }
                            .filter { it.transactionDate.isNotBlank() }
                            .groupBy { it.transactionDate }
                            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
                        val now = System.currentTimeMillis()
                        amountByDate.forEach { (date, amount) ->
                            dao.upsert(
                                com.example.vmmswidget.data.db.EasyShopSalesEntity(
                                    date = date,
                                    amount = amount,
                                    createdAt = now
                                )
                            )
                        }
                    }
                }

                dao.deleteOlderThan(keepFrom)
                history = dao.getAll()
                Pair(history, repo.fetchTodaySales())
            }
            if (!isAdded) return@launch

            val amountByDate = historyRows.associate { it.date to it.amount }

            easyDates = (7L downTo 1L).map { today.minusDays(it) }
            val prevDates = (14L downTo 8L).map { today.minusDays(it) }
            easyValues = easyDates.map { d -> amountByDate[d.toString()] ?: 0 }

            val easyTotal = easyValues.sum()
            val easyPrevTotal = prevDates.sumOf { d -> amountByDate[d.toString()] ?: 0 }
            easyDiff = easyTotal - easyPrevTotal

            if (todayResult.success) {
                val nonCanceled = todayResult.records.filterNot { it.isCanceled }
                val todayRows = nonCanceled.filter {
                    it.transactionDate.isBlank() || it.transactionDate == today.toString()
                }
                val sourceRows = if (todayRows.isEmpty()) nonCanceled else todayRows
                val todayShares = sourceRows
                    .groupBy { row ->
                        row.issuerName.ifBlank {
                            row.cardType.ifBlank { "기타" }
                        }
                    }
                    .mapValues { (_, rows) -> rows.sumOf { it.amount } }
                    .entries
                    .sortedByDescending { it.value }
                    .map { TransactionsRepository.TodayItemShare(it.key, it.value) }
                easyTodayShares = todayShares
                easyTodayTotal = todayShares.sumOf { it.amount }
            } else {
                easyTodayShares = emptyList()
                easyTodayTotal = 0
            }

            val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
            range.text = if (easyDates.isNotEmpty()) {
                "${easyDates.first().format(fmt)} - ${easyDates.last().format(fmt)}"
            } else {
                "--"
            }

            empty.visibility = when {
                !todayResult.success -> View.VISIBLE
                easyTodayTotal == 0 && easyValues.sum() == 0 -> View.VISIBLE
                else -> View.GONE
            }
            if (empty.visibility == View.VISIBLE) {
                empty.text = if (!todayResult.success) {
                    "EasyShop 오늘 매출 조회 실패: ${todayResult.message}"
                } else {
                    "EasyShop 데이터가 없습니다."
                }
            }

            chart.setData(easyDates, easyValues, today)
            chart.setAnimationProgress(0f)
            pie.setData(easyTodayTotal, easyTodayShares)
            pie.setAnimationProgress(0f)
            easyAnimator?.cancel()
            easyAnimator = startAnimation(
                chart = chart,
                pieChart = pie,
                totalText = total,
                totalAmount = easyTotal,
                diff = easyDiff
            )
        }
    }

    private fun bindSubTabs(root: View) {
        val vmmsTab = root.findViewById<TextView>(R.id.tab_sales_vmms)
        val easyTab = root.findViewById<TextView>(R.id.tab_sales_easyshop)
        vmmsTab.setOnClickListener { setSubTab(root, true) }
        easyTab.setOnClickListener { setSubTab(root, false) }
        setSubTab(root, isVmmsTabSelected)
    }

    private fun setSubTab(root: View, showVmms: Boolean) {
        isVmmsTabSelected = showVmms
        val vmmsTab = root.findViewById<TextView>(R.id.tab_sales_vmms)
        val easyTab = root.findViewById<TextView>(R.id.tab_sales_easyshop)
        val vmmsContent = root.findViewById<View>(R.id.layout_vmms_content)
        val easyContent = root.findViewById<View>(R.id.layout_easyshop_content)

        vmmsContent.visibility = if (showVmms) View.VISIBLE else View.GONE
        easyContent.visibility = if (showVmms) View.GONE else View.VISIBLE

        if (showVmms) {
            vmmsTab.setBackgroundResource(R.drawable.order_subtab_selected_bg)
            vmmsTab.setTextColor(Color.parseColor("#1E4FA3"))
            easyTab.setBackgroundResource(android.R.color.transparent)
            easyTab.setTextColor(Color.parseColor("#6B7280"))
        } else {
            easyTab.setBackgroundResource(R.drawable.order_subtab_selected_bg)
            easyTab.setTextColor(Color.parseColor("#1E4FA3"))
            vmmsTab.setBackgroundResource(android.R.color.transparent)
            vmmsTab.setTextColor(Color.parseColor("#6B7280"))
        }
    }

    override fun onResume() {
        super.onResume()
        val root = view ?: return
        if (isVmmsTabSelected) {
            val chart = root.findViewById<SalesChartView>(R.id.sales_chart)
            val pie = root.findViewById<TodayPieChartView>(R.id.today_pie_chart)
            val total = root.findViewById<TextView>(R.id.text_total)
            if (vmmsDates.isNotEmpty()) {
                chart.setData(vmmsDates, vmmsValues, LocalDate.now())
                chart.setAnimationProgress(0f)
                pie.setData(vmmsTodayTotal, vmmsTodayShares)
                pie.setAnimationProgress(0f)
                vmmsAnimator?.cancel()
                vmmsAnimator = startAnimation(chart, pie, total, vmmsValues.sum(), vmmsDiff)
            }
        } else {
            val chart = root.findViewById<SalesChartView>(R.id.easyshop_sales_chart)
            val pie = root.findViewById<TodayPieChartView>(R.id.easyshop_pie_chart)
            val total = root.findViewById<TextView>(R.id.text_easyshop_total)
            if (easyDates.isNotEmpty()) {
                chart.setData(easyDates, easyValues, LocalDate.now())
                chart.setAnimationProgress(0f)
                pie.setData(easyTodayTotal, easyTodayShares)
                pie.setAnimationProgress(0f)
                easyAnimator?.cancel()
                easyAnimator = startAnimation(chart, pie, total, easyValues.sum(), easyDiff)
            }
        }
    }

    private fun showDailySalesDialog() {
        if (dailySalesDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_sales, null, false)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_daily_sales)
        val emptyText = dialogView.findViewById<TextView>(R.id.text_daily_sales_empty)
        val close = dialogView.findViewById<android.widget.Button>(R.id.button_daily_sales_close)
        val adapter = DailySalesAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dailySalesDialog = dialog
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            dailySalesLoadJob?.cancel()
            dailySalesLoadJob = null
            if (dailySalesDialog === dialog) dailySalesDialog = null
        }

        dailySalesLoadJob?.cancel()
        dailySalesLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).salesDao().getAll()
            }.sortedByDescending { it.date }
            if (!dialog.isShowing) return@launch
            adapter.submit(all)
            emptyText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        }
        dialog.show()
    }

    private fun startAnimation(
        chart: SalesChartView,
        pieChart: TodayPieChartView,
        totalText: TextView,
        totalAmount: Int,
        diff: Int
    ): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                chart.setAnimationProgress(f)
                pieChart.setAnimationProgress(f)
                val animatedTotal = (totalAmount * f).toInt()
                val animatedDiff = (kotlin.math.abs(diff) * f).toInt()
                totalText.text = makeTotalText(animatedTotal, animatedDiff, diff)
            }
            start()
        }
    }

    private fun makeTotalText(animatedTotal: Int, animatedDiff: Int, diff: Int): CharSequence {
        val changeText: String
        val changeColor: Int
        if (diff > 0) {
            changeText = "▲${String.format("%,d", animatedDiff)}원"
            changeColor = Color.parseColor("#E07A7A")
        } else if (diff < 0) {
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
        return spannable
    }

    override fun onStop() {
        vmmsAnimator?.cancel()
        easyAnimator?.cancel()
        vmmsAnimator = null
        easyAnimator = null
        super.onStop()
    }

    override fun onDestroyView() {
        vmmsAnimator?.cancel()
        easyAnimator?.cancel()
        vmmsAnimator = null
        easyAnimator = null
        dailySalesLoadJob?.cancel()
        dailySalesLoadJob = null
        dailySalesDialog?.dismiss()
        dailySalesDialog = null
        super.onDestroyView()
    }
}
