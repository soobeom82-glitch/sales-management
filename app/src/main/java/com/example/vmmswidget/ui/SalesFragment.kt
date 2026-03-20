package com.example.vmmswidget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
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
import com.example.vmmswidget.data.db.EasyShopSalesEntity
import com.example.vmmswidget.data.db.SalesEntity
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
    private var vmmsPieDate: LocalDate = LocalDate.now()
    private var vmmsPieTotal: Int = 0
    private var vmmsPieShares: List<TransactionsRepository.TodayItemShare> = emptyList()
    private var vmmsAnimator: ValueAnimator? = null
    private var vmmsPieAnimator: ValueAnimator? = null
    private var dailySalesDialog: AlertDialog? = null
    private var dailySalesLoadJob: Job? = null
    private var vmmsBackfillJob: Job? = null
    private var vmmsPieLoadJob: Job? = null
    private var easyDailySalesDialog: AlertDialog? = null
    private var easyDailySalesLoadJob: Job? = null

    // EasyShop state
    private var easyDates: List<LocalDate> = emptyList()
    private var easyValues: List<Int> = emptyList()
    private var easyDepositValues: List<Int> = emptyList()
    private var easyDiff: Int = 0
    private var easyTodayTotal: Int = 0
    private var easyTodayDeposit: Int = 0
    private var easyTopDate: LocalDate = LocalDate.now()
    private var easyTopSales: Int = 0
    private var easyTopDeposit: Int = 0
    private var easyTodayShares: List<TransactionsRepository.TodayItemShare> = emptyList()
    private var easyAnimator: ValueAnimator? = null
    private var easyBackfillJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sales, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindSubTabs(view)
        applySalesSubtabRouteIfAny(view)
        initHeaderDates(view)
        initClickActions(view)
        bindChartBackfillActions(view)
        WorkScheduler.scheduleEasyShopDailyRecord(requireContext())
        loadVmmsData(view)
        loadEasyShopData(view)
    }

    private fun bindChartBackfillActions(root: View) {
        root.findViewById<SalesChartView>(R.id.sales_chart)
            .setOnDateClickListener { date, salesAmount, _ ->
                selectVmmsPieDate(root, date)
                if (salesAmount == 0) {
                    backfillVmmsDate(root, date)
                }
            }

        root.findViewById<SalesChartView>(R.id.easyshop_sales_chart)
            .setOnDateClickListener { date, salesAmount, secondaryAmount ->
                selectEasyShopTopDate(root, date, salesAmount, secondaryAmount ?: 0)
                if (salesAmount == 0) {
                    backfillEasyShopDate(root, date)
                }
            }
    }

    private fun selectVmmsPieDate(root: View, date: LocalDate) {
        vmmsPieDate = date
        vmmsPieLoadJob?.cancel()
        val appContext = requireContext().applicationContext
        val pie = root.findViewById<TodayPieChartView>(R.id.today_pie_chart)
        val dateText = root.findViewById<TextView>(R.id.text_today_pie_date)
        vmmsPieLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val pieData = if (date == LocalDate.now() && vmmsTodayShares.isNotEmpty()) {
                TransactionsRepository.TodayPieData(vmmsTodayTotal, vmmsTodayShares)
            } else {
                withContext(Dispatchers.IO) {
                    TransactionsRepository(appContext).fetchPieDataForDate(date)
                }
            }
            if (!isAdded) return@launch
            vmmsPieTotal = pieData.totalAmount
            vmmsPieShares = pieData.shares
            dateText.text = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            pie.setData(vmmsPieTotal, vmmsPieShares)
            pie.setAnimationProgress(0f)
            vmmsPieAnimator?.cancel()
            vmmsPieAnimator = startPieOnlyAnimation(pie)
        }
    }

    private fun backfillVmmsDate(root: View, date: LocalDate) {
        vmmsBackfillJob?.cancel()
        val appContext = requireContext().applicationContext
        vmmsBackfillJob = viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val repo = TransactionsRepository(appContext)
                val daily = repo.fetchDailyTotalExcludingCanceled(date) ?: return@withContext false
                AppDatabase.get(appContext).salesDao().upsert(
                    SalesEntity(
                        date = daily.date,
                        amount = daily.amount,
                        createdAt = System.currentTimeMillis()
                    )
                )
                true
            }
            if (!isAdded || !updated) return@launch
            loadVmmsData(root)
        }
    }

    private fun backfillEasyShopDate(root: View, date: LocalDate) {
        easyBackfillJob?.cancel()
        val appContext = requireContext().applicationContext
        easyBackfillJob = viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val repo = EasyShopRepository(appContext)
                val dao = AppDatabase.get(appContext).easyShopSalesDao()

                val sales = repo.fetchSales(from = date, to = date)
                if (!sales.success) return@withContext false

                val amount = sumEasyShopSalesByDate(sales.records, date)
                val existing = dao.getByDate(date.toString())
                val deposit = repo.fetchDepositAmounts(date, date)
                val depositAmount = if (deposit.success) {
                    deposit.amountsByDate[date.toString()] ?: 0
                } else {
                    existing?.depositAmount ?: 0
                }

                dao.upsert(
                    EasyShopSalesEntity(
                        date = date.toString(),
                        amount = amount,
                        depositAmount = depositAmount,
                        createdAt = System.currentTimeMillis()
                    )
                )
                true
            }
            if (!isAdded || !updated) return@launch
            loadEasyShopData(root, animateOnRefresh = false)
        }
    }

    private fun selectEasyShopTopDate(root: View, date: LocalDate, salesAmount: Int, depositAmount: Int) {
        easyTopDate = date
        easyTopSales = salesAmount
        easyTopDeposit = depositAmount
        root.findViewById<TextView>(R.id.text_easyshop_pie_date).text =
            date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        val compare = root.findViewById<TodayCompareBarChartView>(R.id.easyshop_compare_chart)
        compare.setData(easyTopDeposit, easyTopSales)
        // Click interaction should only swap values, not replay bar animation.
        compare.setAnimationProgress(1f)
    }

    private fun initHeaderDates(root: View) {
        val todayText = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        root.findViewById<TextView>(R.id.text_today_pie_date).text = todayText
        root.findViewById<TextView>(R.id.text_easyshop_pie_date).text = todayText
    }

    private fun initClickActions(root: View) {
        root.findViewById<TextView>(R.id.text_total).setOnClickListener {
            showDailySalesDialog()
        }
        root.findViewById<TextView>(R.id.text_easyshop_total).setOnClickListener {
            showEasyShopDailySalesDialog()
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
            vmmsTodayTotal = pieData.totalAmount
            vmmsTodayShares = pieData.shares

            // 최근 7일: 오늘 포함 (D-6 ~ D)
            vmmsDates = (6L downTo 0L).map { today.minusDays(it) }
            // 이전 7일: D-13 ~ D-7
            val prevDates = (13L downTo 7L).map { today.minusDays(it) }
            vmmsValues = vmmsDates.map { d ->
                if (d == today) vmmsTodayTotal else (amountByDate[d.toString()] ?: 0)
            }
            val vmmsTotal = vmmsValues.sum()
            val prevTotal = prevDates.sumOf { d -> amountByDate[d.toString()] ?: 0 }
            vmmsDiff = vmmsTotal - prevTotal
            if (!vmmsDates.contains(vmmsPieDate)) {
                vmmsPieDate = today
            }
            val pieDataForDate = if (vmmsPieDate == today) {
                pieData
            } else {
                withContext(Dispatchers.IO) {
                    TransactionsRepository(requireContext().applicationContext)
                        .fetchPieDataForDate(vmmsPieDate)
                }
            }
            vmmsPieTotal = pieDataForDate.totalAmount
            vmmsPieShares = pieDataForDate.shares

            if (vmmsDates.isNotEmpty()) {
                val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                range.text = "${vmmsDates.first().format(fmt)} - ${vmmsDates.last().format(fmt)}"
            } else {
                range.text = "--"
            }
            root.findViewById<TextView>(R.id.text_today_pie_date).text =
                vmmsPieDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            chart.setData(vmmsDates, vmmsValues, LocalDate.now())
            chart.setAnimationProgress(0f)
            pie.setData(vmmsPieTotal, vmmsPieShares)
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

    private fun loadEasyShopData(root: View, animateOnRefresh: Boolean = true) {
        val chart = root.findViewById<SalesChartView>(R.id.easyshop_sales_chart)
        val compare = root.findViewById<TodayCompareBarChartView>(R.id.easyshop_compare_chart)
        val range = root.findViewById<TextView>(R.id.text_easyshop_range)
        val total = root.findViewById<TextView>(R.id.text_easyshop_total)
        val empty = root.findViewById<TextView>(R.id.text_easyshop_empty)

        viewLifecycleOwner.lifecycleScope.launch {
            val today = LocalDate.now()
            val appContext = requireContext().applicationContext
            val cachedRows = withContext(Dispatchers.IO) {
                AppDatabase.get(appContext).easyShopSalesDao().getAll()
            }
            if (!isAdded) return@launch
            bindEasyShopChart(
                today = today,
                historyRows = cachedRows,
                todayResult = null,
                todayDepositAmount = null,
                chart = chart,
                compare = compare,
                range = range,
                total = total,
                empty = empty,
                animate = false,
                showEmptyState = false
            )

            val (historyRows, todayResult, todayDepositAmount) = withContext(Dispatchers.IO) {
                syncEasyShopHistoryAndFetchToday(appContext, today)
            }
            if (!isAdded) return@launch
            bindEasyShopChart(
                today = today,
                historyRows = historyRows,
                todayResult = todayResult,
                todayDepositAmount = todayDepositAmount,
                chart = chart,
                compare = compare,
                range = range,
                total = total,
                empty = empty,
                animate = animateOnRefresh,
                showEmptyState = true
            )
        }
    }

    private suspend fun syncEasyShopHistoryAndFetchToday(
        appContext: Context,
        today: LocalDate
    ): Triple<List<EasyShopSalesEntity>, EasyShopRepository.SalesFetchResult, Int> {
        val repo = EasyShopRepository(appContext)
        val dao = AppDatabase.get(appContext).easyShopSalesDao()
        val keepFrom = today.minusDays(365).toString()
        val historyFrom = today.minusDays(14)
        val historyTo = today.minusDays(1)
        val targetHistoryDates = generateSequence(historyFrom) { prev ->
            if (prev.isBefore(historyTo)) prev.plusDays(1) else null
        }.toList()

        // 초기 비어있는 구간은 한 번에 보정해 15일(오늘 제외) 데이터를 채운다.
        var history = dao.getAll()
        if (history.size < 14) {
            val backfillResult = repo.fetchSales(
                from = historyFrom,
                to = historyTo
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
                        EasyShopSalesEntity(
                            date = date,
                            amount = amount,
                            createdAt = now
                        )
                    )
                }
            }
        }

        val depositBackfill = repo.fetchDepositAmounts(historyFrom, historyTo)
        if (depositBackfill.success && depositBackfill.amountsByDate.isNotEmpty()) {
            history = dao.getAll()
            val historyByDate = history.associateBy { it.date }
            val now = System.currentTimeMillis()
            depositBackfill.amountsByDate.forEach { (date, amount) ->
                val existing = historyByDate[date]
                dao.upsert(
                    EasyShopSalesEntity(
                        date = date,
                        amount = existing?.amount ?: 0,
                        depositAmount = amount,
                        createdAt = now
                    )
                )
            }
        }

        // 과거 데이터가 비어 있는 날짜는 "일자별 매출/입금 API"로만 채운다.
        history = dao.getAll()
        val existingDates = history.map { it.date }.toHashSet()
        val missingDates = targetHistoryDates.filterNot { existingDates.contains(it.toString()) }
        Log.i("Vmms", "EasyShop history missing dates before day-fill: ${missingDates.joinToString(",")}")

        missingDates.forEach { date ->
            val key = date.toString()
            val daySales = repo.fetchSales(
                from = date,
                to = date
            )
            if (!daySales.success) {
                Log.w(
                    "Vmms",
                    "EasyShop day-fill skipped (sales fail): $key, msg=${daySales.message}"
                )
                return@forEach
            }

            val dayAmount = sumEasyShopSalesByDate(daySales.records, date)
            val dayDepositAmount = if (depositBackfill.success) {
                depositBackfill.amountsByDate[key] ?: 0
            } else {
                val dayDeposit = repo.fetchDepositAmounts(date, date)
                if (!dayDeposit.success) {
                    Log.w(
                        "Vmms",
                        "EasyShop day-fill skipped (deposit fail): $key, msg=${dayDeposit.message}"
                    )
                    return@forEach
                }
                dayDeposit.amountsByDate[key] ?: 0
            }

            val entity = EasyShopSalesEntity(
                date = key,
                amount = dayAmount,
                depositAmount = dayDepositAmount,
                createdAt = System.currentTimeMillis()
            )
            dao.upsert(entity)
            existingDates.add(key)
            Log.i("Vmms", "EasyShop day-fill upsert: $key, sales=$dayAmount, deposit=$dayDepositAmount")
        }

        dao.deleteOlderThan(keepFrom)
        history = dao.getAll()

        val todayDeposit = repo.fetchTodayDepositAmount(today)
        val todayDepositAmount = if (todayDeposit.success) todayDeposit.amount else 0
        return Triple(history, repo.fetchTodaySales(), todayDepositAmount)
    }

    private fun bindEasyShopChart(
        today: LocalDate,
        historyRows: List<EasyShopSalesEntity>,
        todayResult: EasyShopRepository.SalesFetchResult?,
        todayDepositAmount: Int?,
        chart: SalesChartView,
        compare: TodayCompareBarChartView,
        range: TextView,
        total: TextView,
        empty: TextView,
        animate: Boolean,
        showEmptyState: Boolean
    ) {
        val amountByDate = historyRows.associate { it.date to it.amount }
        val depositByDate = historyRows.associate { it.date to it.depositAmount }

        if (todayResult != null) {
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
        } else {
            val todayFromDb = historyRows.firstOrNull { it.date == today.toString() }
            if (todayFromDb != null) {
                easyTodayTotal = todayFromDb.amount
                easyTodayDeposit = todayFromDb.depositAmount
            }
        }
        if (todayDepositAmount != null) {
            easyTodayDeposit = todayDepositAmount
        }

        // 최근 7일: 오늘 포함 (D-6 ~ D)
        easyDates = (6L downTo 0L).map { today.minusDays(it) }
        // 이전 7일: D-13 ~ D-7
        val prevDates = (13L downTo 7L).map { today.minusDays(it) }
        easyValues = easyDates.map { d ->
            if (d == today) easyTodayTotal else (amountByDate[d.toString()] ?: 0)
        }
        easyDepositValues = easyDates.map { d ->
            if (d == today) easyTodayDeposit else (depositByDate[d.toString()] ?: 0)
        }

        val easyTotal = easyValues.sum()
        val easyPrevTotal = prevDates.sumOf { d -> amountByDate[d.toString()] ?: 0 }
        easyDiff = easyTotal - easyPrevTotal

        if (!easyDates.contains(easyTopDate)) {
            easyTopDate = today
        }
        easyTopSales = if (easyTopDate == today) {
            easyTodayTotal
        } else {
            amountByDate[easyTopDate.toString()] ?: 0
        }
        easyTopDeposit = if (easyTopDate == today) {
            easyTodayDeposit
        } else {
            depositByDate[easyTopDate.toString()] ?: 0
        }

        val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        range.text = if (easyDates.isNotEmpty()) {
            "${easyDates.first().format(fmt)} - ${easyDates.last().format(fmt)}"
        } else {
            "--"
        }
        // 상단 날짜(오늘 막대 비교 영역)도 선택된 일자로 맞춘다.
        range.rootView.findViewById<TextView>(R.id.text_easyshop_pie_date).text =
            easyTopDate.format(fmt)

        if (showEmptyState) {
            empty.visibility = when {
                todayResult != null && !todayResult.success -> View.VISIBLE
                easyTodayTotal == 0 && easyValues.sum() == 0 -> View.VISIBLE
                else -> View.GONE
            }
            if (empty.visibility == View.VISIBLE) {
                empty.text = if (todayResult != null && !todayResult.success) {
                    "EasyShop 오늘 매출 조회 실패: ${todayResult.message}"
                } else {
                    "EasyShop 데이터가 없습니다."
                }
            }
        } else {
            empty.visibility = View.GONE
        }

        chart.setData(easyDates, easyValues, today)
        chart.setSecondaryData(easyDepositValues)
        compare.setData(easyTopDeposit, easyTopSales)

        easyAnimator?.cancel()
        if (animate) {
            chart.setAnimationProgress(0f)
            compare.setAnimationProgress(0f)
            easyAnimator = startEasyAnimation(
                chart = chart,
                compareChart = compare,
                totalText = total,
                totalAmount = easyTotal,
                diff = easyDiff
            )
        } else {
            chart.setAnimationProgress(1f)
            compare.setAnimationProgress(1f)
            total.text = makeTotalText(easyTotal, kotlin.math.abs(easyDiff), easyDiff)
        }
    }

    private fun sumEasyShopSalesByDate(
        records: List<EasyShopRepository.SalesRecord>,
        date: LocalDate
    ): Int {
        val nonCanceled = records.filterNot { it.isCanceled }
        val dayRows = nonCanceled.filter {
            it.transactionDate.isBlank() || it.transactionDate == date.toString()
        }
        val sourceRows = if (dayRows.isEmpty()) nonCanceled else dayRows
        return sourceRows.sumOf { it.amount }
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
        applySalesSubtabRouteIfAny(root)
        if (isVmmsTabSelected) {
            val chart = root.findViewById<SalesChartView>(R.id.sales_chart)
            val pie = root.findViewById<TodayPieChartView>(R.id.today_pie_chart)
            val total = root.findViewById<TextView>(R.id.text_total)
            if (vmmsDates.isNotEmpty()) {
                chart.setData(vmmsDates, vmmsValues, LocalDate.now())
                chart.setAnimationProgress(0f)
                pie.setData(vmmsPieTotal, vmmsPieShares)
                pie.setAnimationProgress(0f)
                root.findViewById<TextView>(R.id.text_today_pie_date).text =
                    vmmsPieDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                vmmsAnimator?.cancel()
                vmmsAnimator = startAnimation(chart, pie, total, vmmsValues.sum(), vmmsDiff)
            }
        } else {
            val chart = root.findViewById<SalesChartView>(R.id.easyshop_sales_chart)
            val compare = root.findViewById<TodayCompareBarChartView>(R.id.easyshop_compare_chart)
            val total = root.findViewById<TextView>(R.id.text_easyshop_total)
            if (easyDates.isNotEmpty()) {
                chart.setData(easyDates, easyValues, LocalDate.now())
                chart.setSecondaryData(easyDepositValues)
                chart.setAnimationProgress(0f)
                compare.setData(easyTopDeposit, easyTopSales)
                compare.setAnimationProgress(0f)
                root.findViewById<TextView>(R.id.text_easyshop_pie_date).text =
                    easyTopDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                easyAnimator?.cancel()
                easyAnimator = startEasyAnimation(chart, compare, total, easyValues.sum(), easyDiff)
            }
        }
    }

    private fun applySalesSubtabRouteIfAny(root: View) {
        val main = activity as? MainActivity ?: return
        when (main.consumeSalesSubtabTarget()) {
            MainActivity.SALES_SUBTAB_EASYSHOP -> setSubTab(root, false)
            MainActivity.SALES_SUBTAB_VMMS -> setSubTab(root, true)
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

    private fun showEasyShopDailySalesDialog() {
        if (easyDailySalesDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_easyshop_daily_sales, null, false)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_easyshop_daily_sales)
        val emptyText = dialogView.findViewById<TextView>(R.id.text_easyshop_daily_sales_empty)
        val close = dialogView.findViewById<android.widget.Button>(R.id.button_easyshop_daily_sales_close)
        val adapter = EasyShopDailySalesAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        easyDailySalesDialog = dialog
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            easyDailySalesLoadJob?.cancel()
            easyDailySalesLoadJob = null
            if (easyDailySalesDialog === dialog) easyDailySalesDialog = null
        }

        easyDailySalesLoadJob?.cancel()
        easyDailySalesLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).easyShopSalesDao().getAll()
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

    private fun startEasyAnimation(
        chart: SalesChartView,
        compareChart: TodayCompareBarChartView,
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
                compareChart.setAnimationProgress(f)
                val animatedTotal = (totalAmount * f).toInt()
                val animatedDiff = (kotlin.math.abs(diff) * f).toInt()
                totalText.text = makeTotalText(animatedTotal, animatedDiff, diff)
            }
            start()
        }
    }

    private fun startPieOnlyAnimation(pieChart: TodayPieChartView): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                pieChart.setAnimationProgress(f)
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
        vmmsPieAnimator?.cancel()
        easyAnimator?.cancel()
        vmmsAnimator = null
        vmmsPieAnimator = null
        easyAnimator = null
        super.onStop()
    }

    override fun onDestroyView() {
        vmmsAnimator?.cancel()
        vmmsPieAnimator?.cancel()
        easyAnimator?.cancel()
        vmmsAnimator = null
        vmmsPieAnimator = null
        easyAnimator = null
        dailySalesLoadJob?.cancel()
        dailySalesLoadJob = null
        dailySalesDialog?.dismiss()
        dailySalesDialog = null
        easyDailySalesLoadJob?.cancel()
        easyDailySalesLoadJob = null
        easyDailySalesDialog?.dismiss()
        easyDailySalesDialog = null
        vmmsBackfillJob?.cancel()
        vmmsBackfillJob = null
        vmmsPieLoadJob?.cancel()
        vmmsPieLoadJob = null
        easyBackfillJob?.cancel()
        easyBackfillJob = null
        super.onDestroyView()
    }
}
