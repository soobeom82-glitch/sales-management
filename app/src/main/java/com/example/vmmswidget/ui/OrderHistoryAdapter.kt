package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.OrderHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderHistoryAdapter(
    private val items: MutableList<OrderHistoryEntity>,
    private val onHistoryClick: (OrderHistoryEntity) -> Unit
) : RecyclerView.Adapter<OrderHistoryAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_history, parent, false)
        return Holder(view, onHistoryClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    fun submit(newItems: List<OrderHistoryEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class Holder(
        itemView: View,
        private val onHistoryClick: (OrderHistoryEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val orderedAt = itemView.findViewById<TextView>(R.id.col_ordered_at)
        private val orderItems = itemView.findViewById<TextView>(R.id.col_order_items)
        private val remarkFlag = itemView.findViewById<TextView>(R.id.col_remark_flag)
        private val formatter = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

        fun bind(row: OrderHistoryEntity, position: Int) {
            val bg = if (position % 2 == 0) EVEN_COLOR else ODD_COLOR
            itemView.setBackgroundColor(bg)
            orderedAt.text = formatter.format(Date(row.orderedAt))
            orderItems.text = row.itemsText
            if (row.remark.isBlank()) {
                remarkFlag.text = ""
                remarkFlag.setTextColor(android.graphics.Color.TRANSPARENT)
            } else {
                remarkFlag.text = "✓"
                remarkFlag.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            }
            itemView.setOnClickListener { onHistoryClick(row) }
        }
    }

    companion object {
        private val EVEN_COLOR = android.graphics.Color.parseColor("#F7F9FB")
        private val ODD_COLOR = android.graphics.Color.WHITE
    }
}
