package com.example.timevillage.ui.village

import coil.imageLoader
import coil.request.SuccessResult
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import com.example.timevillage.data.local.BuildingEntity
import com.example.timevillage.util.formatToTime
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillageScreen(viewModel: VillageViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isConfigLoaded by viewModel.isConfigLoaded

    if (!isConfigLoaded) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B5E20)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Состояния для камеры (зум и перемещение)
    val gridSize = viewModel.getGridSize()
    val radius = gridSize / 2
    var scale by remember { mutableFloatStateOf(1.5f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cellSizeDp = (screenWidth - 40.dp) / gridSize
    val cellSizePx = with(LocalDensity.current) { cellSizeDp.toPx() }

    var showShopByCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedBuilding by remember { mutableStateOf<BuildingEntity?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B5E20))) {
        // --- СЛОЙ 1: КАРТА И СЕТКА ---
        Box(
            Modifier.fillMaxSize()
                .pointerInput(gridSize) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
                .pointerInput(gridSize, uiState.buildings) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val localX = (tapOffset.x - centerX - offset.x) / scale
                        val localY = (tapOffset.y - centerY - offset.y) / scale
                        val gridX = (localX / cellSizePx).roundToInt()
                        val gridY = (localY / cellSizePx).roundToInt()

                        if (gridX in -radius..radius && gridY in -radius..radius) {
                            val b = uiState.buildings.find { it.x == gridX && it.y == gridY }
                            if (b != null) selectedBuilding = b
                            else showShopByCoords = gridX to gridY
                        }
                    }
                }
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    for (y in -radius..radius) {
                        Row {
                            for (x in -radius..radius) {
                                val building = uiState.buildings.find { it.x == x && it.y == y }
                                Box(
                                    Modifier.size(cellSizeDp).padding(1.dp)
                                        .background(Color.White.copy(0.05f), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (building != null) {
                                        UniversalBuildingImage(
                                            model = viewModel.getBuildingRes(building.type, building.level),
                                            frameCount = viewModel.getFrameCount(building.type, building.level),
                                            buildingType = building.type,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text("+", color = Color.White.copy(0.05f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- СЛОЙ 2: ВЕРХНЯЯ ПАНЕЛЬ (БАЛАНС) ---
        // Используем Column + Surface для отступа от статус-бара
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Spacer(Modifier.statusBarsPadding()) // Отступ под вырез камеры
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color.Black.copy(0.75f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💰",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = uiState.accumulatedTime.formatToTime(),
                        color = Color(0xFFFFB74D), // Оранжевый цвет времени
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }

    // --- ЛОГИКА МАГАЗИНА ---
    if (showShopByCoords != null) {
        ModalBottomSheet(onDismissRequest = { showShopByCoords = null }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text("МАГАЗИН", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                LazyRow(
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.shopItems) { item ->
                        val cost = viewModel.getUpgradeCost(item.type, 1)
                        ShopCard(
                            name = item.name,
                            imageRes = item.imageRes,
                            canAfford = uiState.accumulatedTime >= cost,
                            isUnlocked = viewModel.canBuildNew(item.type),
                            cost = cost,
                            frameCount = viewModel.getFrameCount(item.type, 1)
                        ) {
                            viewModel.buyBuilding(item.type, showShopByCoords!!.first, showShopByCoords!!.second)
                            viewModel.saveToCloud() // СИНХРОНИЗАЦИЯ ПОСЛЕ ПОКУПКИ
                            showShopByCoords = null
                        }
                    }
                }
            }
        }
    }

    // --- ЛОГИКА УЛУЧШЕНИЯ ---
    selectedBuilding?.let { b ->
        val nextLevel = b.level + 1
        val upgradeCost = viewModel.getUpgradeCost(b.type, nextLevel)
        val canAfford = uiState.accumulatedTime >= upgradeCost

        AlertDialog(
            onDismissRequest = { selectedBuilding = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.upgradeBuilding(b)
                        viewModel.saveToCloud() // СИНХРОНИЗАЦИЯ ПОСЛЕ УЛУЧШЕНИЯ
                        selectedBuilding = null
                    },
                    enabled = canAfford
                ) {
                    Text("УЛУЧШИТЬ")
                }
            },
            title = { Text("Здание: Уровень ${b.level}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(100.dp)) {
                        UniversalBuildingImage(
                            model = viewModel.getBuildingRes(b.type, nextLevel),
                            frameCount = viewModel.getFrameCount(b.type, nextLevel),
                            buildingType = b.type,
                            isGray = !canAfford,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Стоимость улучшения: ${upgradeCost.formatToTime()}",
                        color = if (canAfford) Color.Unspecified else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
fun SpriteAnimationCanvas(
    bitmap: ImageBitmap,
    frameCount: Int,
    isContinuous: Boolean,
    isGray: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentFrame by remember { mutableIntStateOf(0) }

    LaunchedEffect(bitmap) {
        while (true) {
            if (isContinuous) {
                delay(100)
                currentFrame = (currentFrame + 1) % frameCount
            } else {
                delay((5000..10000).random().toLong())
                for (i in 0 until frameCount) {
                    currentFrame = i
                    delay(120)
                }
                currentFrame = 0
            }
        }
    }

    Canvas(modifier = modifier) {
        val frameWidth = bitmap.width / frameCount
        val frameHeight = bitmap.height
        val colorFilter = if (isGray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

        drawImage(
            image = bitmap,
            srcOffset = IntOffset(currentFrame * frameWidth, 0),
            srcSize = IntSize(frameWidth, frameHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = colorFilter,
            filterQuality = FilterQuality.None
        )
    }
}

@Composable
fun UniversalBuildingImage(
    model: Any?,
    frameCount: Int,
    buildingType: String = "",
    isGray: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadedBitmap by remember(model) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(model) {
        val request = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            loadedBitmap = result.drawable.toBitmap().asImageBitmap()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bitmap = loadedBitmap
        if (bitmap != null) {
            val safeFrameCount = if (frameCount < 1) 1 else frameCount

            // Теперь если кадров > 1, анимация запускается ВСЕГДА
            if (safeFrameCount > 1) {
                SpriteAnimationCanvas(
                    bitmap = bitmap,
                    frameCount = safeFrameCount,
                    // Костер определяется по типу или по количеству кадров (16)
                    isContinuous = (buildingType == "fire" || safeFrameCount == 16),
                    isGray = isGray,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val frameWidth = bitmap.width / safeFrameCount
                    val frameHeight = bitmap.height
                    val colorFilter = if (isGray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(0, 0),
                        srcSize = IntSize(frameWidth, frameHeight),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        colorFilter = colorFilter,
                        filterQuality = FilterQuality.None
                    )
                }
            }
        } else {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White.copy(0.3f))
        }
    }
}

@Composable
fun ShopCard(
    name: String,
    imageRes: Any,
    canAfford: Boolean,
    isUnlocked: Boolean,
    cost: Long,
    frameCount: Int,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.width(110.dp).height(160.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(60.dp)) {
                UniversalBuildingImage(
                    model = imageRes,
                    frameCount = frameCount,
                    buildingType = "fire", // Передаем fire для анимации костра в магазине
                    isGray = !canAfford || !isUnlocked,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onBuy,
                enabled = canAfford && isUnlocked,
                contentPadding = PaddingValues(2.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(cost.toString(), fontSize = 10.sp)
            }
        }
    }
}