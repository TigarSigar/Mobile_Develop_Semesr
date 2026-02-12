package com.example.timevillage.data

data class GameConfig(
    val buildings: List<BuildingCategoryConfig>
)

data class BuildingCategoryConfig(
    val type: String,
    val name_by_lvl: List<String>? = null,
    val house_slots_by_lvl: List<Int>? = null,
    val unlock_at_main_lvl: Int = 1,
    val levels: List<BuildingLevelConfig>
)

data class BuildingLevelConfig(
    val lvl: Int,
    val cost: Int,
    val time: Int,
    val main_lvl_req: Int = 1
)