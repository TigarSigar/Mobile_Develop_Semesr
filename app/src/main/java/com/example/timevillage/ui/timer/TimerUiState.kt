package com.example.timevillage.ui.timer

import com.example.timevillage.data.local.CategoryEntity

data class ImportedTvTask(
    val id: String,
    val title: String,
    val tag: String,
    val colorHex: String,
    val position: Int
)

data class TimerListItem(
    val targetId: String,
    val title: String,
    val colorHex: String,
    val isImported: Boolean,
    val position: Int
)

data class TimerUiState(
    val displaySeconds: Long = 0L,
    val unsavedTotalSeconds: Long = 0L,
    val isRunning: Boolean = false, // Running from TV
    val isSharedRunning: Boolean = false,
    val activeSourceApp: String = "",
    val activeTargetId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val importedTasks: List<ImportedTvTask> = emptyList(),
    val orderedItems: List<TimerListItem> = emptyList(),
    val unsavedSecondsByTarget: Map<String, Long> = emptyMap()
)
