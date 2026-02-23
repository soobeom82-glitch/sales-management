package com.example.vmmswidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.OrderCategoryEntity
import com.example.vmmswidget.data.db.OrderItemEntity

class OrderTreeAdapter(
    private val onCategoryLongPress: (View, OrderCategoryEntity) -> Unit,
    private val onToggleCategory: (OrderCategoryEntity) -> Unit,
    private val onAddItemClick: (OrderCategoryEntity) -> Unit,
    private val onItemLongPress: (View, OrderItemEntity) -> Unit,
    private val onItemClick: (OrderItemEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Category(
            val category: OrderCategoryEntity,
            val items: List<OrderItemEntity>,
            val expanded: Boolean
        ) : Row()
    }

    private val rows = mutableListOf<Row>()

    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return CategoryVH(inflater.inflate(R.layout.item_order_tree_category, parent, false))
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CategoryVH).bind(rows[position] as Row.Category)
    }

    inner class CategoryVH(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.text_category_name)
        private val expandIcon = view.findViewById<TextView>(R.id.text_expand_icon)
        private val itemsContainer = view.findViewById<ViewGroup>(R.id.items_container)

        fun bind(row: Row.Category) {
            name.text = row.category.name
            expandIcon.text = " >"
            expandIcon.rotation = if (row.expanded) 90f else 0f
            val showMenuOnLongPress = View.OnLongClickListener {
                onCategoryLongPress(it, row.category)
                true
            }
            name.setOnClickListener { onToggleCategory(row.category) }
            expandIcon.setOnClickListener { onToggleCategory(row.category) }
            name.setOnLongClickListener(showMenuOnLongPress)
            expandIcon.setOnLongClickListener(showMenuOnLongPress)
            itemView.setOnLongClickListener(showMenuOnLongPress)

            itemsContainer.removeAllViews()
            itemsContainer.visibility = if (row.expanded) View.VISIBLE else View.GONE
            if (!row.expanded) return
            if (row.items.isEmpty()) {
                itemsContainer.addView(makeAddBubble(itemsContainer, row.category))
                return
            }
            row.items.forEach { item ->
                itemsContainer.addView(makeBubble(itemsContainer, item.name, item))
            }
            itemsContainer.addView(makeAddBubble(itemsContainer, row.category))
        }
    }

    private fun makeBubble(
        parent: ViewGroup,
        text: String,
        item: OrderItemEntity?
    ): View {
        val context = parent.context
        return TextView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
                bottomMargin = dp(8)
            }
            this.text = text
            setBackgroundResource(R.drawable.order_item_bubble)
            setTextColor(0xFF4B5563.toInt())
            textSize = 13f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            if (item != null) {
                setOnClickListener { onItemClick(item) }
                setOnLongClickListener {
                    onItemLongPress(it, item)
                    true
                }
            }
        }
    }

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun makeAddBubble(parent: ViewGroup, category: OrderCategoryEntity): View {
        val context = parent.context
        return TextView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
                bottomMargin = dp(8)
            }
            text = "항목추가+"
            setBackgroundResource(R.drawable.order_item_bubble)
            setTextColor(0xFF8A9BB5.toInt())
            textSize = 12f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onAddItemClick(category) }
        }
    }
}
