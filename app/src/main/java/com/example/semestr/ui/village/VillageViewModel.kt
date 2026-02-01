package com.example.semestr.ui.village

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.semestr.R
import com.example.semestr.data.local.BuildingEntity
import com.example.semestr.data.repository.VillageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Модель для магазина
data class ShopItem(
    val type: String,
    val name: String,
    val cost: Long,
    val imageRes: Int,
    val frames: Int
)

class VillageViewModel(val repository: VillageRepository) : ViewModel() {

    // Подписка на данные из БД
    val uiState: StateFlow<VillageUiState> = combine(
        repository.allBuildings,
        repository.userInfo
    ) { buildings, user ->
        VillageUiState(
            buildings = buildings,
            accumulatedTime = user?.accumulatedTime ?: 0,
            globalTime = user?.globalTime ?: 0,
            nickname = user?.nickname ?: "Игрок"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VillageUiState())

    // Настройки магазина под твои файлы house1, house2, house3
    val shopItems = listOf(
        // Тип, Название, Цена, Картинка, КОЛИЧЕСТВО КАДРОВ
        ShopItem("HOUSE", "Домик", 0L, R.drawable.house1, 3),
        ShopItem("FORGE", "Кузница", 0L, R.drawable.forge1, 2)
    )

    init {
        viewModelScope.launch {
            // Проверяем первый список из потока данных
            val currentBuildings = repository.allBuildings.first()
            if (currentBuildings.isEmpty()) {
                // Создаем тот самый стартовый MAIN уровень 0
                repository.insertBuilding(
                    BuildingEntity(type = "MAIN", x = 0, y = 0, level = 0)
                )
            }
        }
    }

    fun getFrameCount(type: String, level: Int): Int {
        return when (type) {
            "MAIN" -> if (level == 0) 16 else 4
            "HOUSE" -> 3
            "FORGE" -> 2
            else -> 1
        }
    }

    fun getGridSize(): Int {
        // Ищем уровень главного здания
        val mainBuilding = uiState.value.buildings.find { it.type == "MAIN" }
        val level = mainBuilding?.level ?: 0

        // Логика расширения:
        // Level 0 -> 3 (3x3)
        // Level 1 -> 5 (5x5)
        // Level 2 -> 7 (7x7) и так далее
        return 3 + (level * 2)
    }

    // Обновим также проверку возможности постройки
    fun canBuildNew(): Boolean {
        val mainBuilding = uiState.value.buildings.find { it.type == "MAIN" }
        val currentLevel = mainBuilding?.level ?: 0

        // Условие: строить домики можно только если ГЗ выше 0 уровня
        if (currentLevel == 0) return false

        // Максимальное кол-во зданий теперь тоже зависит от размера поля
        val gridSize = getGridSize()
        return uiState.value.buildings.size < (gridSize * gridSize)
    }

    fun buyBuilding(type: String, cost: Long, x: Int, y: Int) {
        viewModelScope.launch {
            repository.spendTime(cost)
            repository.insertBuilding(BuildingEntity(type = type, x = x, y = y, level = 1))
        }
    }

    fun upgradeBuilding(building: BuildingEntity, cost: Long) {
        viewModelScope.launch {
            repository.spendTime(cost)
            repository.insertBuilding(building.copy(level = building.level + 1))
        }
    }

    fun canUpgradeBuilding(building: BuildingEntity) = building.level < 3

    fun updateNickname(newName: String) {
        viewModelScope.launch { repository.updateNickname(newName) }
    }

    // Выбор ресурса по уровню
    fun getBuildingRes(type: String, level: Int, x: Int = 100, y: Int = 100): Int {
        // 1. Главное здание
        if (type == "MAIN" || (x == 0 && y == 0)) {
            return when (level) {
                0 -> R.drawable.main0
                1 -> R.drawable.main1
                // 2 -> R.drawable.main2
                else -> R.drawable.main1
            }
        }

        // 2. Кузница (FORGE)
        if (type == "FORGE") {
            return when (level) {
                1 -> R.drawable.forge1
                // 2 -> R.drawable.forge2
                else -> R.drawable.forge1
            }
        }

        // 3. Дома (HOUSE)
        return when (level) {
            1 -> R.drawable.house1
            2 -> R.drawable.house2
            3 -> R.drawable.house3
            4 -> R.drawable.house4
            5 -> R.drawable.house5
            else -> R.drawable.house1
        }
    }
}