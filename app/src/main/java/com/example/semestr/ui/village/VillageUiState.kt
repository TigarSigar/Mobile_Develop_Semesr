package com.example.semestr.ui.village

import com.example.semestr.data.local.BuildingEntity

data class VillageUiState(
    val nickname: String = "",
    val accumulatedTime: Long = 0,
    val globalTime: Long = 0,
    val buildings: List<BuildingEntity> = emptyList(), // Список зданий
    val isLoading: Boolean = true // Флаг загрузки
)