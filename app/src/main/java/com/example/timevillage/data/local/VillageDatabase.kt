package com.example.timevillage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BuildingEntity::class, CategoryEntity::class, UserInfoEntity::class],
    version = 22,
    exportSchema = false
)
abstract class VillageDatabase : RoomDatabase() {
    abstract fun villageDao(): VillageDao

    companion object {
        @Volatile
        private var INSTANCE: VillageDatabase? = null

        fun getDatabase(context: Context): VillageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VillageDatabase::class.java,
                    "village_database"
                )
                    // Эта строка позволяет базе просто удалиться и создаться заново
                    // при изменении версии, что предотвращает вылеты при запуске
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}