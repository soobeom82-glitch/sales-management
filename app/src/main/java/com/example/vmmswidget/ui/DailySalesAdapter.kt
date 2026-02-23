package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.SalesEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailySalesAdapter(
    private val items: MutableList<SalesEntity>
) : RecyclerView.Adapter<DailySalesAdapter.VH>() {

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
        val dateText = runCatching {
            LocalDate.parse(row.date).format(DateTimeFormatter.ofPattern("M/d"))
        }.getOrDefault(row.date)
        holder.date.text = dateText
        holder.amount.text = String.format("%,d원", row.amount)
    }

    fun submit(newItems: List<SalesEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

