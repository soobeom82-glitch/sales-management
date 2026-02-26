package com.example.vmmswidget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import kotlin.math.max

class SalesChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var dates: List<LocalDate> = emptyList()
    private var values: List<Int> = emptyList()
    private var secondaryValues: List<Int> = emptyList()
    private var secondaryLabel: String = "입금"
    private var today: LocalDate? = null
    private var animProgress: Float = 1f

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        strokeWidth = 3.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8C2D1.toInt()
        strokeWidth = 2.2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 36f // Y-axis label size (keep)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3F6EA7.toInt()
        style = Paint.Style.FILL
    }
    private val barPaintToday = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5F8FC8.toInt()
        style = Paint.Style.FILL
    }
    private val secondaryBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE7B66B.toInt()
        style = Paint.Style.FILL
    }
    private val secondaryBarPaintToday = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF0C98F.toInt()
        style = Paint.Style.FILL
    }
    private val salesBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2D5F97.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val secondaryBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC58B32.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val secondaryHatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55A56B14
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val weekendBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7DADA.toInt()
        style = Paint.Style.FILL
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 40f
    }

    fun setData(
        dates: List<LocalDate>,
        values: List<Int>,
        today: LocalDate
    ) {
        this.dates = dates
        this.values = values
        this.secondaryValues = emptyList()
        this.today = today
        invalidate()
    }

    fun setSecondaryData(values: List<Int>, label: String = "입금") {
        secondaryValues = values
        secondaryLabel = label
        invalidate()
    }

    fun setAnimationProgress(progress: Float) {
        animProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val paddingLeft = 140f
        val paddingBottom = 90f
        val paddingTop = 26f
        val paddingRight = 20f

        val hasSecondary = secondaryValues.size == values.size
        val legendAreaHeight = if (hasSecondary) 64f else 0f
        val plotTop = paddingTop + legendAreaHeight
        val w = width - paddingLeft - paddingRight
        val h = (height - plotTop - paddingBottom).coerceAtLeast(1f)
        val rawMax = max(
            values.maxOrNull() ?: 1,
            if (hasSecondary) secondaryValues.maxOrNull() ?: 1 else 1
        )
        val rounded = ((rawMax + 500) / 1000) * 1000
        val maxValue = max(1, rounded + 15_000)
        val barCount = values.size
        val gap = 24f
        val bandWidth = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(8f)
        val innerGap = if (hasSecondary) 1f else 0f
        val pairBarWidth = if (hasSecondary) {
            ((bandWidth - innerGap) / 2f).coerceAtLeast(4f)
        } else {
            0f
        }
        val secondaryBarWidth = if (hasSecondary) pairBarWidth else 0f
        val salesBarWidth = if (hasSecondary) pairBarWidth else (bandWidth * 0.70f).coerceAtLeast(8f)
        val startX = paddingLeft
        val chartLeft = paddingLeft
        val chartRight = paddingLeft + w

        // weekend/holiday background bands
        val holidaySet = com.example.vmmswidget.data.HolidayCalendar
            .holidaysForYears(dates.map { it.year }.toSet())
        for (i in 0 until barCount) {
            val d = dates.getOrNull(i) ?: continue
            if (d.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                d.dayOfWeek == java.time.DayOfWeek.SUNDAY ||
                holidaySet.contains(d)
            ) {
                val bandLeft = startX + i * (bandWidth + gap)
                val left = bandLeft
                val right = bandLeft + bandWidth
                canvas.drawRect(left, plotTop, right, plotTop + h, weekendBgPaint)
            }
        }

        // horizontal grid (10,000 unit)
        val gridStep = 10_000
        val maxGrid = ((maxValue + gridStep - 1) / gridStep) * gridStep
        var v = 0
        while (v <= maxGrid) {
            val y = plotTop + h - (h * v / maxGrid.toFloat())
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            v += gridStep
        }

        // average line for 7-day values
        val avg = values.average().toFloat()
        val avgY = plotTop + h - (h * (avg / maxGrid.toFloat()))
        canvas.drawLine(chartLeft, avgY, chartRight, avgY, avgPaint)

        if (hasSecondary) {
            val legendY = paddingTop + 46f
            var legendX = chartLeft + 8f
            val markW = 24f
            val markH = 14f
            val markToTextGap = 16f
            val itemGap = 56f
            canvas.drawRect(legendX, legendY - markH, legendX + markW, legendY, secondaryBarPaint)
            canvas.drawRect(legendX, legendY - markH, legendX + markW, legendY, secondaryBorderPaint)
            drawHatch(
                canvas = canvas,
                left = legendX,
                top = legendY - markH,
                right = legendX + markW,
                bottom = legendY
            )
            legendX += markW + markToTextGap
            canvas.drawText(secondaryLabel, legendX, legendY, legendPaint)
            legendX += legendPaint.measureText(secondaryLabel) + itemGap

            canvas.drawRect(legendX, legendY - markH, legendX + markW, legendY, barPaint)
            canvas.drawRect(legendX, legendY - markH, legendX + markW, legendY, salesBorderPaint)
            legendX += markW + markToTextGap
            canvas.drawText("매출", legendX, legendY, legendPaint)
        }

        // Y axis
        canvas.drawLine(
            chartLeft,
            plotTop,
            chartLeft,
            plotTop + h,
            axisPaint
        )
        // X axis
        canvas.drawLine(
            chartLeft,
            plotTop + h,
            chartRight,
            plotTop + h,
            axisPaint
        )

        // Y-axis labels (10,000 unit)
        v = 0
        val labelAreaWidth = paddingLeft - 12f
        val labelX = 8f
        while (v <= maxGrid) {
            val y = plotTop + h - (h * v / maxGrid.toFloat())
            val text = String.format("%,d", v)
            val textWidth = labelPaint.measureText(text)
            val x = labelX + (labelAreaWidth - textWidth) / 2f
            canvas.drawText(text, x, y + 12f, labelPaint)
            v += gridStep
        }

        for (i in 0 until barCount) {
            val salesValue = values[i]
            val bandLeft = startX + i * (bandWidth + gap)
            val bottom = plotTop + h
            val d = dates.getOrNull(i)

            val salesPaint = if (d != null && d == today) barPaintToday else barPaint
            val depositPaint = if (d != null && d == today) secondaryBarPaintToday else secondaryBarPaint
            if (hasSecondary) {
                val depositValue = secondaryValues[i]
                val clusterWidth = secondaryBarWidth + salesBarWidth + innerGap
                val clusterLeft = bandLeft + (bandWidth - clusterWidth) / 2f

                val depositHeight = h * depositValue / maxGrid.toFloat() * animProgress
                val depositTop = plotTop + (h - depositHeight)
                val depositLeft = clusterLeft
                val depositRight = depositLeft + secondaryBarWidth
                canvas.drawRect(depositLeft, depositTop, depositRight, bottom, depositPaint)
                canvas.drawRect(depositLeft, depositTop, depositRight, bottom, secondaryBorderPaint)
                drawHatch(
                    canvas = canvas,
                    left = depositLeft,
                    top = depositTop,
                    right = depositRight,
                    bottom = bottom
                )

                val salesHeight = h * salesValue / maxGrid.toFloat() * animProgress
                val salesTop = plotTop + (h - salesHeight)
                val salesLeft = depositRight + innerGap
                val salesRight = salesLeft + salesBarWidth
                canvas.drawRect(salesLeft, salesTop, salesRight, bottom, salesPaint)
                canvas.drawRect(salesLeft, salesTop, salesRight, bottom, salesBorderPaint)
            } else {
                val salesHeight = h * salesValue / maxGrid.toFloat() * animProgress
                val left = bandLeft + (bandWidth - salesBarWidth) / 2f
                val top = plotTop + (h - salesHeight)
                val right = left + salesBarWidth
                canvas.drawRect(left, top, right, bottom, salesPaint)
                canvas.drawRect(left, top, right, bottom, salesBorderPaint)
            }

            // X-axis label (day of month)
            val day = dates.getOrNull(i)?.dayOfWeek?.let { dow ->
                when (dow) {
                    java.time.DayOfWeek.MONDAY -> "월"
                    java.time.DayOfWeek.TUESDAY -> "화"
                    java.time.DayOfWeek.WEDNESDAY -> "수"
                    java.time.DayOfWeek.THURSDAY -> "목"
                    java.time.DayOfWeek.FRIDAY -> "금"
                    java.time.DayOfWeek.SATURDAY -> "토"
                    java.time.DayOfWeek.SUNDAY -> "일"
                }
            } ?: ""
            // X-axis label (Mon.)
            val dayText = when (day) {
                "월" -> "Mon."
                "화" -> "Tue."
                "수" -> "Wed."
                "목" -> "Thu."
                "금" -> "Fri."
                "토" -> "Sat."
                "일" -> "Sun."
                else -> ""
            }
            // X-axis label size: 1.3x of base(36 -> 46.8)
            val xAxisPaint = Paint(labelPaint).apply { textSize = 47f }
            val xDayWidth = xAxisPaint.measureText(dayText)
            val dayX2 = bandLeft + (bandWidth - xDayWidth) / 2f
            canvas.drawText(dayText, dayX2, bottom + 40f, xAxisPaint)
        }
    }

    private fun drawHatch(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        if (right <= left || bottom <= top) return
        val height = bottom - top
        val step = 8f
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        var x = left - height
        while (x < right) {
            canvas.drawLine(x, bottom, x + height, top, secondaryHatchPaint)
            x += step
        }
        canvas.restore()
    }
}
