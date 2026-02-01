package com.example.semestr.ui.timer

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.semestr.data.local.CategoryEntity
import com.example.semestr.util.formatToTime

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Режимы интерфейса
    var isEditMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    // Состояния для редактирования
    var colorDialogTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var nameDialogTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var editNameValue by remember { mutableStateOf("") }

    val colorOptions = listOf(
        Color(0xFF4CAF50), // Зеленый
        Color(0xFF03A9F4), // Голубой
        Color(0xFF9C27B0), // Фиолетовый
        Color(0xFFF44336), // Красный
        Color(0xFFFFEB3B), // Желтый
        Color(0xFFFF9800), // Оранжевый
        Color(0xFFE91E63)  // Тот самый сочный Розовый
    )

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Категории", style = MaterialTheme.typography.headlineSmall)

            Row {
                if (isEditMode) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Green)
                    }
                }
                IconButton(onClick = { isEditMode = !isEditMode }) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = "Toggle Edit Mode",
                        tint = if (isEditMode) Color.Cyan else Color.White
                    )
                }
            }
        }

        // Таймер
        Text(
            text = state.sessionSeconds.formatToTime(),
            style = MaterialTheme.typography.displayLarge,
            color = if (state.isRunning) Color(0xFF4CAF50) else Color.White,
            modifier = Modifier.padding(vertical = 20.dp)
        )

        // Список категорий
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(state.categories, key = { _, cat -> cat.id }) { index, category ->
                CategoryRow(
                    category = category,
                    isEditMode = isEditMode,
                    isActive = state.activeCategoryId == category.id && state.isRunning,
                    onPlay = { viewModel.startTimer(category.id) },
                    onPause = { viewModel.pauseTimer() },
                    onDelete = { viewModel.deleteCategory(category) },
                    onColorClick = { colorDialogTarget = category },
                    onNameClick = {
                        nameDialogTarget = category
                        editNameValue = category.name
                    },
                    onMoveUp = { if (index > 0) viewModel.moveCategory(category, up = true) },
                    onMoveDown = { if (index < state.categories.size - 1) viewModel.moveCategory(category, up = false) }
                )
            }
        }

        // Кнопка сохранения
        if (state.sessionSeconds > 0 && !state.isRunning) {
            Button(
                onClick = { viewModel.finishSession() },
                modifier = Modifier.padding(16.dp).fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Завершить и сохранить", color = Color.White)
            }
        }
    }

    // --- Диалоги ---

    // 1. Диалог переименования
    nameDialogTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { nameDialogTarget = null },
            title = { Text("Переименовать") },
            text = {
                OutlinedTextField(
                    value = editNameValue,
                    onValueChange = { editNameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editNameValue.isNotBlank()) {
                        viewModel.updateCategoryName(category, editNameValue)
                        nameDialogTarget = null
                    }
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { nameDialogTarget = null }) { Text("Отмена") }
            }
        )
    }

    // 2. Диалог выбора цвета
    colorDialogTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { colorDialogTarget = null },
            title = { Text("Выбор цвета") },
            text = {
                // Контейнер с принудительным заполнением всей доступной ширины
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        // Arrangement.SpaceBetween распределит кружки максимально широко
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorOptions.forEach { color ->
                            val hexString = String.format("#%06X", (0xFFFFFF and color.toArgb()))

                            Box(
                                modifier = Modifier
                                    .size(36.dp) // Чуть уменьшили размер (с 40 до 36), чтобы точно влезло
                                    .background(color, shape = CircleShape)
                                    // Обводку убрали совсем
                                    .clickable {
                                        viewModel.updateCategoryColor(category, hexString)
                                        colorDialogTarget = null
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { colorDialogTarget = null }) {
                    Text("Закрыть", color = Color(0xFF4CAF50))
                }
            }
        )
    }

    // 3. Диалог добавления новой категории
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новая категория") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCatName.isNotBlank()) {
                        viewModel.addCategory(newCatName, "#4CAF50")
                        newCatName = ""
                        showAddDialog = false
                    }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun CategoryRow(
    category: CategoryEntity,
    isEditMode: Boolean,
    isActive: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onColorClick: () -> Unit,
    onNameClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        Color.Gray
    }

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Индикатор цвета
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, CircleShape)
                    .clickable(enabled = isEditMode) { onColorClick() }
            )

            Spacer(Modifier.width(16.dp))

            // Имя категории
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEditMode) { onNameClick() },
                color = if (isEditMode) Color.Cyan else Color.White
            )

            // Управление
            AnimatedContent(targetState = isEditMode, label = "ControlsAnim") { edit ->
                if (edit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMoveUp) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                        }
                        IconButton(onClick = onMoveDown) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Del", tint = Color(0xFFEF5350))
                        }
                    }
                } else {
                    IconButton(
                        onClick = { if (isActive) onPause() else onPlay() },
                        modifier = Modifier.background(if (isActive) Color.Gray else color, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}