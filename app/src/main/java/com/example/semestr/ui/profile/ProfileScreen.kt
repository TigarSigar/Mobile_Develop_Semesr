package com.example.semestr.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.semestr.ui.village.VillageViewModel
import com.example.semestr.util.formatToTime

@Composable
fun ProfileScreen(viewModel: VillageViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Локальное состояние для текста в поле ввода
    var tempNickname by remember { mutableStateOf(uiState.nickname) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // Поле изменения никнейма
        OutlinedTextField(
            value = tempNickname,
            onValueChange = { tempNickname = it },
            label = { Text("Ваш никнейм") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (tempNickname != uiState.nickname) {
                    TextButton(onClick = { viewModel.updateNickname(tempNickname) }) {
                        Text("Сохр.")
                    }
                }
            }
        )

        Spacer(Modifier.height(32.dp))

        // Статистика
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Общая статистика", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Всего заработано времени:")
                Text(
                    text = uiState.globalTime.formatToTime(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}