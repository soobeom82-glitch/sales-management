package com.example.vmmswidget.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProductMappingEntity)

    @Query("SELECT * FROM product_mapping ORDER BY colNo ASC")
    suspend fun getAll(): List<ProductMappingEntity>

    @Query("SELECT * FROM product_mapping WHERE colNo = :colNo LIMIT 1")
    suspend fun getByColNo(colNo: String): ProductMappingEntity?

    @Query("SELECT * FROM product_mapping WHERE colNo IN (:colNos)")
    suspend fun getByColNos(colNos: List<String>): List<ProductMappingEntity>

    @Query("DELETE FROM product_mapping WHERE colNo = :colNo")
    suspend fun deleteByColNo(colNo: String)
}

