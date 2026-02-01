package com.example.semestr.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.semestr.data.local.CategoryEntity
import com.example.semestr.data.repository.VillageRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TimerViewModel(private val repository: VillageRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            // Теперь категории приходят отсортированными (если в Dao прописан ORDER BY position)
            repository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun addCategory(name: String, colorHex: String = "#4CAF50") {
        viewModelScope.launch {
            val currentList = _uiState.value.categories
            val nextPos = if (currentList.isEmpty()) 0 else currentList.maxOf { it.position } + 1

            repository.updateCategory(
                CategoryEntity(
                    name = name,
                    colorHex = colorHex,
                    position = nextPos
                )
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun startTimer(categoryId: Int) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(isRunning = true, activeCategoryId = categoryId) }
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(sessionSeconds = it.sessionSeconds + 1) }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun finishSession() {
        val seconds = _uiState.value.sessionSeconds
        pauseTimer()
        viewModelScope.launch {
            if (seconds > 0) repository.updateTime(seconds)
            _uiState.update {
                it.copy(sessionSeconds = 0, isRunning = false, activeCategoryId = null)
            }
        }
    }

    fun updateCategoryColor(category: CategoryEntity, newColorHex: String) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(colorHex = newColorHex))
        }
    }
    fun updateCategoryName(category: CategoryEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = newName))
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        val seconds = _uiState.value.sessionSeconds

        viewModelScope.launch {
            if (seconds > 0) {
                repository.updateTime(seconds) // СОХРАНЕНИЕ ТУТ
            }
            _uiState.update {
                it.copy(sessionSeconds = 0, isRunning = false, activeCategoryId = null)
            }
        }
    }

    fun moveCategory(category: CategoryEntity, up: Boolean) {
        val currentList = _uiState.value.categories
        val index = currentList.indexOfFirst { it.id == category.id }
        val targetIndex = if (up) index - 1 else index + 1

        if (targetIndex in currentList.indices) {
            val targetCat = currentList[targetIndex]

            viewModelScope.launch(Dispatchers.IO) {
                val currentPos = if (category.position == targetCat.position) index else category.position
                val targetPos = if (category.position == targetCat.position) targetIndex else targetCat.position

                repository.updateCategory(category.copy(position = targetPos))
                repository.updateCategory(targetCat.copy(position = currentPos))
            }
        }
    }
}