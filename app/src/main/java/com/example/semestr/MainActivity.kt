package com.example.semestr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.semestr.data.local.BuildingEntity
import com.example.semestr.data.local.VillageDatabase
import com.example.semestr.data.repository.VillageRepository
import com.example.semestr.ui.MainScreen
import com.example.semestr.ui.theme.SemestrTheme
import com.example.semestr.ui.timer.TimerViewModel
import com.example.semestr.ui.village.VillageViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = VillageDatabase.getDatabase(this)
        val repository = VillageRepository(database.villageDao())

        val vViewModel = VillageViewModel(repository)
        val tViewModel = TimerViewModel(repository)

        setContent {
            SemestrTheme {
                MainScreen(villageViewModel = vViewModel, timerViewModel = tViewModel)
            }
        }

        lifecycleScope.launch {
            if (repository.allBuildings.first().isEmpty()) {
                repository.insertBuilding(BuildingEntity(type = "MAIN", level = 0, x = 0, y = 0))
            }
        }
    }
}