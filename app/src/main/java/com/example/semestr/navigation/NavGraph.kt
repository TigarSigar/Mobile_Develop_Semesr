package com.example.semestr.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.semestr.ui.village.VillageScreen
import com.example.semestr.ui.village.VillageViewModel
import com.example.semestr.ui.timer.TimerViewModel
import com.example.semestr.ui.timer.TimerScreen // Проверь название своего экрана таймера

@Composable
fun NavGraph(
    villageViewModel: VillageViewModel,
    timerViewModel: TimerViewModel
) {
    // Создаем контроллер навигации прямо здесь
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "village"
    ) {
        composable("village") {
            // Передаем именно ту вьюмодель, которую получили в аргументах
            VillageScreen(viewModel = villageViewModel)
        }

        composable("timer") {
            // Экран таймера (если он у тебя создан)
            TimerScreen(viewModel = timerViewModel)
        }
    }
}