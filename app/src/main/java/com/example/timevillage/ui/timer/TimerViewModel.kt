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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var timerJob: Job? = null
    private var timerRef: DatabaseReference? = null
    private var timerListener: ValueEventListener? = null
    private var importedTasksRegistration: ListenerRegistration? = null
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            importedTasksRegistration?.remove()
            importedTasksRegistration = null
            timerListener?.let { listener ->
                timerRef?.removeEventListener(listener)
            }
            timerListener = null
            timerRef = null
            _sharedTimer.value = null
            _uiState.update { it.copy(importedTasks = emptyList()) }
        } else {
            observeImportedTvTasks(uid)
            observeSharedTimer(uid)
        }
    }

    private data class StartRequest(
        val targetId: String,
        val targetName: String,
        val tag: String,
        val importedTaskId: String?
    )

    val conflictTimerState = mutableStateOf<ActiveTimer?>(null)
    private var pendingStartRequest: StartRequest? = null

    init {
        observeLocalCategories()
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.uid?.let { uid ->
            observeImportedTvTasks(uid)
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
                val imported = snapshot?.documents.orEmpty()
                    .filter { !(it.getBoolean("isCompleted") ?: false) }
                    .mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val tags = doc.get("tags") as? List<*>
                        val tag = tags?.firstOrNull()?.toString()?.ifBlank { "tv" } ?: "tv"
                        ImportedTvTask(
                            id = doc.id,
                            title = title,
                            tag = tag.lowercase()
                        )
                    }
                _uiState.update { it.copy(importedTasks = imported) }
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
                _sharedTimer.value = snapshot.getValue(ActiveTimer::class.java)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TV_TIMER", "shared timer read error: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        timerListener = listener
    }

    fun addCategory(name: String, colorHex: String = "#4CAF50") {
        viewModelScope.launch {
            val currentList = _uiState.value.categories
            val nextPos = if (currentList.isEmpty()) 0 else currentList.maxOf { it.position } + 1
            repository.updateCategory(
                CategoryEntity(
                    name = name,
                    colorHex = colorHex,
                    position = nextPos
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
            tag = category.colorHex,
            importedTaskId = null
        )
        startWithConflictHandling(request)
    }

    fun startImportedTaskTimer(task: ImportedTvTask) {
        val request = StartRequest(
            targetId = task.id,
            targetName = task.title,
            tag = task.tag,
            importedTaskId = task.id
        )
        startWithConflictHandling(request)
    }

    private fun startWithConflictHandling(request: StartRequest) {
        val remote = _sharedTimer.value
        if (remote != null && remote.isRunning && remote.sourceApp != SOURCE_APP_TV) {
            pendingStartRequest = request
            conflictTimerState.value = remote
            return
        }

        viewModelScope.launch {
            if (
                remote != null &&
                remote.isRunning &&
                remote.sourceApp == SOURCE_APP_TV &&
                remote.taskId != request.targetId
            ) {
                finalizeRemoteSession(remote)
            }
            launchLocalSession(request)
        }
    }

    private suspend fun launchLocalSession(request: StartRequest) {
        timerJob?.cancel()

        val isResume = _uiState.value.activeTargetId == request.targetId && !_uiState.value.isRunning
        val initialSeconds = if (isResume) _uiState.value.sessionSeconds else 0L
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

        _uiState.update {
            it.copy(
                isRunning = true,
                activeTargetId = request.targetId,
                activeImportedTaskId = request.importedTaskId,
                sessionSeconds = initialSeconds
            )
        }

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { state ->
                    if (state.isRunning && state.activeTargetId == request.targetId) {
                        state.copy(sessionSeconds = state.sessionSeconds + 1)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
        viewModelScope.launch {
            clearSharedTimer()
        }
    }

    fun finishSession() {
        val seconds = _uiState.value.sessionSeconds
        val activeImportedTaskId = _uiState.value.activeImportedTaskId
        pauseTimer()
        viewModelScope.launch {
            if (seconds > 0) {
                applyElapsedDelta(seconds, activeImportedTaskId)
            }
            _uiState.update {
                it.copy(
                    sessionSeconds = 0,
                    isRunning = false,
                    activeTargetId = null,
                    activeImportedTaskId = null
                )
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
            finalizeRemoteSession(remote)
            conflictTimerState.value = null
            pendingStartRequest = null
            launchLocalSession(pending)
        }
    }

    fun dismissConflict() {
        conflictTimerState.value = null
        pendingStartRequest = null
    }

    private suspend fun finalizeRemoteSession(timer: ActiveTimer) {
        val elapsed = if (timer.startTime > 0L) {
            ((System.currentTimeMillis() - timer.startTime) / 1000).coerceAtLeast(0)
        } else {
            0L
        }
        if (elapsed > 0L) {
            val importedTaskId = timer.taskId.takeIf { isImportedTaskTarget(it) }
            applyElapsedDelta(elapsed, importedTaskId)
        }
        clearSharedTimer()
    }

    private suspend fun applyElapsedDelta(deltaSeconds: Long, importedTaskId: String?) {
        if (deltaSeconds <= 0L) return

        repository.updateTime(deltaSeconds)

        val uid = auth.currentUser?.uid ?: return
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
        } catch (e: Exception) {
            Log.e("TV_TIMER", "apply delta error: ${e.message}")
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

    fun updateCategoryName(category: CategoryEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = newName))
        }
    }

    fun moveCategory(category: CategoryEntity, up: Boolean) {
        val currentList = _uiState.value.categories
        val index = currentList.indexOfFirst { it.id == category.id }
        val targetIndex = if (up) index - 1 else index + 1

        if (targetIndex in currentList.indices) {
            val targetCat = currentList[targetIndex]
            viewModelScope.launch(Dispatchers.IO) {
                val currentPos = if (category.position == targetCat.position) index else category.position
                val targetPos = if (category.position == targetCat.position) targetIndex else targetCat.position
                repository.updateCategory(category.copy(position = targetPos))
                repository.updateCategory(targetCat.copy(position = currentPos))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        auth.removeAuthStateListener(authStateListener)
        importedTasksRegistration?.remove()
        timerListener?.let { listener ->
            timerRef?.removeEventListener(listener)
        }
    }
}
