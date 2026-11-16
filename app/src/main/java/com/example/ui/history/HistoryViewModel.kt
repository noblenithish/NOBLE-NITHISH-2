package com.example.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HistoryViewModel(
    private val userId: String,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val localError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        chatRepository.observeConversations(userId)
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Failed to observe conversations", fsError ?: error)
                localError.value = (fsError ?: error).message ?: "Failed to load conversation history."
                emit(emptyList())
            },
        localError
    ) { list, err ->
        HistoryUiState(
            conversations = list,
            isLoading = false,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            chatRepository.togglePin(userId, conversation.id, !conversation.isPinned)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to update pin." }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(userId, conversationId)
                .onFailure { localError.value = it.localizedMessage ?: "Failed to delete conversation." }
        }
    }

    fun clearError() {
        localError.value = null
    }

    private companion object {
        const val TAG = "HistoryViewModel"
    }
}
