package com.example.semestr.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val level: Int = 1,
    val x: Int,
    val y: Int
)

@Entity(tableName = "user_info")
data class UserInfoEntity(
    @PrimaryKey val id: Int = 0,
    val nickname: String = "Player",
    val accumulatedTime: Long = 0,
    val globalTime: Long = 0
)