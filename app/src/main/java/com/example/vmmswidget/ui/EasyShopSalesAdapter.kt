package com.example.vmmswidget.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.net.EasyShopRepository

class EasyShopSalesAdapter(
    private val items: MutableList<EasyShopRepository.SalesRecord>
) : RecyclerView.Adapter<EasyShopSalesAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_easyshop_sales, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<EasyShopRepository.SalesRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val time = itemView.findViewById<TextView>(R.id.col_easy_time)
        private val terminal = itemView.findViewById<TextView>(R.id.col_easy_terminal)
        private val type = itemView.findViewById<TextView>(R.id.col_easy_type)
        private val amount = itemView.findViewById<TextView>(R.id.col_easy_amount)
        private val status = itemView.findViewById<TextView>(R.id.col_easy_status)

        fun bind(row: EasyShopRepository.SalesRecord, position: Int) {
            val bg = if (row.isCanceled) CANCELED_COLOR else if (position % 2 == 0) EVEN_COLOR else ODD_COLOR
            itemView.setBackgroundColor(bg)
            time.text = row.transactionTime
            terminal.text = row.terminalNo
            type.text = row.cardType
            amount.text = String.format("%,d원", row.amount)
            status.text = row.status
        }
    }

    companion object {
        private val EVEN_COLOR = Color.parseColor("#F7F9FB")
        private val ODD_COLOR = Color.WHITE
        private val CANCELED_COLOR = Color.parseColor("#FDEEEF")
    }
}
