package com.example.vmmswidget.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SalesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SalesEntity)

    @Query("SELECT * FROM daily_sales ORDER BY date DESC LIMIT :limit")
    suspend fun getLastDays(limit: Int): List<SalesEntity>

    @Query("SELECT * FROM daily_sales WHERE date < :today ORDER BY date DESC LIMIT :limit")
    suspend fun getLastDaysExcludingToday(today: String, limit: Int): List<SalesEntity>

    @Query("SELECT * FROM daily_sales ORDER BY date ASC")
    suspend fun getAll(): List<SalesEntity>

    @Query("DELETE FROM daily_sales WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
