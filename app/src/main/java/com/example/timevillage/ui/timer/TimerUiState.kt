package com.example.timevillage.ui.timer

import com.example.timevillage.data.local.CategoryEntity

data class TimerUiState(
    val sessionSeconds: Long = 0L,
    val isRunning: Boolean = false,
    val activeCategoryId: Int? = null,
    val categories: List<CategoryEntity> = emptyList()
)