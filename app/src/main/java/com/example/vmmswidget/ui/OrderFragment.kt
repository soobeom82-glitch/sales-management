package com.example.vmmswidget.ui

import android.os.Bundle
import android.graphics.Paint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vmmswidget.R
import com.example.vmmswidget.data.db.AppDatabase
import com.example.vmmswidget.data.db.OrderCategoryEntity
import com.example.vmmswidget.data.db.OrderHistoryDatabase
import com.example.vmmswidget.data.db.OrderItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderFragment : Fragment() {
    private enum class SubTab { ORDER, HISTORY }

    private lateinit var treeAdapter: OrderTreeAdapter
    private lateinit var historyAdapter: OrderHistoryAdapter
    private val uncategorizedName = "미분류"
    private val defaultCategoriesOrdered = listOf(
        "🫘 원두",
        "🥄 분말",
        "🥤 일회용품",
        "🫖 티백",
        "🧃 RTD",
        "🍪 다쿠아즈"
    )
    private val defaultItemsByCategory = linkedMapOf(
        "🫘 원두" to listOf(
            "🫘 원두"
        ),
        "🍪 다쿠아즈" to listOf(
            "🍓 딸기",
            "🍫 초코",
            "🧂 솔트",
            "🧀 치즈",
            "🍬 캬라멜"
        ),
        "🥄 분말" to listOf(
            "🥛 전지분",
            "🍫 초코분",
            "🧋 아이스티",
            "🍋 핑크레몬에이드"
        ),
        "🥤 일회용품" to listOf(
            "🧊 아이스컵",
            "☕ 핫컵",
            "🥤 아이스컵리드",
            "🔒 핫컵리드",
            "🧣 컵홀더",
            "🧺 컵캐리어",
            "🥤 빨대",
            "🥢 커피스틱",
            "🧻 냅킨"
        ),
        "🧃 RTD" to listOf(
            "💧 생수",
            "🫧 탄산수",
            "👑 더킹"
        )
    )
    private var currentSubTab: SubTab = SubTab.ORDER
    private val categoryExpandedState = mutableMapOf<Long, Boolean>()
    private val removeAllCategoriesOnceKey = "order_remove_all_categories_once_v1"
    private val seedDefaultCategoriesOnceKey = "order_seed_default_categories_once_v1"
    private val seedDefaultItemsOnceKey = "order_seed_default_items_once_v1"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tabOrder = view.findViewById<TextView>(R.id.tab_order)
        val tabHistory = view.findViewById<TextView>(R.id.tab_order_history)
        val addCategoryButton = view.findViewById<TextView>(R.id.button_add_category)
        val clearPlannedButton = view.findViewById<TextView>(R.id.button_clear_planned)
        val submitOrderButton = view.findViewById<TextView>(R.id.button_submit_order)
        val orderPlannedLayout = view.findViewById<View>(R.id.layout_order_planned)
        val orderHeader = view.findViewById<View>(R.id.layout_order_header)
        val listRecycler = view.findViewById<RecyclerView>(R.id.recycler_order_tree)
        val historyLayout = view.findViewById<View>(R.id.layout_order_history)
        val historyRecycler = view.findViewById<RecyclerView>(R.id.recycler_order_history)
        val historyEmpty = view.findViewById<TextView>(R.id.text_order_history_empty)
        treeAdapter = OrderTreeAdapter(
            onCategoryLongPress = { anchor, category -> showCategoryMenu(anchor, category) },
            onToggleCategory = { category ->
                val current = categoryExpandedState[category.id] ?: true
                categoryExpandedState[category.id] = !current
                loadTree()
            },
            onAddItemClick = { category -> showAddItemDialog(category) },
            onItemLongPress = { anchor, item -> showItemMenu(anchor, item) },
            onItemClick = { item ->
                lifecycleScope.launch {
                    val dao = AppDatabase.get(requireContext()).orderDao()
                    val inserted = withContext(Dispatchers.IO) {
                        val category = dao.findCategoryById(item.categoryId)
                        val categoryEmoji = extractLeadingEmoji(category?.name.orEmpty())
                        val displayItemName = if (categoryEmoji.isBlank()) {
                            stripLeadingEmoji(item.name)
                        } else {
                            "$categoryEmoji ${stripLeadingEmoji(item.name)}"
                        }
                        dao.insertPlannedItem(
                            com.example.vmmswidget.data.db.OrderPlannedEntity(
                                orderItemId = item.id,
                                categoryId = item.categoryId,
                                itemName = displayItemName,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (inserted > 0L) {
                        toast("발주 예정에 추가되었습니다.")
                        loadTree()
                    } else {
                        toast("이미 발주 예정에 있습니다.")
                    }
                }
            }
        )
        listRecycler.layoutManager = LinearLayoutManager(requireContext())
        listRecycler.adapter = treeAdapter
        historyAdapter = OrderHistoryAdapter(
            mutableListOf(),
            onHistoryClick = { history -> showHistoryDetailDialog(history) }
        )
        historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        historyRecycler.adapter = historyAdapter
        addCategoryButton.setOnClickListener { showAddCategoryDialog() }
        clearPlannedButton.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.get(requireContext()).orderDao().clearPlannedItems()
                }
                loadTree()
                toast("발주 예정이 비워졌습니다.")
            }
        }
        submitOrderButton.setOnClickListener {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    val dao = AppDatabase.get(requireContext()).orderDao()
                    val historyDao = OrderHistoryDatabase.get(requireContext()).orderHistoryDao()
                    val selectedPlanned = dao.getSelectedPlannedItems()
                    if (selectedPlanned.isEmpty()) {
                        return@withContext false
                    }

                    val categoryMap = dao.getCategories().associateBy { it.id }
                    val itemMap = dao.getAllItems().associateBy { it.id }
                    val historyText = selectedPlanned.joinToString(", ") { planned ->
                        val categoryName = stripLeadingEmoji(categoryMap[planned.categoryId]?.name.orEmpty())
                            .ifBlank { uncategorizedName }
                        val itemName = stripLeadingEmoji(itemMap[planned.orderItemId]?.name ?: planned.itemName)
                        "$categoryName:$itemName"
                    }

                    val inserted = historyDao.insert(
                        com.example.vmmswidget.data.db.OrderHistoryEntity(
                            orderedAt = System.currentTimeMillis(),
                            itemsText = historyText
                        )
                    )
                    if (inserted <= 0L) return@withContext false
                    dao.deletePlannedByIds(selectedPlanned.map { it.id })
                    true
                }
                if (!result) {
                    toast("선택된 발주 예정 항목이 없습니다.")
                } else {
                    toast("발주 이력에 기록되었습니다.")
                    loadTree()
                }
            }
        }
        tabOrder.setOnClickListener {
            if (currentSubTab != SubTab.ORDER) {
                currentSubTab = SubTab.ORDER
                renderSubTab(
                    tabOrder,
                    tabHistory,
                    orderPlannedLayout,
                    orderHeader,
                    listRecycler,
                    historyLayout,
                    historyEmpty
                )
                loadTree()
            }
        }
        tabHistory.setOnClickListener {
            if (currentSubTab != SubTab.HISTORY) {
                currentSubTab = SubTab.HISTORY
                renderSubTab(
                    tabOrder,
                    tabHistory,
                    orderPlannedLayout,
                    orderHeader,
                    listRecycler,
                    historyLayout,
                    historyEmpty
                )
            }
        }
        renderSubTab(
            tabOrder,
            tabHistory,
            orderPlannedLayout,
            orderHeader,
            listRecycler,
            historyLayout,
            historyEmpty
        )
        loadTree()
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.recycler_order_tree)?.adapter = null
        view?.findViewById<RecyclerView>(R.id.recycler_order_history)?.adapter = null
        super.onDestroyView()
    }

    private fun loadTree() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.get(requireContext()).orderDao()
            withContext(Dispatchers.IO) {
                val uncategorizedId = ensureUncategorizedCategory(dao)
                runCleanupOnce(dao, uncategorizedId)
                runDefaultCategorySeedOnce(dao)
                runDefaultItemSeedOnce(dao)
            }
            val categories = withContext(Dispatchers.IO) { dao.getCategories() }
            val allItems = withContext(Dispatchers.IO) { dao.getAllItems() }
            val plannedItems = withContext(Dispatchers.IO) { dao.getPlannedItems() }
            val histories = withContext(Dispatchers.IO) {
                OrderHistoryDatabase.get(requireContext()).orderHistoryDao().getAll()
            }
            val itemMap = allItems.groupBy { it.categoryId }

            val rows = mutableListOf<OrderTreeAdapter.Row>()
            val categoryPriority = defaultCategoriesOrdered.withIndex().associate { it.value to it.index }
            val orderedCategories = categories.sortedWith(
                compareBy<OrderCategoryEntity> { if (isUncategorized(it.name)) 1 else 0 }
                    .thenBy { categoryPriority[it.name] ?: Int.MAX_VALUE }
                    .thenBy { it.name }
            )
            orderedCategories.forEach { category ->
                val expanded = categoryExpandedState[category.id] ?: true
                rows.add(
                    OrderTreeAdapter.Row.Category(
                        category = category,
                        items = itemMap[category.id].orEmpty().sortedBy { stripLeadingEmoji(it.name) },
                        expanded = expanded
                    )
                )
            }
            treeAdapter.submit(rows)
            renderPlannedItems(plannedItems)
            historyAdapter.submit(histories)
            view?.findViewById<TextView>(R.id.text_order_history_empty)?.visibility =
                if (histories.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun renderSubTab(
        tabOrder: TextView,
        tabHistory: TextView,
        orderPlannedLayout: View,
        orderHeader: View,
        listRecycler: RecyclerView,
        historyLayout: View,
        historyEmpty: TextView
    ) {
        val isOrder = currentSubTab == SubTab.ORDER
        tabOrder.setBackgroundResource(
            if (isOrder) R.drawable.order_subtab_selected_bg else android.R.color.transparent
        )
        tabHistory.setBackgroundResource(
            if (isOrder) android.R.color.transparent else R.drawable.order_subtab_selected_bg
        )
        tabOrder.setTextColor(if (isOrder) 0xFF1E4FA3.toInt() else 0xFF6B7280.toInt())
        tabHistory.setTextColor(if (isOrder) 0xFF6B7280.toInt() else 0xFF1E4FA3.toInt())
        orderPlannedLayout.visibility = if (isOrder) View.VISIBLE else View.GONE
        orderHeader.visibility = if (isOrder) View.VISIBLE else View.GONE
        listRecycler.visibility = if (isOrder) View.VISIBLE else View.GONE
        historyLayout.visibility = if (isOrder) View.GONE else View.VISIBLE
        if (isOrder) {
            historyEmpty.visibility = View.GONE
        }
    }

    private fun renderPlannedItems(plannedItems: List<com.example.vmmswidget.data.db.OrderPlannedEntity>) {
        val container = view?.findViewById<ViewGroup>(R.id.planned_items_container) ?: return
        container.removeAllViews()
        if (plannedItems.isEmpty()) {
            container.addView(makePlannedBubble(null, "항목 없음", isPlaceholder = true))
            return
        }
        plannedItems.forEach { item ->
            container.addView(makePlannedBubble(item, item.itemName, isPlaceholder = false))
        }
    }

    private fun makePlannedBubble(
        plannedItem: com.example.vmmswidget.data.db.OrderPlannedEntity?,
        text: String,
        isPlaceholder: Boolean
    ): View {
        val context = requireContext()
        val isSelected = plannedItem?.selected ?: true
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(
                TextView(context).apply {
                    this.text = text
                    setBackgroundResource(
                        if (isPlaceholder || isSelected) {
                            R.drawable.order_item_bubble
                        } else {
                            R.drawable.order_item_bubble_unselected
                        }
                    )
                    setTextColor(
                        when {
                            isPlaceholder -> 0xFF9CA3AF.toInt()
                            isSelected -> 0xFF4B5563.toInt()
                            else -> 0xFF9CA3AF.toInt()
                        }
                    )
                    paintFlags = if (!isPlaceholder && !isSelected) {
                        paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                    textSize = 13f
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                    if (!isPlaceholder && plannedItem != null) {
                        setOnClickListener {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.get(requireContext()).orderDao()
                                        .updatePlannedSelected(plannedItem.id, !plannedItem.selected)
                                }
                                loadTree()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun showCategoryMenu(anchor: View, category: OrderCategoryEntity) {
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        popup.menu.add("수정")
        popup.menu.add("삭제")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title.toString()) {
                "수정" -> {
                    if (isUncategorized(category.name)) {
                        toast("미분류 카테고리는 이름을 변경할 수 없습니다.")
                        return@setOnMenuItemClickListener true
                    }
                    showRenameDialog(
                        title = "카테고리 수정",
                        initial = category.name
                    ) { newName ->
                        lifecycleScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.get(requireContext())
                                        .orderDao()
                                        .updateCategoryName(category.id, newName)
                                }
                            }.onFailure {
                                toast("카테고리 수정 실패")
                            }
                            loadTree()
                        }
                    }
                }
                "삭제" -> {
                    if (isUncategorized(category.name)) {
                        toast("미분류 카테고리는 삭제할 수 없습니다.")
                        return@setOnMenuItemClickListener true
                    }
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val dao = AppDatabase.get(requireContext()).orderDao()
                            val uncategorizedId = ensureUncategorizedCategory(dao)
                            dao.moveItemsAndDeleteCategory(category, uncategorizedId)
                        }
                        loadTree()
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun showAddItemDialog(category: OrderCategoryEntity) {
        showRenameDialog(
            title = "${category.name} 항목 추가",
            initial = ""
        ) { newName ->
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(requireContext()).orderDao().insertItem(
                            OrderItemEntity(
                                categoryId = category.id,
                                name = newName,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }.onFailure {
                    toast("항목 추가 실패")
                }
                loadTree()
            }
        }
    }

    private fun showItemMenu(anchor: View, item: OrderItemEntity) {
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        popup.menu.add("수정")
        popup.menu.add("삭제")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title.toString()) {
                "수정" -> showEditItemDialog(item)
                "삭제" -> {
                    lifecycleScope.launch {
                        val removed = withContext(Dispatchers.IO) {
                            AppDatabase.get(requireContext()).orderDao().deleteItemAndPlanned(item.id)
                        }
                        if (removed > 0) {
                            toast("항목이 삭제되었습니다.")
                        } else {
                            toast("항목 삭제 실패")
                        }
                        loadTree()
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun showEditItemDialog(item: OrderItemEntity) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(requireContext()).orderDao()
            val categories = withContext(Dispatchers.IO) { dao.getCategories() }
            val sortedCategories = categories.sortedWith(
                compareBy<OrderCategoryEntity> { if (isUncategorized(it.name)) 1 else 0 }
                    .thenBy { stripLeadingEmoji(it.name) }
            )
            if (sortedCategories.isEmpty()) {
                toast("카테고리가 없습니다.")
                return@launch
            }

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(16)
                setPadding(pad, pad, pad, 0)
            }
            val nameInput = EditText(requireContext()).apply {
                setText(item.name)
                setSelection(item.name.length)
            }
            val categorySpinner = Spinner(requireContext())
            val categoryNames = sortedCategories.map { it.name }
            categorySpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                categoryNames
            )
            val selectedIdx = sortedCategories.indexOfFirst { it.id == item.categoryId }.coerceAtLeast(0)
            categorySpinner.setSelection(selectedIdx)

            container.addView(
                nameInput,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                categorySpinner,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            )

            AlertDialog.Builder(requireContext())
                .setTitle("항목 수정")
                .setView(container)
                .setPositiveButton("저장") { _, _ ->
                    val newName = nameInput.text?.toString()?.trim().orEmpty()
                    if (newName.isEmpty()) {
                        toast("이름을 입력하세요.")
                        return@setPositiveButton
                    }
                    val targetCategory = sortedCategories[categorySpinner.selectedItemPosition]
                    lifecycleScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                AppDatabase.get(requireContext()).orderDao()
                                    .updateItem(item.id, newName, targetCategory.id)
                            }
                        }.onFailure {
                            toast("항목 수정 실패")
                        }
                        loadTree()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun showAddCategoryDialog() {
        showRenameDialog(
            title = "카테고리 추가",
            initial = ""
        ) { categoryName ->
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(requireContext()).orderDao().insertCategory(
                            OrderCategoryEntity(
                                name = categoryName,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }.onFailure {
                    toast("카테고리 추가 실패")
                }
                loadTree()
            }
        }
    }

    private fun showRenameDialog(title: String, initial: String, onSubmit: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            setText(initial)
            setSelection(initial.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    toast("이름을 입력하세요.")
                    return@setPositiveButton
                }
                onSubmit(text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private suspend fun ensureUncategorizedCategory(
        dao: com.example.vmmswidget.data.db.OrderDao
    ): Long {
        val existing = dao.findCategoryByName(uncategorizedName)
        if (existing != null) return existing.id
        val inserted = dao.insertCategory(
            OrderCategoryEntity(
                name = uncategorizedName,
                createdAt = System.currentTimeMillis()
            )
        )
        if (inserted > 0) return inserted
        return dao.findCategoryByName(uncategorizedName)?.id ?: 0L
    }

    private fun isUncategorized(name: String): Boolean {
        return name == uncategorizedName
    }

    private suspend fun runCleanupOnce(
        dao: com.example.vmmswidget.data.db.OrderDao,
        uncategorizedId: Long
    ) {
        val prefs = requireContext().getSharedPreferences("order_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(removeAllCategoriesOnceKey, false)) return
        dao.deleteAllItems()
        dao.deleteAllCategoriesExcept(uncategorizedId)
        prefs.edit().putBoolean(removeAllCategoriesOnceKey, true).apply()
    }

    private suspend fun ensureDefaultCategories(dao: com.example.vmmswidget.data.db.OrderDao) {
        val existing = dao.getCategories().map { it.name }.toSet()
        defaultCategoriesOrdered.forEach { name ->
            if (name !in existing) {
                dao.insertCategory(
                    OrderCategoryEntity(
                        name = name,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun runDefaultCategorySeedOnce(dao: com.example.vmmswidget.data.db.OrderDao) {
        val prefs = requireContext().getSharedPreferences("order_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(seedDefaultCategoriesOnceKey, false)) return
        ensureDefaultCategories(dao)
        prefs.edit().putBoolean(seedDefaultCategoriesOnceKey, true).apply()
    }

    private suspend fun ensureDefaultItems(dao: com.example.vmmswidget.data.db.OrderDao) {
        val categories = dao.getCategories()
        val categoryIdByName = categories.associate { it.name to it.id }
        val existingItems = dao.getAllItems()
        val existingByCategory = existingItems.groupBy { it.categoryId }
            .mapValues { (_, items) -> items.map { it.name }.toSet() }

        defaultItemsByCategory.forEach { (categoryName, items) ->
            val categoryId = categoryIdByName[categoryName] ?: return@forEach
            val existingNames = existingByCategory[categoryId].orEmpty()
            items.forEach { itemName ->
                if (itemName !in existingNames) {
                    dao.insertItem(
                        OrderItemEntity(
                            categoryId = categoryId,
                            name = itemName,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun runDefaultItemSeedOnce(dao: com.example.vmmswidget.data.db.OrderDao) {
        val prefs = requireContext().getSharedPreferences("order_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(seedDefaultItemsOnceKey, false)) return
        ensureDefaultItems(dao)
        prefs.edit().putBoolean(seedDefaultItemsOnceKey, true).apply()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showHistoryDetailDialog(history: com.example.vmmswidget.data.db.OrderHistoryEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_order_history_detail, null, false)
        val timeText = dialogView.findViewById<TextView>(R.id.text_history_detail_time)
        val itemsText = dialogView.findViewById<TextView>(R.id.text_history_detail_items)
        val remarkText = dialogView.findViewById<TextView>(R.id.text_history_detail_remark)
        val remarkInput = dialogView.findViewById<android.widget.EditText>(R.id.input_history_remark)
        val saveRemarkButton = dialogView.findViewById<View>(R.id.button_history_save_remark)
        val deleteButton = dialogView.findViewById<View>(R.id.button_history_delete)
        val closeButton = dialogView.findViewById<View>(R.id.button_history_close)

        timeText.text = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.KOREA)
            .format(java.util.Date(history.orderedAt))
        itemsText.text = history.itemsText
        remarkText.text = history.remark.ifBlank { "-" }
        remarkInput.setText(history.remark)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        saveRemarkButton.setOnClickListener {
            val newRemark = remarkInput.text?.toString()?.trim().orEmpty()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    OrderHistoryDatabase.get(requireContext()).orderHistoryDao()
                        .updateRemark(history.id, newRemark)
                }
                remarkText.text = newRemark.ifBlank { "-" }
                toast("비고가 저장되었습니다.")
                loadTree()
            }
        }
        deleteButton.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    OrderHistoryDatabase.get(requireContext()).orderHistoryDao().delete(history)
                }
                dialog.dismiss()
                loadTree()
                toast("이력이 삭제되었습니다.")
            }
        }
        dialog.show()
    }

    private fun extractLeadingEmoji(text: String): String {
        return text.trim().split(" ").firstOrNull()?.takeIf { token ->
            token.any { !it.isLetterOrDigit() }
        }.orEmpty()
    }

    private fun stripLeadingEmoji(text: String): String {
        return text.trim().replace(Regex("^[^\\p{L}\\p{N}]+\\s+"), "").trim()
    }
}
