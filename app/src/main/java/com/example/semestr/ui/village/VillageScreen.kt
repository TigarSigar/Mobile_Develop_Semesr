package com.example.semestr.ui.village

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.semestr.data.local.BuildingEntity
import com.example.semestr.util.formatToTime
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillageScreen(viewModel: VillageViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val gridSize = viewModel.getGridSize()
    val radius = gridSize / 2

    var showShopByCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedBuilding by remember { mutableStateOf<BuildingEntity?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B5E20))) {
        // Верхняя панель времени
        Surface(Modifier.fillMaxWidth(), color = Color.Black.copy(0.4f)) {
            Text(
                text = "Доступно: ${uiState.accumulatedTime.formatToTime()}",
                modifier = Modifier.padding(16.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Игровая сетка
        val cellSize = (LocalConfiguration.current.screenWidthDp - 40) / gridSize
        Column(Modifier.align(Alignment.Center)) {
            for (y in -radius..radius) {
                Row {
                    for (x in -radius..radius) {
                        val building = uiState.buildings.find { it.x == x && it.y == y }
                        Box(
                            Modifier.size(cellSize.dp).padding(2.dp)
                                .background(Color.White.copy(0.05f), RoundedCornerShape(4.dp))
                                .clickable {
                                    if (building != null) selectedBuilding = building
                                    else if (viewModel.canBuildNew()) showShopByCoords = x to y
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (building != null) {
                                // 1. Получаем ID картинки (передаем координаты x, y чтобы костер определился)
                                val resId = viewModel.getBuildingRes(building.type, building.level, building.x, building.y)

                                // 2. Считаем количество кадров через созданную нами функцию
                                val frames = viewModel.getFrameCount(building.type, building.level)

                                // 3. Определяем, бесконечная ли анимация (только для костра MAIN 0)
                                val isInfinite = building.type == "MAIN" && building.level == 0

                                // 4. Рисуем!
                                SmartAnimation(
                                    resId = resId,
                                    frameCount = frames,
                                    infinite = isInfinite
                                )
                            } else {
                                Text("+", color = Color.White.copy(0.1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Магазин
    if (showShopByCoords != null) {
        ModalBottomSheet(onDismissRequest = { showShopByCoords = null }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text("МАГАЗИН", Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Black)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(viewModel.shopItems) { item ->
                        val canAfford = uiState.accumulatedTime >= item.cost
                        ShopCard(item, canAfford) {
                            viewModel.buyBuilding(item.type, item.cost, showShopByCoords!!.first, showShopByCoords!!.second)
                            showShopByCoords = null
                        }
                    }
                }
            }
        }
    }


    // Диалог улучшения
    selectedBuilding?.let { building ->
        // Определяем параметры для следующего уровня
        val nextLevel = building.level + 1
        val cost = nextLevel * 500L
        val canAfford = uiState.accumulatedTime >= cost
        val canUpgrade = viewModel.canUpgradeBuilding(building)

        // Получаем ресурс для СЛЕДУЮЩЕГО уровня (чтобы показать, что купим)
        // Если уровень уже макс (3), берем текущий, чтобы не было ошибок
        val nextLevelRes = if (canUpgrade) {
            viewModel.getBuildingRes(building.type, nextLevel)
        } else {
            viewModel.getBuildingRes(building.type, building.level)
        }

        AlertDialog(
            onDismissRequest = { selectedBuilding = null },
            title = { Text("Здание уровня ${building.level}", color = Color.White) },
            containerColor = Color(0xFF1A1A1A),
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Отрисовка будущей картинки (1 кадр!)
                    // Внутри AlertDialog -> Column
                    StaticImage(
                        resId = nextLevelRes,
                        // Используем нашу функцию, чтобы получить 16 для костра или 3 для дома
                        frameCount = viewModel.getFrameCount(building.type, nextLevel),
                        modifier = Modifier.size(100.dp),
                        isGray = !canAfford
                    )

                    Spacer(Modifier.height(12.dp))

                    if (canUpgrade) {
                        Text("Улучшить до уровня $nextLevel?", color = Color.LightGray)
                        Text(
                            text = "Цена: ${cost.formatToTime()}",
                            color = if (canAfford) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Достигнут максимальный уровень", color = Color.Yellow)
                    }
                }
            },
            confirmButton = {
                if (canUpgrade) {
                    Button(
                        onClick = {
                            viewModel.upgradeBuilding(building, cost)
                            selectedBuilding = null
                        },
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                    ) {
                        Text("УЛУЧШИТЬ")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBuilding = null }) {
                    Text("ОТМЕНА", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun ShopCard(item: ShopItem, canAfford: Boolean, onBuy: () -> Unit) {
    Card(Modifier.width(130.dp).height(180.dp)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.name, fontWeight = FontWeight.Bold)

            // Исправлено: передаем item.frames (убедись, что в ViewModel в shopItems они указаны верно)
            StaticImage(
                resId = item.imageRes,
                frameCount = item.frames,
                modifier = Modifier.size(70.dp),
                isGray = !canAfford
            )

            Spacer(Modifier.weight(1f))
            Button(onClick = onBuy, enabled = canAfford) {
                Text(item.cost.toString())
            }
        }
    }
}

@Composable
fun StaticImage(resId: Int, frameCount: Int, modifier: Modifier, isGray: Boolean = false) {
    val context = LocalContext.current
    val bitmap = remember(resId) {
        (context.getDrawable(resId) as BitmapDrawable).bitmap.asImageBitmap()
    }

    Canvas(modifier = modifier) {
        val safeFrameCount = if (frameCount < 1) 1 else frameCount
        val sw = bitmap.width / safeFrameCount

        drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, 0), // Всегда берем первый кадр
            srcSize = IntSize(sw, bitmap.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = if (isGray) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            } else null
        )
    }
}
@Composable
fun SmartAnimation(resId: Int, frameCount: Int, infinite: Boolean) {
    // remember(resId) заставит функцию сбросить кадр на 0, если сменилась картинка
    var frame by remember(resId) { mutableStateOf(0) }
    val context = LocalContext.current

    // Безопасно грузим картинку
    val bitmap = remember(resId) {
        (context.getDrawable(resId) as BitmapDrawable).bitmap.asImageBitmap()
    }

    LaunchedEffect(resId, frameCount) {
        // Если кадр всего один — ничего не делаем
        if (frameCount <= 1) {
            frame = 0
            return@LaunchedEffect
        }

        while (true) {
            if (infinite) {
                // Логика для костра (MAIN 0): 16 кадров крутятся без остановки
                delay(100) // Скорость горения костра
                frame = (frame + 1) % frameCount
            } else {
                // Логика для домов (3 кадра) и палатки (4 кадра)
                frame = 0 // Замираем на первом кадре

                // Случайная пауза от 5 до 15 секунд, чтобы дома не дергались одновременно
                delay(Random.nextLong(5000, 15000))

                // Проигрываем анимацию один раз
                for (i in 0 until frameCount) {
                    frame = i
                    delay(150) // Скорость проигрывания "движения"
                }
                frame = 0 // Возвращаемся в покой
            }
        }
    }

    // Отрисовка
    Canvas(Modifier.fillMaxSize(0.8f)) {
        // Защита от деления на 0
        val safeCount = if (frameCount > 0) frameCount else 1
        val sw = bitmap.width / safeCount

        // Рисуем только нужный кусочек (кадр) из всей ленты
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(frame * sw, 0),
            srcSize = IntSize(sw, bitmap.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}