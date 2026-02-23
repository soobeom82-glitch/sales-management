package com.example.vmmswidget.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R

data class ProductMappingRow(
    val colNo: String,
    val product: String,
    var actualProduct: String
)

class ProductMappingAdapter(
    private val items: MutableList<ProductMappingRow>
) : RecyclerView.Adapter<ProductMappingAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_mapping, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    fun submit(newItems: List<ProductMappingRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentItems(): List<ProductMappingRow> = items.toList()

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colNo = itemView.findViewById<TextView>(R.id.map_col_no)
        private val product = itemView.findViewById<TextView>(R.id.map_product)
        private val actualProduct = itemView.findViewById<EditText>(R.id.map_actual_product)
        private var watcher: TextWatcher? = null

        fun bind(row: ProductMappingRow, position: Int) {
            itemView.setBackgroundColor(if (position % 2 == 0) EVEN_COLOR else ODD_COLOR)
            colNo.text = row.colNo
            product.text = row.product

            watcher?.let { actualProduct.removeTextChangedListener(it) }
            actualProduct.setText(row.actualProduct)
            watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    row.actualProduct = s?.toString().orEmpty()
                }
            }
            actualProduct.addTextChangedListener(watcher)
        }
    }

    companion object {
        private val EVEN_COLOR = android.graphics.Color.parseColor("#F7F9FB")
        private val ODD_COLOR = android.graphics.Color.WHITE
    }
}

