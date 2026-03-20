package com.example.timevillage.ui.timer

import com.example.timevillage.data.local.CategoryEntity

data class ImportedTvTask(
    val id: String,
    val title: String,
    val tag: String
)

data class TimerUiState(
    val sessionSeconds: Long = 0L,
    val isRunning: Boolean = false,
    val activeTargetId: String? = null,
    val activeImportedTaskId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val importedTasks: List<ImportedTvTask> = emptyList()
)
