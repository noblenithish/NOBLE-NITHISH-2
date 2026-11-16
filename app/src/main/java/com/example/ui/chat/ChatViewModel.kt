package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.*
import com.example.core.speech.SpeechOutputService
import com.example.data.model.*
import com.example.data.repository.AssistantRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingReply: String? = null,
    val assistantName: String = "JARVIS",
    val presetLabel: String = PersonalityPreset.CLASSIC_JARVIS.displayName,
    val modelLabel: String = AiModelOption.GEMINI_FLASH.label,
    val isThinking: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val userId: String,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chatRepository: ChatRepository,
    private val assistantRepository: AssistantRepository,
    private val aiService: JarvisAiService,
    private val speechOutput: SpeechOutputService
) : ViewModel() {

    private val activeConvId = MutableStateFlow<String?>(null)
    private val isThinking = MutableStateFlow(false)
    private val streamingText = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    private val openTaskCount = assistantRepository.observeTasks(userId)
        .map { list -> list.count { !it.isCompleted } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val activeReminderCount = assistantRepository.observeReminders(userId)
        .map { list -> list.count { !it.isDone } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val messagesFlow = activeConvId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            chatRepository.observeMessages(userId, id)
                .catch { err ->
                    Log.w(TAG, "Error observing chat messages", err)
                    error.value = err.message ?: "Failed to load messages."
                    emit(emptyList())
                }
        }
    }

    private val preferencesFlow = preferencesRepository.observePreferences(userId)
        .catch { emit(AssistantPreferences(userId = userId)) }

    val uiState: StateFlow<ChatUiState> = combine(
        activeConvId,
        messagesFlow,
        preferencesFlow,
        streamingText,
        isThinking,
        error
    ) { values ->
        val convId = values[0] as String?
        @Suppress("UNCHECKED_CAST")
        val msgs = values[1] as List<ChatMessage>
        val prefs = values[2] as AssistantPreferences
        val stream = values[3] as String?
        val thinking = values[4] as Boolean
        val err = values[5] as String?

        ChatUiState(
            activeConversationId = convId,
            messages = msgs,
            streamingReply = stream,
            assistantName = prefs.assistantName,
            presetLabel = prefs.resolvedPreset().displayName,
            modelLabel = prefs.resolvedModel().label,
            isThinking = thinking,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun selectConversation(conversationId: String) {
        activeConvId.value = conversationId
        streamingText.value = null
        error.value = null
    }

    fun startNewConversation() {
        activeConvId.value = null
        streamingText.value = null
        error.value = null
    }

    fun clearError() {
        error.value = null
    }

    fun speakMessage(text: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.getPreferencesOrDefault(userId)
            speechOutput.speak(text, prefs.voicePitch.toFloat(), prefs.voiceRate.toFloat())
        }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || isThinking.value) return

        viewModelScope.launch {
            error.value = null
            streamingText.value = null
            isThinking.value = true

            try {
                val convId = activeConvId.value ?: run {
                    val created = chatRepository.createConversation(userId, text.take(48))
                    created.getOrThrow().also { activeConvId.value = it }
                }

                chatRepository.appendMessage(
                    userId = userId,
                    conversationId = convId,
                    sender = MessageSender.USER,
                    content = text,
                    source = MessageSource.TEXT
                ).getOrThrow()

                when (val detected = IntentExtractor.extract(text)) {
                    is DetectedAssistantAction.CreateTask -> {
                        assistantRepository.createTask(
                            userId = userId,
                            title = detected.title,
                            priority = detected.priority
                        ).getOrThrow()
                        val badge = "Task added • ${detected.priority.name}"
                        val reply = "Done — I added \"${detected.title}\" (${detected.priority.name.lowercase()} priority) to your Tasks."
                        chatRepository.appendMessage(
                            userId = userId,
                            conversationId = convId,
                            sender = MessageSender.ASSISTANT,
                            content = reply,
                            source = MessageSource.TEXT,
                            actionBadge = badge
                        ).getOrThrow()
                        isThinking.value = false
                        return@launch
                    }

                    is DetectedAssistantAction.CreateReminder -> {
                        assistantRepository.createReminder(
                            userId = userId,
                            title = detected.title,
                            triggerAtMillis = detected.triggerAtMillis
                        ).getOrThrow()
                        val badge = "Reminder set • in ${detected.humanDelay}"
                        val reply = "Reminder scheduled for ${detected.humanDelay} from now: \"${detected.title}\"."
                        chatRepository.appendMessage(
                            userId = userId,
                            conversationId = convId,
                            sender = MessageSender.ASSISTANT,
                            content = reply,
                            source = MessageSource.TEXT,
                            actionBadge = badge
                        ).getOrThrow()
                        isThinking.value = false
                        return@launch
                    }

                    null -> Unit
                }

                val prefs = preferencesRepository.getPreferencesOrDefault(userId)
                val profile = userProfileRepository.observeProfile(userId).catch { emit(null) }.firstOrNull()
                val history = chatRepository.getRecentMessages(userId, convId, limit = 12)
                    .dropLast(1) // Exclude just-sent user prompt from prior history

                val context = AiRequestContext(
                    userName = profile?.displayName ?: "User",
                    preferences = prefs,
                    recentHistory = history,
                    openTaskCount = openTaskCount.value,
                    upcomingReminderCount = activeReminderCount.value
                )

                var finalReply = ""
                aiService.streamReply(text, context).collect { event ->
                    when (event) {
                        is AiStreamEvent.Partial -> streamingText.value = event.accumulatedText
                        is AiStreamEvent.Complete -> finalReply = event.fullText
                    }
                }

                streamingText.value = null
                if (finalReply.isNotBlank()) {
                    chatRepository.appendMessage(
                        userId = userId,
                        conversationId = convId,
                        sender = MessageSender.ASSISTANT,
                        content = finalReply,
                        source = MessageSource.TEXT
                    ).getOrThrow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                streamingText.value = null
                error.value = e.localizedMessage ?: "Unable to reach JARVIS AI right now."
            } finally {
                isThinking.value = false
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
