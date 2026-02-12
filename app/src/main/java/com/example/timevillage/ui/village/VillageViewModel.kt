package com.example.timevillage.ui.village

import coil.imageLoader
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.timevillage.R
import com.example.timevillage.data.local.BuildingEntity
import com.example.timevillage.data.repository.VillageRepository
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

// --- МОДЕЛИ ДАННЫХ (Под твой JSON) ---
data class GameConfig(
    val server_settings: ServerSettings? = null,
    val buildings: List<BuildingCategoryConfig> = emptyList()
)

data class ServerSettings(
    val base_url: String = ""
)

data class BuildingCategoryConfig(
    val type: String = "",
    val name_by_lvl: List<String>? = null,
    val grid_size_by_lvl: List<Int>? = null,
    val unlock_at_main_lvl: Int = 1,
    val levels: List<BuildingLevelConfig> = emptyList()
)

data class BuildingLevelConfig(
    val lvl: Int = 1,
    val cost: Int = 0,
    val url: String? = null,
    val frames: Int? = 1
)

data class ShopItem(
    val type: String,
    val name: String,
    val imageRes: Int,
    val frames: Int
)

data class VillageUiState(
    val buildings: List<BuildingEntity> = emptyList(),
    val accumulatedTime: Long = 0,
    val globalTime: Long = 0,
    val nickname: String = "Игрок"
)

class VillageViewModel(val repository: VillageRepository) : ViewModel() {

    // Резервный URL, если Firebase пустой
    private var BASE_URL = "http://176.197.150.40:9000"

    private val _downloadProgress = mutableStateOf(0f)
    val downloadProgress: State<Float> = _downloadProgress

    private val _isAllAssetsLoaded = mutableStateOf(false)
    val isAllAssetsLoaded: State<Boolean> = _isAllAssetsLoaded

    private val _gameConfig = MutableStateFlow<GameConfig?>(null)
    val isConfigLoaded = mutableStateOf(false)

    private val remoteConfig = Firebase.remoteConfig
    private val gson = Gson()

