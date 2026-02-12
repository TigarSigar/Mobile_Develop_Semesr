package com.example.timevillage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.timevillage.ui.timer.TimerScreen
import com.example.timevillage.ui.timer.TimerViewModel
import com.example.timevillage.ui.village.VillageScreen
import com.example.timevillage.ui.village.VillageViewModel
import com.example.timevillage.ui.profile.ProfileScreen

@Composable
fun MainScreen(
    villageViewModel: VillageViewModel,
    timerViewModel: TimerViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Деревня") },
                    icon = { Icon(Icons.Default.Home, null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Таймер") },
                    icon = { Icon(Icons.Default.PlayArrow, null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Профиль") },
                    icon = { Icon(Icons.Default.AccountCircle, null) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> VillageScreen(villageViewModel)
                1 -> TimerScreen(timerViewModel)
                2 -> ProfileScreen(viewModel = villageViewModel)
            }
        }
    }
}