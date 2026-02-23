package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.OrderItemEntity

class OrderItemAdapter(
    private val onDelete: (OrderItemEntity) -> Unit
) : RecyclerView.Adapter<OrderItemAdapter.VH>() {

    private val items = mutableListOf<OrderItemEntity>()

    fun submit(list: List<OrderItemEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_item, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.text_item_name)
        private val delete = view.findViewById<TextView>(R.id.button_item_delete)

        fun bind(item: OrderItemEntity) {
            name.text = item.name
            delete.setOnClickListener { onDelete(item) }
        }
    }
}