    val uiState: StateFlow<VillageUiState> = combine(
        repository.allBuildings,
        repository.userInfo,
        _gameConfig
    ) { buildings, user, _ ->
        VillageUiState(
            buildings = buildings,
            accumulatedTime = user?.accumulatedTime ?: 0,
            globalTime = user?.globalTime ?: 0,
            nickname = user?.nickname ?: "Игрок"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VillageUiState())

    init {
        setupRemoteConfig()
    }

    // 1. Настройка Firebase
    private fun setupRemoteConfig() {
        val settings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(settings)
        fetchGameConfig()
    }

    // 2. Получение конфига (откуда узнаем ссылки)
    private fun fetchGameConfig() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            viewModelScope.launch { // Переводим всё в корутину для порядка
                if (task.isSuccessful) {
                    val json = remoteConfig.getString("buildings_json")
                    if (json.isNotEmpty()) {
                        try {
                            val parsedConfig = gson.fromJson(json, GameConfig::class.java)
                            _gameConfig.value = parsedConfig
                            parsedConfig.server_settings?.base_url?.let {
                                if (it.isNotEmpty()) BASE_URL = it
                            }
                        } catch (e: Exception) {
                            Log.e("VILLAGE_DEBUG", "JSON Error", e)
                        }
                    }
                }

                // Сначала создаем начальное здание в базе
                checkStartBuilding()

                // Потом говорим системе, что конфиг готов
                isConfigLoaded.value = true
            }
        }
    }

    fun saveToCloud() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val userData = mapOf(
            "accumulatedTime" to uiState.value.accumulatedTime,
            "globalTime" to uiState.value.globalTime,
            "nickname" to (user.displayName ?: uiState.value.nickname),
            "buildings" to uiState.value.buildings.map { building ->
                mapOf(
                    "type" to building.type,
                    "level" to building.level,
                    "x" to building.x, // Используем правильные поля x
                    "y" to building.y  // и y
                )
            },
            "lastSync" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(user.uid)
            .set(userData)
            .addOnSuccessListener { Log.d("AUTH_SYNC", "Saved to Cloud") }
    }

    fun syncWithCloud() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val cloudAcc = document.getLong("accumulatedTime") ?: 0L
                    val cloudGlobal = document.getLong("globalTime") ?: 0L
                    val cloudBuildings = document.get("buildings") as? List<Map<String, Any>> ?: emptyList()

                    viewModelScope.launch {
                        repository.syncAllData(cloudAcc, cloudGlobal, cloudBuildings)
                    }
                } else {
                    saveToCloud()
                }
            }
    }

    fun preloadAssets(context: Context) {
        // Используем системный загрузчик Coil
        val imageLoader = context.imageLoader
        viewModelScope.launch {
            var attempts = 0
            while (_gameConfig.value == null && attempts < 50) {
                delay(200)
                attempts++
            }

            val config = _gameConfig.value ?: return@launch
            val currentBase = config.server_settings?.base_url ?: BASE_URL

            // Убираем дубликаты ссылок через .distinct()
            val urlsToLoad = config.buildings.flatMap { it.levels }
                .mapNotNull { it.url }
                .distinct()
                .map { if (it.startsWith("http")) it else "$currentBase/$it" }

            if (urlsToLoad.isEmpty()) {
                _isAllAssetsLoaded.value = true
                return@launch
            }

            urlsToLoad.forEachIndexed { index, url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()

                // Важно: вызываем enqueue или execute через системный загрузчик
                imageLoader.execute(request)

                _downloadProgress.value = (index + 1).toFloat() / urlsToLoad.size
                Log.d("VILLAGE_DEBUG", "Preloaded to disk: $url")
            }

            // Даем маленькую паузу, чтобы кэш сохранился
            delay(500)
            _isAllAssetsLoaded.value = true
        }
    }


    // 4. Получение ресурса (динамическая ссылка)
    fun getBuildingRes(type: String, level: Int): Any {
        val buildingData = getBuildingData(type, level)
        val path = buildingData?.url ?: return R.drawable.main1
        val currentBase = _gameConfig.value?.server_settings?.base_url ?: BASE_URL
        return if (path.startsWith("http")) path else "$currentBase/$path"
    }

    // --- ВСПОМОГАТЕЛЬНАЯ ЛОГИКА ---

    fun getFrameCount(type: String, level: Int): Int = getBuildingData(type, level)?.frames ?: 1

    fun getBuildingData(type: String, level: Int): BuildingLevelConfig? =
        _gameConfig.value?.buildings?.find { it.type == type }?.levels?.find { it.lvl == level }

    fun getGridSize(): Int {
        val config = _gameConfig.value ?: return 1
        val mainBuilding = uiState.value.buildings.find { it.type == "MAIN" }
        val mainConfig = config.buildings.find { it.type == "MAIN" }
        val size = mainConfig?.grid_size_by_lvl?.getOrElse((mainBuilding?.level ?: 1) - 1) { 1 } ?: 1
        return size
    }

    private suspend fun checkStartBuilding() {
        // Читаем базу данных ОДИН раз для проверки
        val currentBuildings = repository.allBuildings.first()
        if (currentBuildings.isEmpty()) {
            Log.d("VILLAGE_DEBUG", "DB empty, inserting MAIN (Bonfire)")
            // Ставим костер в центр (0,0)
            repository.insertBuilding(BuildingEntity(type = "MAIN", x = 0, y = 0, level = 1))

            // Маленькая задержка, чтобы Room успел записать данные
            delay(100)
        } else {
            Log.d("VILLAGE_DEBUG", "Buildings already exist: ${currentBuildings.size}")
        }
    }

    fun canBuildNew(type: String): Boolean {
        if (type == "MAIN") return false
        val config = _gameConfig.value ?: return false
        val mainLvl = uiState.value.buildings.find { it.type == "MAIN" }?.level ?: 1
        val category = config.buildings.find { it.type == type } ?: return false
        return mainLvl >= category.unlock_at_main_lvl
    }

    fun canUpgrade(building: BuildingEntity): Boolean {
        if (building.type == "MAIN") return true
        val mainLvl = uiState.value.buildings.find { it.type == "MAIN" }?.level ?: 1
        return building.level < mainLvl
    }

    fun getUpgradeCost(type: String, nextLvl: Int): Long =
        getBuildingData(type, nextLvl)?.cost?.toLong() ?: 100L

    fun buyBuilding(type: String, x: Int, y: Int) {
        viewModelScope.launch {
            val cost = getUpgradeCost(type, 1)
            if (uiState.value.accumulatedTime >= cost) {
                repository.spendTime(cost)
                repository.insertBuilding(BuildingEntity(type = type, x = x, y = y, level = 1))
            }
        }
    }

    fun upgradeBuilding(building: BuildingEntity) {
        if (!canUpgrade(building)) return
        viewModelScope.launch {
            val nextLvl = building.level + 1
            val cost = getUpgradeCost(building.type, nextLvl)
            if (uiState.value.accumulatedTime >= cost) {
                repository.spendTime(cost)
                repository.insertBuilding(building.copy(level = nextLvl))
            }
        }
    }

    val shopItems = listOf(
        ShopItem("HOUSE", "Дом", R.drawable.house1, 1)
    )

    fun updateNickname(newName: String) {
        viewModelScope.launch { repository.updateNickname(newName) }
    }
}