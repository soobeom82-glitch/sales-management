package com.example.vmmswidget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TodayCompareBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var depositAmount: Int = 0
    private var salesAmount: Int = 0
    private var animationProgress: Float = 1f

    private val leftLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4B5563")
        textSize = sp(13f)
        textAlign = Paint.Align.LEFT
    }
    private val rightLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4B5563")
        textSize = sp(13f)
        textAlign = Paint.Align.RIGHT
    }
    private val leftAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = sp(13f)
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }
    private val rightAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E4FA3")
        textSize = sp(13f)
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    private val leftTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        style = Paint.Style.FILL
    }
    private val rightTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        style = Paint.Style.FILL
    }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B7C1CD")
        style = Paint.Style.FILL
    }
    private val salesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F8CC9")
        style = Paint.Style.FILL
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        strokeWidth = dp(1.2f)
    }
    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    fun setData(depositAmount: Int, salesAmount: Int) {
        this.depositAmount = max(0, depositAmount)
        this.salesAmount = max(0, salesAmount)
        invalidate()
    }

    fun setAnimationProgress(progress: Float) {
        animationProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()

        val labelY = top + dp(18f)
        val amountY = labelY + dp(18f)
        val chartTop = amountY + dp(10f)
        val chartBottom = bottom - dp(8f)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val contentW = (right - left).coerceAtLeast(1f)
        val centerX = left + (contentW / 2f)
        val centerGap = dp(10f)
        val leftEnd = centerX - (centerGap / 2f)
        val rightStart = centerX + (centerGap / 2f)
        val sidePadding = dp(8f)
        val halfWidth = (
            min(leftEnd - left, right - rightStart) - sidePadding
            ).coerceAtLeast(1f)
        val barHeight = min(dp(14f), chartHeight * 0.28f)
        val barTop = chartTop + ((chartHeight - barHeight) / 2f)
        val barBottom = barTop + barHeight
        val centerY = barTop + (barHeight / 2f)
        val radius = barHeight / 2f

        canvas.drawText("오늘 입금 내역", left, labelY, leftLabelPaint)
        canvas.drawText("오늘 매출", right, labelY, rightLabelPaint)
        canvas.drawText("${String.format("%,d", depositAmount)}원", left, amountY, leftAmountPaint)
        canvas.drawText("${String.format("%,d", salesAmount)}원", right, amountY, rightAmountPaint)

        val guideUnit = 10_000
        val maxValue = max(depositAmount, salesAmount)
        val guideMax = max(guideUnit, ((maxValue + guideUnit - 1) / guideUnit) * guideUnit)
        var mark = guideUnit
        while (mark <= guideMax) {
            val ratio = mark.toFloat() / guideMax.toFloat()
            val leftX = leftEnd - (halfWidth * ratio)
            val rightX = rightStart + (halfWidth * ratio)
            canvas.drawLine(leftX, chartTop, leftX, chartBottom, guideLinePaint)
            canvas.drawLine(rightX, chartTop, rightX, chartBottom, guideLinePaint)
            mark += guideUnit
        }

        canvas.drawLine(centerX, chartTop, centerX, chartBottom, centerLinePaint)

        val leftTrack = RectF(leftEnd - halfWidth, barTop, leftEnd, barBottom)
        val rightTrack = RectF(rightStart, barTop, rightStart + halfWidth, barBottom)
        canvas.drawRoundRect(leftTrack, radius, radius, leftTrackPaint)
        canvas.drawRoundRect(rightTrack, radius, radius, rightTrackPaint)

        val depositWidth = halfWidth * (depositAmount.toFloat() / guideMax.toFloat()) * animationProgress
        if (depositWidth > 0f) {
            val l = leftEnd - depositWidth
            val depositRect = RectF(l, barTop, leftEnd, barBottom)
            canvas.drawRect(depositRect, depositPaint)
            canvas.drawCircle(l, centerY, radius, depositPaint)
        }

        val salesWidth = halfWidth * (salesAmount.toFloat() / guideMax.toFloat()) * animationProgress
        if (salesWidth > 0f) {
            val r = rightStart + salesWidth
            val salesRect = RectF(rightStart, barTop, r, barBottom)
            canvas.drawRect(salesRect, salesPaint)
            canvas.drawCircle(r, centerY, radius, salesPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }
}
