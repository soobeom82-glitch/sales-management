package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R

data class TransactionRow(
    val id: String,
    val terminalId: String,
    val colNo: String,
    val time: String,
    val rawProduct: String,
    val item: String,
    val amount: String,
    val cardNo: String,
    val isCanceled: Boolean
)

class TransactionsAdapter(
    private val items: MutableList<TransactionRow>,
    private val onAmountClick: (TransactionRow) -> Unit
) : RecyclerView.Adapter<TransactionsAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return Holder(view, onAmountClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    fun submit(newItems: List<TransactionRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun prepend(newItems: List<TransactionRow>) {
        if (newItems.isEmpty()) return
        items.addAll(0, newItems)
        notifyItemRangeInserted(0, newItems.size)
    }

    fun append(newItems: List<TransactionRow>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    class Holder(itemView: View, private val onAmountClick: (TransactionRow) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val time = itemView.findViewById<TextView>(R.id.col_time)
        private val item = itemView.findViewById<TextView>(R.id.col_item)
        private val amount = itemView.findViewById<TextView>(R.id.col_amount)
        private val cardNo = itemView.findViewById<TextView>(R.id.col_card)

        fun bind(row: TransactionRow, position: Int) {
            val bg = if (row.isCanceled) CANCELED_COLOR else if (position % 2 == 0) EVEN_COLOR else ODD_COLOR
            itemView.setBackgroundColor(bg)
            time.text = row.time
            item.text = row.item
            amount.text = row.amount
            cardNo.text = row.cardNo
            amount.setOnClickListener { onAmountClick(row) }
        }
    }

    companion object {
        private val EVEN_COLOR = android.graphics.Color.parseColor("#F7F9FB")
        private val ODD_COLOR = android.graphics.Color.WHITE
        private val CANCELED_COLOR = android.graphics.Color.parseColor("#FDEEEF")
    }
}
