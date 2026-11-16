package com.example.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: UserProfile? = null,
    val preferences: AssistantPreferences = AssistantPreferences(),
    val statusMessage: String? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val userId: String,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)
    private val localError = MutableStateFlow<String?>(null)

    private val profileFlow = userProfileRepository.observeProfile(userId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe profile", fsError ?: error)
            emit(null)
        }

    private val prefsFlow = preferencesRepository.observePreferences(userId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe preferences", fsError ?: error)
            emit(AssistantPreferences(userId = userId))
        }

    val uiState: StateFlow<SettingsUiState> = combine(
        profileFlow,
        prefsFlow,
        statusMessage,
        localError
    ) { profile, prefs, status, err ->
        SettingsUiState(
            profile = profile,
            preferences = prefs,
            statusMessage = status,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updatePreferences(transform: (AssistantPreferences) -> AssistantPreferences) {
        val next = transform(uiState.value.preferences)
        viewModelScope.launch {
            localError.value = null
            preferencesRepository.savePreferences(userId, next)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to save preferences." }
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            localError.value = null
            userProfileRepository.updateDisplayName(userId, newName)
                .onSuccess { statusMessage.value = "Display name updated." }
                .onFailure { localError.value = it.localizedMessage ?: "Failed to update name." }
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            localError.value = null
            chatRepository.clearAllConversations(userId)
                .onSuccess { statusMessage.value = "Conversation history cleared." }
                .onFailure { localError.value = it.localizedMessage ?: "Failed to clear history." }
        }
    }

    fun clearStatus() {
        statusMessage.value = null
        localError.value = null
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
