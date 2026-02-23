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
        color = 0xFF4A6FA5.toInt()
        style = Paint.Style.FILL
    }
    private val barPaintToday = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9BB5D6.toInt()
        style = Paint.Style.FILL
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
    fun setData(
        dates: List<LocalDate>,
        values: List<Int>,
        today: LocalDate
    ) {
        this.dates = dates
        this.values = values
        this.today = today
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

        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom

        val rawMax = values.maxOrNull() ?: 1
        val rounded = ((rawMax + 500) / 1000) * 1000
        val maxValue = max(1, rounded + 15_000)
        val barCount = values.size
        val gap = 24f
        val bandWidth = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(8f)
        val barWidth = (bandWidth * 0.70f).coerceAtLeast(8f)
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
                canvas.drawRect(left, paddingTop, right, paddingTop + h, weekendBgPaint)
            }
        }

        // horizontal grid (10,000 unit)
        val gridStep = 10_000
        val maxGrid = ((maxValue + gridStep - 1) / gridStep) * gridStep
        var v = 0
        while (v <= maxGrid) {
            val y = paddingTop + h - (h * v / maxGrid.toFloat())
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            v += gridStep
        }

        // average line for 7-day values
        val avg = values.average().toFloat()
        val avgY = paddingTop + h - (h * (avg / maxGrid.toFloat()))
        canvas.drawLine(chartLeft, avgY, chartRight, avgY, avgPaint)

        // Y axis
        canvas.drawLine(
            chartLeft,
            paddingTop,
            chartLeft,
            paddingTop + h,
            axisPaint
        )
        // X axis
        canvas.drawLine(
            chartLeft,
            paddingTop + h,
            chartRight,
            paddingTop + h,
            axisPaint
        )

        // Y-axis labels (10,000 unit)
        v = 0
        val labelAreaWidth = paddingLeft - 12f
        val labelX = 8f
        while (v <= maxGrid) {
            val y = paddingTop + h - (h * v / maxGrid.toFloat())
            val text = String.format("%,d", v)
            val textWidth = labelPaint.measureText(text)
            val x = labelX + (labelAreaWidth - textWidth) / 2f
            canvas.drawText(text, x, y + 12f, labelPaint)
            v += gridStep
        }

        for (i in 0 until barCount) {
            val v = values[i]
            val barHeight = h * v / maxGrid.toFloat() * animProgress
            val bandLeft = startX + i * (bandWidth + gap)
            val left = bandLeft + (bandWidth - barWidth) / 2f
            val top = paddingTop + (h - barHeight)
            val right = left + barWidth
            val bottom = paddingTop + h
            val d = dates.getOrNull(i)
            val paint = if (d != null && d == today) barPaintToday else barPaint
            canvas.drawRect(left, top, right, bottom, paint)

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
            val dayX2 = left + (barWidth - xDayWidth) / 2f
            canvas.drawText(dayText, dayX2, bottom + 40f, xAxisPaint)
        }
    }
}
