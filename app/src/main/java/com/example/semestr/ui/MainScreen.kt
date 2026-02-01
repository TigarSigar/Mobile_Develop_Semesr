package com.example.semestr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.semestr.ui.profile.ProfileScreen
import com.example.semestr.ui.timer.TimerScreen
import com.example.semestr.ui.timer.TimerViewModel
import com.example.semestr.ui.village.VillageScreen
import com.example.semestr.ui.village.VillageViewModel

@Composable
fun MainScreen(
    villageViewModel: VillageViewModel,
    timerViewModel: TimerViewModel
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Кнопка Деревни
                NavigationBarItem(
                    selected = currentRoute == "village",
                    onClick = {
                        if (currentRoute != "village") {
                            navController.navigate("village") {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Деревня") },
                    label = { Text("Деревня") }
                )

                // Кнопка Таймера
                NavigationBarItem(
                    selected = currentRoute == "timer",
                    onClick = {
                        if (currentRoute != "timer") {
                            navController.navigate("timer") {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Таймер") },
                    label = { Text("Таймер") }
                )

                // Кнопка Профиля
                NavigationBarItem(
                    selected = currentRoute == "profile",
                    onClick = {
                        if (currentRoute != "profile") {
                            navController.navigate("profile") {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль") }
                )
            }
        }
    ) { innerPadding ->
        // Навигационный хост, который переключает экраны внутри Scaffold
        NavHost(
            navController = navController,
            startDestination = "village",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("village") {
                VillageScreen(villageViewModel)
            }
            composable("timer") {
                TimerScreen(timerViewModel)
            }
            composable("profile") {
                ProfileScreen(villageViewModel)
            }
        }
    }
}