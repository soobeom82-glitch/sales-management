package com.example.vmmswidget.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OrderDao {
    @Query("SELECT * FROM order_category ORDER BY createdAt DESC")
    suspend fun getCategories(): List<OrderCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: OrderCategoryEntity): Long

    @Query("SELECT * FROM order_category WHERE name = :name LIMIT 1")
    suspend fun findCategoryByName(name: String): OrderCategoryEntity?

    @Query("SELECT * FROM order_category WHERE id = :categoryId LIMIT 1")
    suspend fun findCategoryById(categoryId: Long): OrderCategoryEntity?

    @Delete
    suspend fun deleteCategory(category: OrderCategoryEntity)

    @Query("UPDATE order_category SET name = :name WHERE id = :categoryId")
    suspend fun updateCategoryName(categoryId: Long, name: String)

    @Query("SELECT * FROM order_item WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    suspend fun getItemsByCategory(categoryId: Long): List<OrderItemEntity>

    @Query("SELECT * FROM order_item ORDER BY createdAt DESC")
    suspend fun getAllItems(): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: OrderItemEntity): Long

    @Query("SELECT * FROM order_planned ORDER BY createdAt DESC")
    suspend fun getPlannedItems(): List<OrderPlannedEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlannedItem(item: OrderPlannedEntity): Long

    @Query("DELETE FROM order_planned")
    suspend fun clearPlannedItems()

    @Query("UPDATE order_planned SET selected = :selected WHERE id = :plannedId")
    suspend fun updatePlannedSelected(plannedId: Long, selected: Boolean)

    @Query("SELECT * FROM order_planned WHERE selected = 1 ORDER BY createdAt DESC")
    suspend fun getSelectedPlannedItems(): List<OrderPlannedEntity>

    @Query("DELETE FROM order_planned WHERE id IN (:plannedIds)")
    suspend fun deletePlannedByIds(plannedIds: List<Long>)

    @Query("DELETE FROM order_planned WHERE orderItemId = :orderItemId")
    suspend fun deletePlannedByOrderItemId(orderItemId: Long): Int

    @Query("DELETE FROM order_item WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long): Int

    @Query("UPDATE order_item SET name = :name WHERE id = :itemId")
    suspend fun updateItemName(itemId: Long, name: String)

    @Query("UPDATE order_item SET name = :name, categoryId = :categoryId WHERE id = :itemId")
    suspend fun updateItem(itemId: Long, name: String, categoryId: Long)

    @Query("UPDATE order_item SET categoryId = :categoryId WHERE id = :itemId")
    suspend fun updateItemCategory(itemId: Long, categoryId: Long)

    @Query("DELETE FROM order_item")
    suspend fun deleteAllItems()

    @Query("DELETE FROM order_category WHERE id != :keepCategoryId")
    suspend fun deleteAllCategoriesExcept(keepCategoryId: Long)

    @Query("UPDATE order_item SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun moveItemsToCategory(fromCategoryId: Long, toCategoryId: Long)

    @Transaction
    suspend fun moveItemsAndDeleteCategory(
        category: OrderCategoryEntity,
        uncategorizedId: Long
    ) {
        moveItemsToCategory(category.id, uncategorizedId)
        deleteCategory(category)
    }

    @Transaction
    suspend fun deleteItemAndPlanned(orderItemId: Long): Int {
        deletePlannedByOrderItemId(orderItemId)
        return deleteItemById(orderItemId)
    }

}
