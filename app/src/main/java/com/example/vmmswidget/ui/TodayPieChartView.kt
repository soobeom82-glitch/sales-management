package com.example.vmmswidget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
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
        val tiltY = 0.55f

        val availableW = width * 0.62f
        val maxRadiusByWidth = availableW / 2f
        val topArea = centerTextPaint.textSize + 16f
        val availableHForPie = (height - topArea - 16f).coerceAtLeast(10f)
        val maxRadiusByHeight = (availableHForPie / (2f * tiltY)).coerceAtLeast(1f)
        val radius = min(maxRadiusByWidth, maxRadiusByHeight) * 0.60f
        val ry = radius * tiltY
        val depth = (ry * 0.14f).coerceAtLeast(7f)
        val cy = topArea + ry + 200f
        val outerRect = RectF(cx - radius, cy - ry, cx + radius, cy + ry)
        val bottomRect = RectF(outerRect).apply { offset(0f, depth) }
        val shadowRect = RectF(outerRect).apply {
            offset(-12f, depth + 14f)
            inset(-10f, 6f)
        }

        val sum = shares.sumOf { it.amount }.coerceAtLeast(1)
        data class SliceGeom(
            val share: TransactionsRepository.TodayItemShare,
            val topColor: Int,
            val sideColor: Int,
            val start: Float,
            val fullSweep: Float,
            val drawStart: Float,
            val drawSweep: Float,
            val mid: Float,
            val offX: Float,
            val offY: Float
        )

        val slices = mutableListOf<SliceGeom>()
        var start = -90f
        shares.forEachIndexed { idx, share ->
            val fullSweep = (share.amount / sum.toFloat()) * 360f
            val sweep = fullSweep * progress
            val gapAngle = min(2.4f, fullSweep * 0.22f)
            val drawSweep = (sweep - gapAngle).coerceAtLeast(0f)
            val drawStart = start + (gapAngle / 2f)
            val baseColor = ringColors[idx % ringColors.size]

            val mid = start + fullSweep / 2f
            val topColor = applyTopLight(baseColor, mid)
            val sideColor = darkenColor(applyTopLight(baseColor, mid, side = true), 0.74f)
            val midRad = Math.toRadians(mid.toDouble())
            val offX = (cos(midRad) * explode).toFloat()
            val offY = (sin(midRad) * explode).toFloat()

            slices += SliceGeom(
                share = share,
                topColor = topColor,
                sideColor = sideColor,
                start = start,
                fullSweep = fullSweep,
                drawStart = drawStart,
                drawSweep = drawSweep,
                mid = mid,
                offX = offX,
                offY = offY
            )
            start += fullSweep
        }

        // Base drop shadow
        canvas.drawOval(shadowRect, shadowPaint)

        // Draw extrusion walls first (front/lower half only)
        slices.forEach { slice ->
            val outer = RectF(outerRect).apply { offset(slice.offX, slice.offY) }
            val bottom = RectF(bottomRect).apply { offset(slice.offX, slice.offY) }
            drawVisibleSideWalls(
                canvas = canvas,
                topRect = outer,
                bottomRect = bottom,
                start = slice.drawStart,
                sweep = slice.drawSweep,
                color = slice.sideColor
            )
        }

        // Draw top faces
        slices.forEach { slice ->
            val outer = RectF(outerRect).apply { offset(slice.offX, slice.offY) }
            drawTopSlice(
                canvas = canvas,
                cx = cx + slice.offX,
                cy = cy + slice.offY,
                outer = outer,
                start = slice.drawStart,
                sweep = slice.drawSweep,
                color = slice.topColor
            )
        }

        // Labels and guide lines
        slices.forEach { slice ->
            val rad = Math.toRadians(slice.mid.toDouble())
            val x1 = cx + slice.offX + cos(rad).toFloat() * radius
            val y1 = cy + slice.offY + sin(rad).toFloat() * ry
            val x2 = cx + slice.offX + cos(rad).toFloat() * (radius + 34f)
            val y2 = cy + slice.offY + sin(rad).toFloat() * (ry + 26f)
            val lowerHalfBias = if (sin(rad) > 0.25) 42f else if (sin(rad) > 0.0) 22f else 0f
            val y2Adjusted = y2 + lowerHalfBias
            val rightSide = cos(rad) >= 0
            val x3 = if (rightSide) x2 + 54f else x2 - 54f

            canvas.drawLine(x1, y1, x2, y2Adjusted, guidePaint)
            canvas.drawLine(x2, y2Adjusted, x3, y2Adjusted, guidePaint)

            val pct = ((slice.share.amount * 100f) / sum.toFloat()).roundToInt()
            val name = if (slice.share.label.length > 12) slice.share.label.take(12) + "…" else slice.share.label
            val label = "$name (${pct}%)"
            val tx = if (rightSide) x3 + 10f else x3 - labelPaint.measureText(label) - 10f
            canvas.drawText(label, tx, y2Adjusted - 2f, labelPaint)
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

    private fun drawVisibleSideWalls(
        canvas: Canvas,
        topRect: RectF,
        bottomRect: RectF,
        start: Float,
        sweep: Float,
        color: Int
    ) {
        if (sweep <= 0f) return
        val visibleSegments = visibleFrontSegments(start, sweep)
        if (visibleSegments.isEmpty()) return
        fillPaint.color = color
        visibleSegments.forEach { (segStart, segSweep) ->
            if (segSweep <= 0f) return@forEach
            val path = Path().apply {
                arcTo(topRect, segStart, segSweep)
                val end = pointOnOval(bottomRect, segStart + segSweep)
                lineTo(end.first, end.second)
                arcTo(bottomRect, segStart + segSweep, -segSweep)
                close()
            }
            canvas.drawPath(path, fillPaint)
        }
    }

    private fun visibleFrontSegments(start: Float, sweep: Float): List<Pair<Float, Float>> {
        if (sweep <= 0f) return emptyList()
        val segments = mutableListOf<Pair<Float, Float>>()
        var remaining = sweep
        var cursor = normalizeAngle(start)
        while (remaining > 0f) {
            val toEnd = 360f - cursor
            val take = min(toEnd, remaining)
            val segStart = cursor
            val segEnd = cursor + take
            val frontStart = maxOf(segStart, 0f)
            val frontEnd = min(segEnd, 180f)
            if (frontEnd > frontStart) {
                segments += frontStart to (frontEnd - frontStart)
            }
            remaining -= take
            cursor = 0f
        }
        return segments
    }

    private fun normalizeAngle(angle: Float): Float {
        var value = angle % 360f
        if (value < 0f) value += 360f
        return value
    }

    private fun pointOnOval(rect: RectF, angle: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angle.toDouble())
        val rx = rect.width() / 2f
        val ry = rect.height() / 2f
        val x = rect.centerX() + (cos(rad) * rx).toFloat()
        val y = rect.centerY() + (sin(rad) * ry).toFloat()
        return x to y
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt()
        )
    }

    // Simulate a directional light coming from upper-right (-45deg).
    private fun applyTopLight(color: Int, angleDeg: Float, side: Boolean = false): Int {
        val lightDir = -45f
        val rad = Math.toRadians((angleDeg - lightDir).toDouble())
        val dot = cos(rad).toFloat() // -1..1
        val intensity = if (side) 0.20f else 0.30f
        return if (dot >= 0f) {
            lightenColor(color, dot * intensity)
        } else {
            darkenColor(color, 1f - (kotlin.math.abs(dot) * intensity))
        }
    }

    private fun lightenColor(color: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val rr = Color.red(color) + ((255 - Color.red(color)) * r).toInt()
        val gg = Color.green(color) + ((255 - Color.green(color)) * r).toInt()
        val bb = Color.blue(color) + ((255 - Color.blue(color)) * r).toInt()
        return Color.argb(Color.alpha(color), rr, gg, bb)
    }
}
