package com.example.vmmswidget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "daily_sales")
data class SalesEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val amount: Int,
    val createdAt: Long
)

fun LocalDate.toKey(): String = toString()
