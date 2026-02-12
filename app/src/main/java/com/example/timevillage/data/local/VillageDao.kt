package com.example.timevillage.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VillageDao {
    @Query("SELECT * FROM buildings")
    fun getAllBuildings(): Flow<List<BuildingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuilding(building: BuildingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildings(buildings: List<BuildingEntity>) // Добавлено для списка

    @Query("DELETE FROM buildings") // Добавлено для очистки
    suspend fun deleteAllBuildings()

    @Query("SELECT * FROM user_info WHERE id = 0")
    fun getUserInfo(): Flow<UserInfoEntity?>

    @Query("SELECT * FROM user_info WHERE id = 0")
    suspend fun getUserInfoSync(): UserInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserInfo(userInfo: UserInfoEntity)

    // Запрос для обновления только времени
    @Query("UPDATE user_info SET accumulatedTime = :accumulated, globalTime = :global WHERE id = 0")
    suspend fun updateTimes(accumulated: Long, global: Long)

    @Transaction
    suspend fun updateAllData(accumulated: Long, global: Long, buildings: List<BuildingEntity>) {
        deleteAllBuildings()
        insertBuildings(buildings)
        updateTimes(accumulated, global)
    }

    // Остальные методы для категорий, если они тебе нужны
    @Query("SELECT * FROM categories ORDER BY position ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
}