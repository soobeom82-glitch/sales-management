package com.example.vmmswidget.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_planned",
    foreignKeys = [
        ForeignKey(
            entity = OrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["orderItemId"], unique = true)
    ]
)
data class OrderPlannedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderItemId: Long,
    val categoryId: Long,
    val itemName: String,
    val createdAt: Long,
    val selected: Boolean = true
)
