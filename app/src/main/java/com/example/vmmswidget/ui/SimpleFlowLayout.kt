package com.example.vmmswidget.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class SimpleFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val horizontalGap = dp(8)
    private val verticalGap = dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize

        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        val childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, childWidthSpec, childHeightSpec)
            val childW = child.measuredWidth
            val childH = child.measuredHeight

            if (x + childW + paddingRight > maxWidth && x > paddingLeft) {
                x = paddingLeft
                y += rowHeight + verticalGap
                rowHeight = 0
            }

            x += childW + horizontalGap
            rowHeight = maxOf(rowHeight, childH)
        }

        val contentHeight = y + rowHeight + paddingBottom
        val finalWidth = resolveSize(widthSize, widthMeasureSpec)
        val finalHeight = resolveSize(contentHeight, heightMeasureSpec)
        setMeasuredDimension(finalWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val childW = child.measuredWidth
            val childH = child.measuredHeight

            if (x + childW + paddingRight > maxWidth && x > paddingLeft) {
                x = paddingLeft
                y += rowHeight + verticalGap
                rowHeight = 0
            }

            child.layout(x, y, x + childW, y + childH)
            x += childW + horizontalGap
            rowHeight = maxOf(rowHeight, childH)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
