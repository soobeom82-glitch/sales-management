package com.example.vmmswidget.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EasyShopSalesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EasyShopSalesEntity)

    @Query("SELECT * FROM easyshop_daily_sales WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): EasyShopSalesEntity?

    @Query("SELECT * FROM easyshop_daily_sales ORDER BY date DESC LIMIT :limit")
    suspend fun getLastDays(limit: Int): List<EasyShopSalesEntity>

    @Query("SELECT * FROM easyshop_daily_sales WHERE date < :today ORDER BY date DESC LIMIT :limit")
    suspend fun getLastDaysExcludingToday(today: String, limit: Int): List<EasyShopSalesEntity>

    @Query("SELECT * FROM easyshop_daily_sales ORDER BY date ASC")
    suspend fun getAll(): List<EasyShopSalesEntity>

    @Query("DELETE FROM easyshop_daily_sales WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
