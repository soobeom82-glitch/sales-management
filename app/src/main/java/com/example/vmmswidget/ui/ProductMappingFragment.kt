package com.example.vmmswidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.ProductMappingEntity
import com.example.vmmswidget.net.TransactionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductMappingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_product_mapping, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.mapping_list)
        val empty = view.findViewById<TextView>(R.id.mapping_empty)
        val saveBtn = view.findViewById<android.widget.Button>(R.id.mapping_save)
        val adapter = ProductMappingAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val rows = loadRows()
            adapter.submit(rows)
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }

        saveBtn.setOnClickListener {
            saveBtn.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val now = System.currentTimeMillis()
                val payload = adapter.currentItems().map {
                    ProductMappingEntity(
                        colNo = it.colNo,
                        product = it.product,
                        actualProduct = it.actualProduct,
                        updatedAt = now
                    )
                }
                TransactionsRepository(requireContext()).saveProductMappings(payload)
                saveBtn.isEnabled = true
                android.widget.Toast.makeText(requireContext(), "설정 저장 완료", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadRows(): List<ProductMappingRow> = withContext(Dispatchers.IO) {
        val repo = TransactionsRepository(requireContext())
        val saved = repo.getProductMappings().associateBy { it.colNo }
        val candidates = repo.fetchProductMappingCandidates()

        val colOrder = LinkedHashSet<String>()
        candidates.forEach { colOrder.add(it.colNo) }
        saved.keys.forEach { colOrder.add(it) }

        colOrder.sortedWith { a, b ->
            val ai = a.toIntOrNull()
            val bi = b.toIntOrNull()
            when {
                ai != null && bi != null -> ai.compareTo(bi)
                ai != null -> -1
                bi != null -> 1
                else -> a.compareTo(b)
            }
        }.map { colNo ->
            val cand = candidates.firstOrNull { it.colNo == colNo }
            val mapped = saved[colNo]
            val baseProduct = mapped?.product ?: cand?.product ?: "-"
            val actualValue = mapped?.actualProduct?.takeIf { it.isNotBlank() } ?: baseProduct
            ProductMappingRow(
                colNo = colNo,
                product = baseProduct,
                actualProduct = actualValue
            )
        }
    }
}
