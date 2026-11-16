package com.example.core.ai

import android.util.Log
import com.example.data.model.AssistantPreferences
import com.example.data.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiRequestContext(
    val userName: String,
    val preferences: AssistantPreferences,
    val recentHistory: List<ChatMessage>,
    val openTaskCount: Int,
    val upcomingReminderCount: Int
)

sealed interface AiStreamEvent {
    data class Partial(val accumulatedText: String) : AiStreamEvent
    data class Complete(val fullText: String) : AiStreamEvent
}

interface JarvisAiService {
    fun streamReply(prompt: String, context: AiRequestContext): Flow<AiStreamEvent>
}

class FirebaseJarvisAiService : JarvisAiService {

    private var cachedKey: Pair<String, String>? = null
    private var cachedModel: GenerativeModel? = null

    private fun getOrCreateModel(modelName: String, systemPrompt: String): GenerativeModel {
        val key = modelName to systemPrompt
        val existing = cachedModel
        if (existing != null && cachedKey == key) {
            return existing
        }
        val created = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = content { text(systemPrompt) }
        )
        cachedKey = key
        cachedModel = created
        return created
    }

    override fun streamReply(prompt: String, context: AiRequestContext): Flow<AiStreamEvent> = flow {
        val prefs = context.preferences
        val systemPrompt = buildSystemPrompt(context)
        val modelName = prefs.resolvedModel().modelId
        val model = getOrCreateModel(modelName, systemPrompt)

        val historyContent: List<Content> = context.recentHistory
            .filter { it.content.isNotBlank() }
            .map { msg ->
                val role = if (msg.isUser()) "user" else "model"
                content(role) { text(msg.content) }
            }

        val chat = model.startChat(history = historyContent)
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiStreamEvent.Partial(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                throw IllegalStateException("AI model returned an empty response.")
            }
            emit(AiStreamEvent.Complete(finalText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            throw IllegalStateException("Response timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw e
        }
    }

    private fun buildSystemPrompt(ctx: AiRequestContext): String {
        val prefs = ctx.preferences
        val preset = prefs.resolvedPreset()
        val now = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("Assistant Name: ${prefs.assistantName}")
            appendLine(preset.systemDirective)
            appendLine("User Name: ${ctx.userName}")
            appendLine("Current Local Time: $now")
            appendLine("Active Personal Assistant Context: ${ctx.openTaskCount} open task(s), ${ctx.upcomingReminderCount} active reminder(s).")
            appendLine(
                "Tone Calibration — Warmth: ${(prefs.warmth * 100).toInt()}%, " +
                    "Conciseness: ${(prefs.conciseness * 100).toInt()}%, " +
                    "Wit/Humor: ${(prefs.humor * 100).toInt()}%."
            )
            if (prefs.customInstructions.isNotBlank()) {
                appendLine("Custom User Instructions: ${prefs.customInstructions}")
            }
        }
    }

    private companion object {
        const val TAG = "JarvisAiService"
    }
}
