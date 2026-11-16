package com.example.ui.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.*
import com.example.core.speech.SpeechInputService
import com.example.core.speech.SpeechListenState
import com.example.core.speech.SpeechOutputService
import com.example.data.model.*
import com.example.data.repository.AssistantRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class VoiceOrbPhase { IDLE, LISTENING, THINKING, SPEAKING }

data class VoiceUiState(
    val assistantName: String = "JARVIS",
    val phase: VoiceOrbPhase = VoiceOrbPhase.IDLE,
    val liveTranscript: String = "",
    val lastAssistantReply: String = "Tap the orb and speak naturally.",
    val rmsLevel: Float = 0f,
    val handsFreeLoop: Boolean = true,
    val error: String? = null
)

class VoiceViewModel(
    private val userId: String,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chatRepository: ChatRepository,
    private val assistantRepository: AssistantRepository,
    private val aiService: JarvisAiService,
    private val speechInput: SpeechInputService,
    private val speechOutput: SpeechOutputService
) : ViewModel() {

    private var voiceConversationId: String? = null
    private val thinking = MutableStateFlow(false)
    private val handsFree = MutableStateFlow(true)
    private val lastReply = MutableStateFlow("Tap the orb and speak naturally.")
    private val localError = MutableStateFlow<String?>(null)

    private val preferencesFlow = preferencesRepository.observePreferences(userId)
        .catch { emit(AssistantPreferences(userId = userId)) }

    val uiState: StateFlow<VoiceUiState> = combine(
        speechInput.state,
        speechInput.partialText,
        speechInput.rmsLevel,
        speechOutput.isSpeaking,
        thinking,
        handsFree,
        lastReply,
        localError,
        preferencesFlow
    ) { values ->
        val listenState = values[0] as SpeechListenState
        val partial = values[1] as String
        val rms = values[2] as Float
        val speaking = values[3] as Boolean
        val isThinking = values[4] as Boolean
        val loop = values[5] as Boolean
        val reply = values[6] as String
        val err = values[7] as String?
        val prefs = values[8] as AssistantPreferences

        val phase = when {
            isThinking -> VoiceOrbPhase.THINKING
            speaking -> VoiceOrbPhase.SPEAKING
            listenState is SpeechListenState.Listening -> VoiceOrbPhase.LISTENING
            else -> VoiceOrbPhase.IDLE
        }

        VoiceUiState(
            assistantName = prefs.assistantName,
            phase = phase,
            liveTranscript = partial,
            lastAssistantReply = reply,
            rmsLevel = rms,
            handsFreeLoop = loop,
            error = err ?: (listenState as? SpeechListenState.Error)?.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceUiState())

    fun toggleHandsFree() {
        handsFree.value = !handsFree.value
    }

    fun onOrbTap() {
        when (uiState.value.phase) {
            VoiceOrbPhase.LISTENING -> speechInput.stopListening()
            VoiceOrbPhase.SPEAKING -> {
                speechOutput.stop()
                startListening()
            }
            VoiceOrbPhase.IDLE -> startListening()
            VoiceOrbPhase.THINKING -> Unit
        }
    }

    fun stopAllVoice() {
        speechInput.stopListening()
        speechOutput.stop()
    }

    fun startListening() {
        localError.value = null
        speechOutput.stop()
        speechInput.startListening { spokenText ->
            handleSpokenInput(spokenText)
        }
    }

    private fun handleSpokenInput(spokenText: String) {
        viewModelScope.launch {
            thinking.value = true
            localError.value = null
            try {
                val convId = voiceConversationId ?: run {
                    chatRepository.createConversation(userId, "Voice Session")
                        .getOrThrow()
                        .also { voiceConversationId = it }
                }

                chatRepository.appendMessage(
                    userId = userId,
                    conversationId = convId,
                    sender = MessageSender.USER,
                    content = spokenText,
                    source = MessageSource.VOICE
                ).getOrThrow()

                val prefs = preferencesRepository.getPreferencesOrDefault(userId)
                var actionBadge: String? = null
                val replyText: String = when (val action = IntentExtractor.extract(spokenText)) {
                    is DetectedAssistantAction.CreateTask -> {
                        assistantRepository.createTask(userId, action.title, priority = action.priority).getOrThrow()
                        actionBadge = "Task added • ${action.priority.name}"
                        "Added ${action.title} to your tasks."
                    }

                    is DetectedAssistantAction.CreateReminder -> {
                        assistantRepository.createReminder(userId, action.title, action.triggerAtMillis).getOrThrow()
                        actionBadge = "Reminder set • in ${action.humanDelay}"
                        "Reminder set for ${action.humanDelay} from now."
                    }

                    null -> {
                        val profile = userProfileRepository.observeProfile(userId).catch { emit(null) }.firstOrNull()
                        val openTasks = assistantRepository.observeTasks(userId).catch { emit(emptyList()) }
                            .first().count { !it.isCompleted }
                        val activeReminders = assistantRepository.observeReminders(userId).catch { emit(emptyList()) }
                            .first().count { !it.isDone }
                        val history = chatRepository.getRecentMessages(userId, convId, limit = 8).dropLast(1)

                        val aiContext = AiRequestContext(
                            userName = profile?.displayName ?: "User",
                            preferences = prefs,
                            recentHistory = history,
                            openTaskCount = openTasks,
                            upcomingReminderCount = activeReminders
                        )

                        var completeText = ""
                        aiService.streamReply(spokenText, aiContext).collect { event ->
                            when (event) {
                                is AiStreamEvent.Partial -> lastReply.value = event.accumulatedText
                                is AiStreamEvent.Complete -> completeText = event.fullText
                            }
                        }
                        completeText
                    }
                }

                chatRepository.appendMessage(
                    userId = userId,
                    conversationId = convId,
                    sender = MessageSender.ASSISTANT,
                    content = replyText,
                    source = MessageSource.VOICE,
                    actionBadge = actionBadge
                ).getOrThrow()

                lastReply.value = replyText
                thinking.value = false

                if (prefs.autoSpeakReplies) {
                    speechOutput.speak(
                        text = replyText,
                        pitch = prefs.voicePitch.toFloat(),
                        rate = prefs.voiceRate.toFloat()
                    ) {
                        if (handsFree.value) {
                            viewModelScope.launch { startListening() }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice turn failed", e)
                localError.value = e.localizedMessage ?: "Voice AI request failed."
                thinking.value = false
            }
        }
    }

    override fun onCleared() {
        speechInput.stopListening()
        speechOutput.stop()
        super.onCleared()
    }

    private companion object {
        const val TAG = "VoiceViewModel"
    }
}
