package com.example.timevillage.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timevillage.ui.village.VillageViewModel

@Composable
fun SplashScreen(viewModel: VillageViewModel, onNextScreen: () -> Unit) {
    val context = LocalContext.current
    val isLoaded by viewModel.isAllAssetsLoaded
    val progress by viewModel.downloadProgress

    // Запускаем предзагрузку при старте сплэша
    LaunchedEffect(Unit) {
        viewModel.preloadAssets(context)
    }

    // Как только всё загрузилось — переходим дальше
    LaunchedEffect(isLoaded) {
        if (isLoaded) {
            onNextScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "TIME VILLAGE",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Исправленный индикатор: прогресс передается как Float
            CircularProgressIndicator(
                progress = { progress },
                color = Color.Yellow,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val percent = (progress * 100).toInt()
            Text(
                text = "Загрузка ресурсов: $percent%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}