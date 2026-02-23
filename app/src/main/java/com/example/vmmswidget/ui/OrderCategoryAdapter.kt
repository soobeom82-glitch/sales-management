package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.OrderCategoryEntity

class OrderCategoryAdapter(
    private val onDelete: (OrderCategoryEntity) -> Unit
) : RecyclerView.Adapter<OrderCategoryAdapter.VH>() {

    private val items = mutableListOf<OrderCategoryEntity>()

    fun submit(list: List<OrderCategoryEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_category, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.text_category_name)
        private val delete = view.findViewById<TextView>(R.id.button_category_delete)

        fun bind(item: OrderCategoryEntity) {
            name.text = item.name
            delete.setOnClickListener { onDelete(item) }
        }
    }
}
