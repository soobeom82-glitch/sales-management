package com.example.vmmswidget.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OrderHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OrderHistoryDatabase : RoomDatabase() {
    abstract fun orderHistoryDao(): OrderHistoryDao

    companion object {
        @Volatile private var instance: OrderHistoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE order_history
                    ADD COLUMN remark TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): OrderHistoryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrderHistoryDatabase::class.java,
                    "vmms_order_history.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
