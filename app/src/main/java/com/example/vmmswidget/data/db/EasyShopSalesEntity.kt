package com.example.vmmswidget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "easyshop_daily_sales")
data class EasyShopSalesEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val amount: Int,
    val createdAt: Long
)
