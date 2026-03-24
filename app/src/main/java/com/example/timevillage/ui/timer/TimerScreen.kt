package com.example.timevillage.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.timevillage.data.local.CategoryEntity
import com.example.timevillage.util.formatToTime

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val conflictTimer = viewModel.conflictTimerState.value

    var isEditMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    var colorDialogCategoryTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var colorDialogImportedTarget by remember { mutableStateOf<ImportedTvTask?>(null) }

    var nameDialogTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var editNameValue by remember { mutableStateOf("") }

    val colorOptions = listOf(
        Color(0xFF4CAF50),
        Color(0xFF03A9F4),
        Color(0xFF9C27B0),
        Color(0xFFF44336),
        Color(0xFFFFEB3B),
        Color(0xFFFF9800),
        Color(0xFFE91E63)
    )

    conflictTimer?.let { timer ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflict() },
            containerColor = Color(0xFF161B16),
            title = {
                Text(
                    text = "Таймер уже запущен",
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Активная сессия сейчас запущена в ${timer.sourceApp}: ${timer.taskName}.",
                    color = Color(0xFFD8E3D8)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resolveTimerConflict() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Завершить активный таймер", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConflict() }) {
                    Text("Отмена", color = Color(0xFFAAC6AA))
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

        Text(
            text = state.displaySeconds.formatToTime(),
            style = MaterialTheme.typography.displayLarge,
            color = when {
                state.isRunning -> Color(0xFF4CAF50)
                state.isSharedRunning -> Color(0xFF81C784)
                else -> Color.White
            },
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )

        if (state.isSharedRunning && state.activeSourceApp.isNotBlank()) {
            Text(
                text = "Источник: ${state.activeSourceApp}",
                color = Color(0xFFAAC6AA),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(state.orderedItems, key = { it.targetId }) { item ->
                val isActive = state.activeTargetId == item.targetId && state.isSharedRunning
                val category = state.categories.firstOrNull {
                    viewModel.localTargetId(it.id) == item.targetId
                }
                val imported = state.importedTasks.firstOrNull { it.id == item.targetId }

                TimerTargetRow(
                    item = item,
                    isEditMode = isEditMode,
                    isActive = isActive,
                    onPlay = {
                        if (category != null) {
                            viewModel.startLocalTimer(category)
                        } else if (imported != null) {
                            viewModel.startImportedTaskTimer(imported)
                        }
                    },
                    onPause = { viewModel.pauseTimer(item.targetId) },
                    onDelete = { category?.let(viewModel::deleteCategory) },
                    onColorClick = {
                        if (category != null) {
                            colorDialogCategoryTarget = category
                        } else if (imported != null) {
                            colorDialogImportedTarget = imported
                        }
                    },
                    onNameClick = {
                        if (category != null) {
                            nameDialogTarget = category
                            editNameValue = category.name
                        }
                    },
                    onMoveUp = { viewModel.moveItem(item.targetId, up = true) },
                    onMoveDown = { viewModel.moveItem(item.targetId, up = false) }
                )
            }
        }

        if (state.unsavedTotalSeconds > 0 && !state.isSharedRunning) {
            Button(
                onClick = { viewModel.finishSession() },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Завершить и сохранить", color = Color.White)
            }
        }
    }

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

    colorDialogCategoryTarget?.let { category ->
        ColorPickerDialog(
            title = "Выбор цвета",
            colorOptions = colorOptions,
            onColorPick = { hex ->
                viewModel.updateCategoryColor(category, hex)
                colorDialogCategoryTarget = null
            },
            onDismiss = { colorDialogCategoryTarget = null }
        )
    }

    colorDialogImportedTarget?.let { imported ->
        ColorPickerDialog(
            title = "Выбор цвета",
            colorOptions = colorOptions,
            onColorPick = { hex ->
                viewModel.updateImportedTaskColor(imported, hex)
                colorDialogImportedTarget = null
            },
            onDismiss = { colorDialogImportedTarget = null }
        )
    }

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
private fun ColorPickerDialog(
    title: String,
    colorOptions: List<Color>,
    onColorPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorOptions.forEach { color ->
                        val hexString = String.format("#%06X", (0xFFFFFF and color.toArgb()))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, shape = CircleShape)
                                .clickable { onColorPick(hexString) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color(0xFF4CAF50))
            }
        }
    )
}

@Composable
private fun TimerTargetRow(
    item: TimerListItem,
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
    val color = parseHexColor(item.colorHex)
    val markerShape: Shape = if (item.isImported) RoundedCornerShape(5.dp) else CircleShape

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 44.dp.toPx() }
    var dragAccumulator by remember(item.targetId, isEditMode) { mutableFloatStateOf(0f) }

    val dragModifier = if (isEditMode) {
        Modifier.pointerInput(item.targetId, isEditMode) {
            detectDragGesturesAfterLongPress(
                onDragEnd = { dragAccumulator = 0f },
                onDragCancel = { dragAccumulator = 0f },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount.y

                    while (dragAccumulator > dragThresholdPx) {
                        onMoveDown()
                        dragAccumulator -= dragThresholdPx
                    }
                    while (dragAccumulator < -dragThresholdPx) {
                        onMoveUp()
                        dragAccumulator += dragThresholdPx
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(dragModifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, markerShape)
                    .clickable(enabled = isEditMode) { onColorClick() }
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEditMode && !item.isImported) { onNameClick() },
                color = if (isEditMode) Color.Cyan else Color.White
            )

            AnimatedContent(targetState = isEditMode, label = "ControlsAnim") { edit ->
                if (edit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMoveUp) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                        }
                        IconButton(onClick = onMoveDown) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                        }
                        if (!item.isImported) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Del",
                                    tint = Color(0xFFEF5350)
                                )
                            }
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

private fun parseHexColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        Color.Gray
    }
}
