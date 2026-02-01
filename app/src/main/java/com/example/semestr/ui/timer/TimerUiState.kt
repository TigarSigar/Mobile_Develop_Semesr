package com.example.semestr.ui.timer

import com.example.semestr.data.local.CategoryEntity

data class TimerUiState(
    val sessionSeconds: Long = 0L,
    val isRunning: Boolean = false,
    val activeCategoryId: Int? = null,
    val categories: List<CategoryEntity> = emptyList()
)