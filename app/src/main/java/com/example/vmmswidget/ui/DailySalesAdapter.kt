package com.example.vmmswidget.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.HolidayCalendar
import com.example.vmmswidget.data.db.SalesEntity
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

class DailySalesAdapter(
    private val items: MutableList<SalesEntity>
) : RecyclerView.Adapter<DailySalesAdapter.VH>() {

    private val holidayCache = mutableMapOf<Int, Set<LocalDate>>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.col_sales_date)
        val amount: TextView = view.findViewById(R.id.col_sales_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_sales, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val parsedDate = runCatching { LocalDate.parse(row.date) }.getOrNull()
        val dateText = parsedDate?.let { formatDateWithWeekday(it) } ?: row.date
        holder.date.text = dateText
        holder.amount.text = String.format("%,d원", row.amount)
        val isWeekendOrHoliday = parsedDate?.let { isWeekendOrHoliday(it) } == true
        holder.itemView.setBackgroundColor(
            if (isWeekendOrHoliday) 0xFFF7DADA.toInt() else Color.TRANSPARENT
        )
    }

    fun submit(newItems: List<SalesEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun isWeekendOrHoliday(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return true
        }
        val holidays = holidayCache.getOrPut(date.year) {
            HolidayCalendar.holidaysForYear(date.year)
        }
        return holidays.contains(date)
    }

    private fun formatDateWithWeekday(date: LocalDate): String {
        val day = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
        return "${date.format(DateTimeFormatter.ofPattern("M/d"))}($day)"
    }
}
