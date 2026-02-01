package com.example.semestr.data.repository

import com.example.semestr.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VillageRepository(private val villageDao: VillageDao) {

    val allBuildings: Flow<List<BuildingEntity>> = villageDao.getAllBuildings()
    val userInfo: Flow<UserInfoEntity?> = villageDao.getUserInfo()

    fun getAllCategories(): Flow<List<CategoryEntity>> = villageDao.getAllCategories()

    suspend fun addCategory(name: String) {
        villageDao.insertCategory(CategoryEntity(name = name))
    }

    suspend fun updateCategory(category: CategoryEntity) {
        villageDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        villageDao.deleteCategory(category)
    }

    suspend fun updateNickname(newNickname: String) {
        val current = userInfo.first()
        if (current == null) {
            villageDao.insertUserInfo(UserInfoEntity(nickname = newNickname))
        } else {
            villageDao.insertUserInfo(current.copy(nickname = newNickname))
        }
    }

    suspend fun updateTime(addedSeconds: Long) {
        val current = villageDao.getUserInfoSync() ?: UserInfoEntity()
        val updated = current.copy(
            accumulatedTime = current.accumulatedTime + addedSeconds,
            globalTime = current.globalTime + addedSeconds
        )
        villageDao.insertUserInfo(updated)
    }

    suspend fun spendTime(amount: Long) {
        val current = villageDao.getUserInfoSync() ?: UserInfoEntity()
        if (current.accumulatedTime >= amount) {
            villageDao.insertUserInfo(current.copy(
                accumulatedTime = current.accumulatedTime - amount
            ))
        }
    }

    suspend fun insertBuilding(building: BuildingEntity) {
        villageDao.insertBuilding(building)
    }
}