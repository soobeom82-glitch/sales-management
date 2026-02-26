package com.example.vmmswidget.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.HolidayCalendar
import com.example.vmmswidget.data.db.EasyShopSalesEntity
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

class EasyShopDailySalesAdapter(
    private val items: MutableList<EasyShopSalesEntity>
) : RecyclerView.Adapter<EasyShopDailySalesAdapter.VH>() {

    private val holidayCache = mutableMapOf<Int, Set<LocalDate>>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.col_easy_date)
        val sales: TextView = view.findViewById(R.id.col_easy_sales)
        val deposit: TextView = view.findViewById(R.id.col_easy_deposit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_easyshop_daily_sales, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val parsedDate = runCatching { LocalDate.parse(row.date) }.getOrNull()
        val dateText = parsedDate?.format(DateTimeFormatter.ofPattern("M/d")) ?: row.date
        holder.date.text = dateText
        holder.sales.text = String.format("%,d원", row.amount)
        holder.deposit.text = String.format("%,d원", row.depositAmount)
        val isWeekendOrHoliday = parsedDate?.let { isWeekendOrHoliday(it) } == true
        holder.itemView.setBackgroundColor(
            if (isWeekendOrHoliday) 0xFFF7DADA.toInt() else Color.TRANSPARENT
        )
    }

    fun submit(newItems: List<EasyShopSalesEntity>) {
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
}
