package com.example.vmmswidget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_mapping")
data class ProductMappingEntity(
    @PrimaryKey val colNo: String,
    val product: String,
    val actualProduct: String,
    val updatedAt: Long
)

