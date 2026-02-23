package com.example.vmmswidget.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrderHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: OrderHistoryEntity): Long

    @Query("SELECT * FROM order_history ORDER BY orderedAt DESC")
    suspend fun getAll(): List<OrderHistoryEntity>

    @Delete
    suspend fun delete(history: OrderHistoryEntity)

    @Query("UPDATE order_history SET remark = :remark WHERE id = :historyId")
    suspend fun updateRemark(historyId: Long, remark: String)
}
