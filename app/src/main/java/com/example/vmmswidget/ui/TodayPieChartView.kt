package com.example.vmmswidget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.example.vmmswidget.net.TransactionsRepository
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class TodayPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var totalAmount: Int = 0
    private var shares: List<TransactionsRepository.TodayItemShare> = emptyList()
    private var progress: Float = 1f

    private val ringColors = intArrayOf(
        0xFF8AB6E8.toInt(),
        0xFF7EC8C6.toInt(),
        0xFFF0B27A.toInt(),
        0xFFE5989B.toInt(),
        0xFFC39BD3.toInt(),
        0xFFA9DFBF.toInt()
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E4FA3.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            22f,
            resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        textSize = 34f
    }

    fun setData(totalAmount: Int, shares: List<TransactionsRepository.TodayItemShare>) {
        this.totalAmount = totalAmount
        this.shares = shares
        invalidate()
    }

    fun setAnimationProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shares.isEmpty()) return

        val cx = width / 2f
        val explode = 0f
        val tiltY = 0.82f

        val availableW = width * 0.62f
        val maxRadiusByWidth = availableW / 2f
        val topArea = centerTextPaint.textSize + 16f
        val availableHForPie = (height - topArea - 16f).coerceAtLeast(10f)
        val maxRadiusByHeight = (availableHForPie / (2f * tiltY)).coerceAtLeast(1f)
        val radius = min(maxRadiusByWidth, maxRadiusByHeight) * 0.60f
        val ry = radius * tiltY
        val cy = topArea + ry + 200f
        val outerRect = RectF(cx - radius, cy - ry, cx + radius, cy + ry)

        val sum = shares.sumOf { it.amount }.coerceAtLeast(1)
        var start = -90f
        for ((idx, share) in shares.withIndex()) {
            val fullSweep = (share.amount / sum.toFloat()) * 360f
            val sweep = fullSweep * progress
            val gapAngle = min(2.4f, fullSweep * 0.22f)
            val drawSweep = (sweep - gapAngle).coerceAtLeast(0f)
            val drawStart = start + (gapAngle / 2f)
            val baseColor = ringColors[idx % ringColors.size]

            val mid = start + fullSweep / 2f
            val midRad = Math.toRadians(mid.toDouble())
            val offX = (cos(midRad) * explode).toFloat()
            val offY = (sin(midRad) * explode).toFloat()

            val outer = RectF(outerRect).apply { offset(offX, offY) }
            drawTopSlice(canvas, cx + offX, cy + offY, outer, drawStart, drawSweep, baseColor)

            val rad = Math.toRadians(mid.toDouble())
            val x1 = cx + offX + cos(rad).toFloat() * radius
            val y1 = cy + offY + sin(rad).toFloat() * ry
            val x2 = cx + offX + cos(rad).toFloat() * (radius + 22f)
            val y2 = cy + offY + sin(rad).toFloat() * (ry + 14f)
            val rightSide = cos(rad) >= 0
            val x3 = if (rightSide) x2 + 38f else x2 - 38f

            canvas.drawLine(x1, y1, x2, y2, guidePaint)
            canvas.drawLine(x2, y2, x3, y2, guidePaint)

            val pct = ((share.amount * 100f) / sum.toFloat()).roundToInt()
            val name = if (share.label.length > 12) share.label.take(12) + "…" else share.label
            val label = "$name (${pct}%)"
            val tx = if (rightSide) x3 + 6f else x3 - labelPaint.measureText(label) - 6f
            canvas.drawText(label, tx, y2 - 4f, labelPaint)

            start += fullSweep
        }

        val animatedTotal = (totalAmount * progress).toInt()
        val amountText = String.format("총 %,d 원", animatedTotal)
        val amountY = outerRect.top - 128f
        canvas.drawText(amountText, cx, amountY, centerTextPaint)
    }

    private fun drawTopSlice(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outer: RectF,
        start: Float,
        sweep: Float,
        color: Int
    ) {
        if (sweep <= 0f) return
        val path = Path().apply {
            moveTo(cx, cy)
            arcTo(outer, start, sweep)
            close()
        }
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }
}
