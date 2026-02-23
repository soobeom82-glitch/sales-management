package com.example.vmmswidget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_history")
data class OrderHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderedAt: Long,
    val itemsText: String,
    val remark: String = ""
)
