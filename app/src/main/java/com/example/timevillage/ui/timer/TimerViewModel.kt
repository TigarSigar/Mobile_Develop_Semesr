package com.example.timevillage.ui.timer

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timevillage.data.local.CategoryEntity
import com.example.timevillage.data.model.ActiveTimer
import com.example.timevillage.data.repository.VillageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val SOURCE_APP_TV = "TV"
private const val LOCAL_PREFIX = "local:"

class TimerViewModel(private val repository: VillageRepository) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance(
        "https://timevillage-42-default-rtdb.europe-west1.firebasedatabase.app"
    ).reference

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState = _uiState.asStateFlow()

    private val _sharedTimer = MutableStateFlow<ActiveTimer?>(null)

    private var timerRef: DatabaseReference? = null
    private var timerListener: ValueEventListener? = null
    private var importedTasksRegistration: ListenerRegistration? = null
    private var userStatsRegistration: ListenerRegistration? = null
    private var uiTickerJob: Job? = null

    private val unsavedSecondsByTarget: MutableMap<String, Long> = mutableMapOf()
    private val stopMutex = Mutex()

    private var persistedAccumulatedSeconds: Long = 0L
    private var pendingCarryOnStopSeconds: Long = 0L
    private var previousSharedTimer: ActiveTimer? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            importedTasksRegistration?.remove()
            importedTasksRegistration = null
            userStatsRegistration?.remove()
            userStatsRegistration = null
            timerListener?.let { listener ->
                timerRef?.removeEventListener(listener)
            }
            timerListener = null
            timerRef = null
            _sharedTimer.value = null
            previousSharedTimer = null
            persistedAccumulatedSeconds = 0L
            pendingCarryOnStopSeconds = 0L
            _uiState.update {
                it.copy(
                    importedTasks = emptyList(),
                    orderedItems = emptyList(),
                    activeTargetId = null,
                    activeSourceApp = "",
                    isSharedRunning = false,
                    isRunning = false,
                    displaySeconds = unsavedTotalSeconds(),
                    unsavedTotalSeconds = unsavedTotalSeconds(),
                    unsavedSecondsByTarget = unsavedSecondsByTarget.toMap()
                )
            }
        } else {
            observeImportedTvTasks(uid)
            observeUserAccumulated(uid)
            observeSharedTimer(uid)
        }
    }

    private data class StartRequest(
        val targetId: String,
        val targetName: String,
        val tag: String
    )

    val conflictTimerState = mutableStateOf<ActiveTimer?>(null)
    private var pendingStartRequest: StartRequest? = null

    init {
        observeLocalCategories()
        startUiTicker()
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.uid?.let { uid ->
            observeImportedTvTasks(uid)
            observeUserAccumulated(uid)
            observeSharedTimer(uid)
        }
    }

    fun localTargetId(categoryId: Int): String = "$LOCAL_PREFIX$categoryId"

    private fun isImportedTaskTarget(targetId: String): Boolean {
        return targetId.isNotBlank() && !targetId.startsWith(LOCAL_PREFIX)
    }

    private fun getTimerRef(uid: String): DatabaseReference {
        return rtdb.child("users").child(uid).child("active_timer")
    }

    private fun observeLocalCategories() {
        viewModelScope.launch {
            repository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
                rebuildOrderedItems()
            }
        }
    }

    private fun observeImportedTvTasks(uid: String) {
        importedTasksRegistration?.remove()
        importedTasksRegistration = firestore.collection("users")
            .document(uid)
            .collection("tasks")
            .whereEqualTo("isTvTask", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TV_TIMER", "imported tasks listener error: ${error.message}")
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents.orEmpty()
                    .filter { !(it.getBoolean("isCompleted") ?: false) }
                    .sortedBy { it.getLong("createdAt") ?: Long.MAX_VALUE }

                val localMaxPosition = _uiState.value.categories.maxOfOrNull { it.position } ?: -1
                val importedMaxPosition = docs.mapNotNull { it.getLong("tvPosition")?.toInt() }.maxOrNull()
                    ?: localMaxPosition
                var nextPosition = maxOf(localMaxPosition, importedMaxPosition) + 1

                val patches = mutableMapOf<String, Map<String, Any>>()
                val imported = docs.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val tags = doc.get("tags") as? List<*>
                    val tag = tags?.firstOrNull()?.toString()?.ifBlank { "tv" } ?: "tv"

                    val savedColor = doc.getString("tvColorHex")
                    val colorHex = savedColor?.takeIf { it.startsWith("#") } ?: importedTagColorHex(tag)

                    val savedPosition = doc.getLong("tvPosition")?.toInt()
                    val position = savedPosition ?: nextPosition++

                    val patch = mutableMapOf<String, Any>()
                    if (savedColor.isNullOrBlank()) patch["tvColorHex"] = colorHex
                    if (savedPosition == null) patch["tvPosition"] = position
                    if (patch.isNotEmpty()) {
                        patches[doc.id] = patch
                    }

                    ImportedTvTask(
                        id = doc.id,
                        title = title,
                        tag = tag.lowercase(),
                        colorHex = colorHex,
                        position = position
                    )
                }

                _uiState.update { it.copy(importedTasks = imported) }
                rebuildOrderedItems()

                if (patches.isNotEmpty()) {
                    patches.forEach { (taskId, patch) ->
                        firestore.collection("users")
                            .document(uid)
                            .collection("tasks")
                            .document(taskId)
                            .set(patch, SetOptions.merge())
                            .addOnFailureListener { e ->
                                Log.e("TV_TIMER", "imported meta patch error: ${e.message}")
                            }
                    }
                }
            }
    }

    private fun observeUserAccumulated(uid: String) {
        userStatsRegistration?.remove()
        userStatsRegistration = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TV_TIMER", "user stats listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val updatedAccumulated = snapshot?.getLong("accumulatedTime") ?: 0L
                val delta = (updatedAccumulated - persistedAccumulatedSeconds).coerceAtLeast(0L)
                persistedAccumulatedSeconds = updatedAccumulated
                if (pendingCarryOnStopSeconds > 0L && delta > 0L) {
                    pendingCarryOnStopSeconds = (pendingCarryOnStopSeconds - delta).coerceAtLeast(0L)
                }
                syncTimerPresentation()
            }
    }

    private fun observeSharedTimer(uid: String) {
        val ref = getTimerRef(uid)
        timerListener?.let { old ->
            timerRef?.removeEventListener(old)
        }
        timerRef = ref
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newTimer = snapshot.getValue(ActiveTimer::class.java)
                handleSharedTimerTransition(previousSharedTimer, newTimer)
                previousSharedTimer = newTimer
                _sharedTimer.value = newTimer
                syncTimerPresentation()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TV_TIMER", "shared timer read error: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        timerListener = listener
    }

    private fun handleSharedTimerTransition(previous: ActiveTimer?, current: ActiveTimer?) {
        if (previous?.isRunning == true && (current == null || !current.isRunning)) {
            if (previous.sourceApp != SOURCE_APP_TV && previous.startTime > 0L) {
                val elapsed = ((System.currentTimeMillis() - previous.startTime) / 1000)
                    .coerceAtLeast(0L)
                if (elapsed > 0L) {
                    pendingCarryOnStopSeconds += elapsed
                }
            }
        }
    }

    private fun startUiTicker() {
        uiTickerJob?.cancel()
        uiTickerJob = viewModelScope.launch {
            while (isActive) {
                syncTimerPresentation()
                delay(1000)
            }
        }
    }

    private fun syncTimerPresentation() {
        val remote = _sharedTimer.value
        val unsavedTotal = unsavedTotalSeconds()

        val elapsedCurrent = if (remote != null && remote.isRunning && remote.startTime > 0L) {
            ((System.currentTimeMillis() - remote.startTime) / 1000).coerceAtLeast(0L)
        } else {
            0L
        }

        val display = persistedAccumulatedSeconds + unsavedTotal + elapsedCurrent + pendingCarryOnStopSeconds
        val isSharedRunning = remote?.isRunning == true
        val sourceApp = remote?.sourceApp.orEmpty()
        val activeTargetId = if (isSharedRunning) remote?.taskId?.takeIf { it.isNotBlank() } else null

        _uiState.update {
            it.copy(
                displaySeconds = display,
                unsavedTotalSeconds = unsavedTotal,
                unsavedSecondsByTarget = unsavedSecondsByTarget.toMap(),
                isSharedRunning = isSharedRunning,
                isRunning = isSharedRunning && sourceApp == SOURCE_APP_TV,
                activeSourceApp = sourceApp,
                activeTargetId = activeTargetId
            )
        }
    }

    private fun rebuildOrderedItems() {
        val state = _uiState.value

        val localItems = state.categories.map { category ->
            TimerListItem(
                targetId = localTargetId(category.id),
                title = category.name,
                colorHex = category.colorHex,
                isImported = false,
                position = category.position
            )
        }

        val importedItems = state.importedTasks.map { task ->
            TimerListItem(
                targetId = task.id,
                title = task.title,
                colorHex = task.colorHex,
                isImported = true,
                position = task.position
            )
        }

        val ordered = (localItems + importedItems)
            .sortedWith(compareBy<TimerListItem> { it.position }.thenBy { it.title.lowercase() })

        _uiState.update { it.copy(orderedItems = ordered) }
    }

    fun addCategory(name: String, colorHex: String = "#4CAF50") {
        viewModelScope.launch {
            val nextPosition = (_uiState.value.orderedItems.maxOfOrNull { it.position } ?: -1) + 1
            repository.updateCategory(
                CategoryEntity(
                    name = name,
                    colorHex = colorHex,
                    position = nextPosition
                )
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun startLocalTimer(category: CategoryEntity) {
        val request = StartRequest(
            targetId = localTargetId(category.id),
            targetName = category.name,
            tag = category.colorHex
        )
        startWithConflictHandling(request)
    }

    fun startImportedTaskTimer(task: ImportedTvTask) {
        val request = StartRequest(
            targetId = task.id,
            targetName = task.title,
            tag = task.tag
        )
        startWithConflictHandling(request)
    }

    private fun startWithConflictHandling(request: StartRequest) {
        val remote = _sharedTimer.value
        if (remote != null && remote.isRunning && remote.taskId == request.targetId) {
            pauseTimer(request.targetId)
            return
        }
        if (remote != null && remote.isRunning && remote.sourceApp != SOURCE_APP_TV) {
            pendingStartRequest = request
            conflictTimerState.value = remote
            return
        }

        viewModelScope.launch {
            val latestRemote = _sharedTimer.value
            if (
                latestRemote != null &&
                latestRemote.isRunning &&
                latestRemote.sourceApp == SOURCE_APP_TV &&
                latestRemote.taskId != request.targetId
            ) {
                stashElapsedWithoutSaving(latestRemote)
            }
            launchLocalSession(request)
        }
    }

    private suspend fun launchLocalSession(request: StartRequest) {
        val now = System.currentTimeMillis()
        updateSharedTimer(
            ActiveTimer(
                taskId = request.targetId,
                taskName = request.targetName,
                tag = request.tag,
                startTime = now,
                isRunning = true,
                sourceApp = SOURCE_APP_TV
            )
        )
    }

    fun pauseTimer(targetId: String? = null) {
        viewModelScope.launch {
            stopMutex.withLock {
                val remote = _sharedTimer.value
                if (remote == null || !remote.isRunning) {
                    return@withLock
                }
                if (targetId != null && remote.taskId != targetId) {
                    return@withLock
                }

                if (remote.sourceApp == SOURCE_APP_TV) {
                    stashElapsedWithoutSaving(remote)
                    clearSharedTimer()
                } else {
                    finalizeRemoteSession(remote)
                }
                syncTimerPresentation()
            }
        }
    }

    fun finishSession() {
        viewModelScope.launch {
            stopMutex.withLock {
                val remote = _sharedTimer.value
                val hasTvRunningSession = remote != null && remote.isRunning && remote.sourceApp == SOURCE_APP_TV
                if (hasTvRunningSession) {
                    stashElapsedWithoutSaving(remote!!)
                }

                val persisted = flushUnsavedSessions()
                if (!persisted) {
                    return@withLock
                }
                unsavedSecondsByTarget.clear()
                if (hasTvRunningSession) {
                    clearSharedTimer()
                }
                syncTimerPresentation()
            }
        }
    }

    fun stopTimer() {
        finishSession()
    }

    fun resolveTimerConflict() {
        val remote = conflictTimerState.value ?: return
        val pending = pendingStartRequest ?: return
        viewModelScope.launch {
            stopMutex.withLock {
                val finalized = finalizeRemoteSession(remote)
                if (!finalized) {
                    return@withLock
                }
                conflictTimerState.value = null
                pendingStartRequest = null
                launchLocalSession(pending)
            }
        }
    }

    fun dismissConflict() {
        conflictTimerState.value = null
        pendingStartRequest = null
    }

    private fun stashElapsedWithoutSaving(timer: ActiveTimer) {
        val targetId = timer.taskId.takeIf { it.isNotBlank() } ?: return
        if (!timer.isRunning || timer.startTime <= 0L) return
        val elapsed = ((System.currentTimeMillis() - timer.startTime) / 1000).coerceAtLeast(0L)
        if (elapsed <= 0L) return
        val current = unsavedSecondsByTarget[targetId] ?: 0L
        unsavedSecondsByTarget[targetId] = current + elapsed
        syncTimerPresentation()
    }

    private fun unsavedTotalSeconds(): Long {
        return unsavedSecondsByTarget.values.sum()
    }

    private suspend fun flushUnsavedSessions(): Boolean {
        val snapshot = unsavedSecondsByTarget
            .filterValues { it > 0L }
            .toMap()
        if (snapshot.isEmpty()) return true

        val totalSeconds = snapshot.values.sum()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            repository.updateTime(totalSeconds)
            persistedAccumulatedSeconds += totalSeconds
            syncTimerPresentation()
            return true
        }
        val userDocRef = firestore.collection("users").document(uid)

        try {
            firestore.runBatch { batch ->
                batch.set(
                    userDocRef,
                    mapOf(
                        "accumulatedTime" to FieldValue.increment(totalSeconds),
                        "globalTime" to FieldValue.increment(totalSeconds)
                    ),
                    SetOptions.merge()
                )

                snapshot.forEach { (targetId, seconds) ->
                    if (isImportedTaskTarget(targetId)) {
                        val taskDocRef = userDocRef.collection("tasks").document(targetId)
                        batch.set(
                            taskDocRef,
                            mapOf("timeSpentSeconds" to FieldValue.increment(seconds)),
                            SetOptions.merge()
                        )
                    }
                }
            }.await()
            repository.updateTime(totalSeconds)
            persistedAccumulatedSeconds += totalSeconds
            pendingCarryOnStopSeconds = (pendingCarryOnStopSeconds - totalSeconds).coerceAtLeast(0L)
            syncTimerPresentation()
            return true
        } catch (e: Exception) {
            Log.e("TV_TIMER", "flush unsaved sessions error: ${e.message}")
            return false
        }
    }

    private suspend fun finalizeRemoteSession(timer: ActiveTimer): Boolean {
        val elapsed = if (timer.startTime > 0L) {
            ((System.currentTimeMillis() - timer.startTime) / 1000).coerceAtLeast(0)
        } else {
            0L
        }
        if (elapsed > 0L) {
            val importedTaskId = timer.taskId.takeIf { isImportedTaskTarget(it) }
            val applied = applyElapsedDelta(elapsed, importedTaskId)
            if (!applied) {
                return false
            }
        }
        clearSharedTimer()
        return true
    }

    private suspend fun applyElapsedDelta(deltaSeconds: Long, importedTaskId: String?): Boolean {
        if (deltaSeconds <= 0L) return true

        val uid = auth.currentUser?.uid ?: return false
        val userDocRef = firestore.collection("users").document(uid)
        val taskDocRef = importedTaskId?.let { userDocRef.collection("tasks").document(it) }

        try {
            firestore.runBatch { batch ->
                batch.set(
                    userDocRef,
                    mapOf(
                        "accumulatedTime" to FieldValue.increment(deltaSeconds),
                        "globalTime" to FieldValue.increment(deltaSeconds)
                    ),
                    SetOptions.merge()
                )
                if (taskDocRef != null) {
                    batch.set(
                        taskDocRef,
                        mapOf("timeSpentSeconds" to FieldValue.increment(deltaSeconds)),
                        SetOptions.merge()
                    )
                }
            }.await()
            repository.updateTime(deltaSeconds)
            persistedAccumulatedSeconds += deltaSeconds
            pendingCarryOnStopSeconds = (pendingCarryOnStopSeconds - deltaSeconds).coerceAtLeast(0L)
            syncTimerPresentation()
            return true
        } catch (e: Exception) {
            Log.e("TV_TIMER", "apply delta error: ${e.message}")
            return false
        }
    }

    private suspend fun updateSharedTimer(timer: ActiveTimer) {
        val uid = auth.currentUser?.uid ?: return
        try {
            getTimerRef(uid).setValue(timer).await()
        } catch (e: Exception) {
            Log.e("TV_TIMER", "update shared timer error: ${e.message}")
        }
    }

    private suspend fun clearSharedTimer() {
        val uid = auth.currentUser?.uid ?: return
        try {
            getTimerRef(uid).setValue(
                ActiveTimer(
                    taskId = "",
                    taskName = "",
                    tag = "",
                    startTime = 0L,
                    isRunning = false,
                    sourceApp = ""
                )
            ).await()
        } catch (e: Exception) {
            Log.e("TV_TIMER", "clear shared timer error: ${e.message}")
        }
    }

    fun updateCategoryColor(category: CategoryEntity, newColorHex: String) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(colorHex = newColorHex))
        }
    }

    fun updateImportedTaskColor(task: ImportedTvTask, newColorHex: String) {
        val updated = _uiState.value.importedTasks.map { current ->
            if (current.id == task.id) current.copy(colorHex = newColorHex) else current
        }
        _uiState.update { it.copy(importedTasks = updated) }
        rebuildOrderedItems()

        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .collection("tasks")
            .document(task.id)
            .set(mapOf("tvColorHex" to newColorHex), SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("TV_TIMER", "update imported color error: ${e.message}")
            }
    }

    fun updateCategoryName(category: CategoryEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = newName))
        }
    }

    fun moveCategory(category: CategoryEntity, up: Boolean) {
        moveItem(localTargetId(category.id), up)
    }

    fun moveImportedTask(task: ImportedTvTask, up: Boolean) {
        moveItem(task.id, up)
    }

    fun moveItem(targetId: String, up: Boolean) {
        val ordered = _uiState.value.orderedItems
        val index = ordered.indexOfFirst { it.targetId == targetId }
        if (index == -1) return

        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex !in ordered.indices) return

        val current = ordered[index]
        val neighbor = ordered[targetIndex]

        val currentPosition = current.position
        val neighborPosition = neighbor.position

        persistPosition(current, neighborPosition)
        persistPosition(neighbor, currentPosition)

        val updatedCategories = _uiState.value.categories.map { category ->
            when (localTargetId(category.id)) {
                current.targetId -> category.copy(position = neighborPosition)
                neighbor.targetId -> category.copy(position = currentPosition)
                else -> category
            }
        }
        val updatedImported = _uiState.value.importedTasks.map { task ->
            when (task.id) {
                current.targetId -> task.copy(position = neighborPosition)
                neighbor.targetId -> task.copy(position = currentPosition)
                else -> task
            }
        }

        _uiState.update {
            it.copy(
                categories = updatedCategories,
                importedTasks = updatedImported
            )
        }
        rebuildOrderedItems()
    }

    private fun persistPosition(item: TimerListItem, newPosition: Int) {
        if (item.isImported) {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users")
                .document(uid)
                .collection("tasks")
                .document(item.targetId)
                .set(mapOf("tvPosition" to newPosition), SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.e("TV_TIMER", "update imported position error: ${e.message}")
                }
            return
        }

        val category = _uiState.value.categories.firstOrNull { localTargetId(it.id) == item.targetId }
            ?: return
        viewModelScope.launch {
            repository.updateCategory(category.copy(position = newPosition))
        }
    }

    override fun onCleared() {
        super.onCleared()
        uiTickerJob?.cancel()
        auth.removeAuthStateListener(authStateListener)
        importedTasksRegistration?.remove()
        userStatsRegistration?.remove()
        timerListener?.let { listener ->
            timerRef?.removeEventListener(listener)
        }
    }
}

private fun importedTagColorHex(tag: String): String {
    return when (tag.lowercase()) {
        "intel", "интеллект" -> "#03A9F4"
        "strength", "сила" -> "#F44336"
        "craft", "ремесло" -> "#FF9800"
        else -> "#4CAF50"
    }
}
