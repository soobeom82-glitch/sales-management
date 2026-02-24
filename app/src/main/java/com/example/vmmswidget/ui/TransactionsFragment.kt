package com.example.vmmswidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.net.TransactionsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class TransactionsFragment : Fragment() {
    private var currentStart: LocalDate = LocalDate.now().minusDays(2)
    private var currentEnd: LocalDate = LocalDate.now()
    private val seenIds = LinkedHashSet<String>()
    private val allItems = mutableListOf<TransactionRow>()
    private var isLoadingBottom = false
    private var currentPage = 1
    private var currentTotalPages = 1
    private var searchQuery: String = ""
    private var certParam: String? = null
    private var approvalDialog: android.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.transactions_list)
        val empty = view.findViewById<TextView>(R.id.transactions_empty)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.transactions_swipe)
        val bottomProgress = view.findViewById<View>(R.id.transactions_bottom_progress)
        val requestCodeButton = view.findViewById<android.widget.Button>(R.id.transactions_request_code)
        val searchInput = view.findViewById<android.widget.EditText>(R.id.transactions_search)
        val searchClear = view.findViewById<android.widget.TextView>(R.id.transactions_search_clear)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = TransactionsAdapter(mutableListOf()) { row ->
            val intent = android.content.Intent(requireContext(), TransactionDetailActivity::class.java).apply {
                putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_NO, row.id)
                putExtra(TransactionDetailActivity.EXTRA_TERMINAL_ID, row.terminalId)
            }
            startActivity(intent)
        }
        recycler.adapter = adapter
        empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilter(adapter, empty)
            }
        })
        searchClear.setOnClickListener {
            searchInput.setText("")
        }

        requestCodeButton.setOnClickListener {
            requestCodeButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val param = TransactionsRepository(requireContext()).requestCancelInit()
                requestCodeButton.isEnabled = true
                if (param.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), "승인번호 요청 실패", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    certParam = param
                    showApprovalInputDialog()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Log.i("Vmms", "Transactions init range: start=${currentStart}, end=${currentEnd}")
            val result = runCatching {
                TransactionsRepository(requireContext()).fetchTransactions(currentStart, currentEnd, 1)
            }.getOrElse { com.example.vmmswidget.net.TransactionsRepository.PageResult(emptyList(), 0) }
            seenIds.clear()
            allItems.clear()
            result.rows.forEach { if (it.id.isNotBlank()) seenIds.add(it.id) }
            allItems.addAll(result.rows)
            applyFilter(adapter, empty)
            currentPage = 1
            currentTotalPages = if (result.totalPages <= 0) 1 else result.totalPages
        }

        swipe.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val end = LocalDate.now()
                val start = end.minusDays(2)
                Log.i("Vmms", "Transactions refresh range: start=${start}, end=${end}")
                val result = runCatching {
                    TransactionsRepository(requireContext()).fetchTransactions(start, end, 1)
                }.getOrElse { com.example.vmmswidget.net.TransactionsRepository.PageResult(emptyList(), 0) }
                val newItems = result.rows.filter { it.id.isBlank() || !seenIds.contains(it.id) }
                newItems.forEach { if (it.id.isNotBlank()) seenIds.add(it.id) }
                if (newItems.isNotEmpty()) {
                    allItems.addAll(0, newItems)
                }
                currentStart = start
                currentEnd = end
                currentPage = 1
                currentTotalPages = if (result.totalPages <= 0) 1 else result.totalPages
                applyFilter(adapter, empty)
                swipe.isRefreshing = false
            }
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (isLoadingBottom) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val last = lm.findLastCompletelyVisibleItemPosition()
                if (last == adapter.itemCount - 1) {
                    isLoadingBottom = true
                    bottomProgress.visibility = View.VISIBLE
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (currentPage < currentTotalPages) {
                            val nextPage = currentPage + 1
                            Log.i("Vmms", "Transactions load nextPage=${nextPage}, range=${currentStart}~${currentEnd}")
                            val result = runCatching {
                                TransactionsRepository(requireContext()).fetchTransactions(currentStart, currentEnd, nextPage)
                            }.getOrElse { com.example.vmmswidget.net.TransactionsRepository.PageResult(emptyList(), 0) }
                            val newItems = result.rows.filter { it.id.isBlank() || !seenIds.contains(it.id) }
                            newItems.forEach { if (it.id.isNotBlank()) seenIds.add(it.id) }
                            if (newItems.isNotEmpty()) {
                                allItems.addAll(newItems)
                            }
                            applyFilter(adapter, empty)
                            currentPage = nextPage
                        } else {
                            val prevDay = currentStart.minusDays(1)
                            Log.i("Vmms", "Transactions load prevDay=${prevDay}, currentStart=${currentStart}, currentEnd=${currentEnd}")
                            val result = runCatching {
                                TransactionsRepository(requireContext()).fetchTransactions(prevDay, prevDay, 1)
                            }.getOrElse { com.example.vmmswidget.net.TransactionsRepository.PageResult(emptyList(), 0) }
                            val newItems = result.rows.filter { it.id.isBlank() || !seenIds.contains(it.id) }
                            newItems.forEach { if (it.id.isNotBlank()) seenIds.add(it.id) }
                            if (newItems.isNotEmpty()) {
                                allItems.addAll(newItems)
                            }
                            applyFilter(adapter, empty)
                            currentStart = prevDay
                            currentPage = 1
                            currentTotalPages = if (result.totalPages <= 0) 1 else result.totalPages
                        }
                        bottomProgress.visibility = View.GONE
                        isLoadingBottom = false
                    }
                }
            }
        })
    }

    private fun applyFilter(adapter: TransactionsAdapter, empty: TextView) {
        val q = searchQuery.trim()
        val filtered = if (q.isEmpty()) {
            allItems
        } else {
            val needle = q.lowercase()
            allItems.filter {
                it.time.lowercase().contains(needle) ||
                    it.item.lowercase().contains(needle) ||
                    it.amount.lowercase().contains(needle) ||
                    it.cardNo.lowercase().contains(needle)
            }
        }
        adapter.submit(filtered)
        empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showApprovalInputDialog() {
        approvalDialog?.dismiss()
        val dialogView = layoutInflater.inflate(R.layout.dialog_approval_code, null, false)
        val input = dialogView.findViewById<android.widget.EditText>(R.id.approval_input).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        approvalDialog = dialog
        dialog.setOnDismissListener {
            if (approvalDialog === dialog) {
                approvalDialog = null
            }
        }

        dialogView.findViewById<android.widget.Button>(R.id.approval_submit).setOnClickListener {
            val code = input.text?.toString()?.trim().orEmpty()
            if (code.length != 6) {
                android.widget.Toast.makeText(requireContext(), "6자리 번호를 입력하세요", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val submitBtn = dialogView.findViewById<android.widget.Button>(R.id.approval_submit)
                submitBtn.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = TransactionsRepository(requireContext()).submitCancelCertCode(code, certParam)
                    submitBtn.isEnabled = true
                    if (ok) {
                        android.widget.Toast.makeText(requireContext(), "인증 완료", android.widget.Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(requireContext(), "인증번호 확인 실패", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialogView.findViewById<android.widget.Button>(R.id.approval_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        approvalDialog?.dismiss()
        approvalDialog = null
        super.onDestroyView()
    }
}
