package com.example.vmmswidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max

object ChartRenderer {
    fun render(
        dates: List<LocalDate>,
        values: List<Int>,
        holidays: Set<LocalDate>,
        width: Int,
        height: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (values.isEmpty() || dates.isEmpty()) return bmp

        val maxValue = max(1, values.maxOrNull() ?: 1)
        val padding = 6f
        val w = width - padding * 2
        val h = height - padding * 2

        val count = values.size
        val step = if (count <= 1) 0f else (w / (count - 1))

        // weekend background bands (Sat/Sun)
        val weekendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7DADA.toInt()
            style = Paint.Style.FILL
        }
        val gap = 2f
        for (i in 0 until count) {
            val d = dates.getOrNull(i) ?: continue
            if (d.dayOfWeek == DayOfWeek.SATURDAY ||
                d.dayOfWeek == DayOfWeek.SUNDAY ||
                holidays.contains(d)
            ) {
                val centerX = padding + step * i
                val left = (centerX - step / 2f + gap).coerceAtLeast(padding)
                val right = (centerX + step / 2f - gap).coerceAtMost(padding + w)
                canvas.drawRect(left, padding, right, padding + h, weekendPaint)
            }
        }

        val points = values.mapIndexed { idx, v ->
            val x = padding + (w * idx / max(1, values.size - 1))
            val y = padding + h - (h * v / maxValue.toFloat())
            x to y
        }

        val smoothPath = smoothPath(points)

        val baseColor = 0xFFB35C5C.toInt() // muted red
        val minStroke = 2.0f
        val maxStroke = 20.0f

        // dashed baseline at 7-day average
        val avgValue = if (values.isNotEmpty()) values.sum() / values.size else 0
        val baselineY = padding + h - (h * avgValue / maxValue.toFloat())
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }
        canvas.drawLine(padding, baselineY, padding + w, baselineY, dashPaint)

        val segments = 24
        for (i in 0 until segments) {
            val t = i / segments.toFloat()
            val t2 = (i + 1) / segments.toFloat()
            val widthAtT = maxStroke - (maxStroke - minStroke) * t
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = baseColor
                style = Paint.Style.STROKE
                strokeWidth = widthAtT
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val segPath = Path()
            smoothPathMeasureSegment(smoothPath, t, t2, segPath)
            canvas.drawPath(segPath, paint)
        }
        return bmp
    }

    private fun smoothPath(points: List<Pair<Float, Float>>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].first, points[0].second)
        if (points.size == 1) return path

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.first + curr.first) / 2f
            val midY = (prev.second + curr.second) / 2f
            path.quadTo(prev.first, prev.second, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.first, last.second)
        return path
    }

    private fun smoothPathMeasureSegment(src: Path, startT: Float, endT: Float, out: Path) {
        val pm = android.graphics.PathMeasure(src, false)
        val len = pm.length
        val start = len * startT
        val end = len * endT
        pm.getSegment(start, end, out, true)
    }
}
