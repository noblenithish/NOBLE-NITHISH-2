package com.example.ui.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AssistantReminder
import com.example.data.model.AssistantTask
import com.example.data.model.TaskPriority
import com.example.data.repository.AssistantRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssistantHubUiState(
    val tasks: List<AssistantTask> = emptyList(),
    val reminders: List<AssistantReminder> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AssistantViewModel(
    private val userId: String,
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val localError = MutableStateFlow<String?>(null)

    private val tasksFlow = assistantRepository.observeTasks(userId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe tasks", fsError ?: error)
            localError.value = (fsError ?: error).message ?: "Failed to load tasks."
            emit(emptyList())
        }

    private val remindersFlow = assistantRepository.observeReminders(userId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe reminders", fsError ?: error)
            localError.value = (fsError ?: error).message ?: "Failed to load reminders."
            emit(emptyList())
        }

    val uiState: StateFlow<AssistantHubUiState> = combine(
        tasksFlow,
        remindersFlow,
        localError
    ) { tasks, reminders, err ->
        AssistantHubUiState(
            tasks = tasks,
            reminders = reminders,
            isLoading = false,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantHubUiState())

    fun addTask(title: String, notes: String, category: String, priority: TaskPriority) {
        if (title.isBlank()) return
        viewModelScope.launch {
            assistantRepository.createTask(userId, title, notes, category, priority)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to create task." }
        }
    }

    fun toggleTask(task: AssistantTask) {
        viewModelScope.launch {
            assistantRepository.toggleTask(userId, task.id, !task.isCompleted)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to update task." }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            assistantRepository.deleteTask(userId, taskId)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to delete task." }
        }
    }

    fun addReminder(title: String, delayMinutes: Int) {
        if (title.isBlank()) return
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        viewModelScope.launch {
            assistantRepository.createReminder(userId, title, triggerAt)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to schedule reminder." }
        }
    }

    fun toggleReminder(reminder: AssistantReminder) {
        viewModelScope.launch {
            assistantRepository.toggleReminder(userId, reminder.id, !reminder.isDone)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to update reminder." }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            assistantRepository.deleteReminder(userId, reminderId)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to delete reminder." }
        }
    }

    fun clearError() {
        localError.value = null
    }

    private companion object {
        const val TAG = "AssistantViewModel"
    }
}
