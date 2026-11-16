package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MessageSender { USER, ASSISTANT }
enum class MessageSource { TEXT, VOICE }
enum class TaskPriority { LOW, MEDIUM, HIGH }

enum class PersonalityPreset(
    val displayName: String,
    val tagline: String,
    val systemDirective: String
) {
    CLASSIC_JARVIS(
        "Classic JARVIS",
        "Composed, articulate, subtly witty engineer-butler",
        "You are JARVIS, a refined, hyper-competent AI assistant. Be calm, precise, helpful, and occasionally dry-witted without wasting the user's time."
    ),
    EXECUTIVE(
        "Executive Brief",
        "Ultra-concise, bulleted, decision-ready",
        "You are JARVIS in Executive mode. Give direct answers, crisp bullet points, clear next steps, and zero fluff."
    ),
    COACH(
        "Focus Coach",
        "Supportive, structured, momentum-driven",
        "You are JARVIS in Focus Coach mode. Break goals into realistic steps, encourage momentum constructively, and suggest practical schedules."
    ),
    CREATIVE(
        "Creative Partner",
        "Imaginative, exploratory, brainstorming ally",
        "You are JARVIS in Creative mode. Offer fresh perspectives, vivid examples, and bold ideas while staying grounded in the user's goal."
    )
}

enum class AiModelOption(val modelId: String, val label: String, val subtitle: String) {
    GEMINI_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Default • Best reasoning & speed balance"),
    GEMINI_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra-fast • Lightweight everyday queries")
}

enum class AppThemeMode(val label: String) {
    DARK("Obsidian"),
    LIGHT("Daylight"),
    SYSTEM("System")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AssistantPreferences(
    @DocumentId val id: String = "default",
    val userId: String = "",
    val assistantName: String = "JARVIS",
    val preset: String = PersonalityPreset.CLASSIC_JARVIS.name,
    val customInstructions: String = "",
    val warmth: Double = 0.65,
    val conciseness: Double = 0.60,
    val humor: Double = 0.35,
    val voicePitch: Double = 1.0,
    val voiceRate: Double = 1.0,
    val autoSpeakReplies: Boolean = true,
    val proactiveBriefings: Boolean = true,
    val aiModel: String = AiModelOption.GEMINI_FLASH.modelId,
    val themeMode: String = AppThemeMode.DARK.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedPreset(): PersonalityPreset =
        PersonalityPreset.entries.firstOrNull { it.name == preset } ?: PersonalityPreset.CLASSIC_JARVIS

    fun resolvedThemeMode(): AppThemeMode =
        AppThemeMode.entries.firstOrNull { it.name == themeMode } ?: AppThemeMode.DARK

    fun resolvedModel(): AiModelOption =
        AiModelOption.entries.firstOrNull { it.modelId == aiModel } ?: AiModelOption.GEMINI_FLASH

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "assistantName" to assistantName.ifBlank { "JARVIS" }.take(40),
        "preset" to resolvedPreset().name,
        "customInstructions" to customInstructions.take(1000),
        "warmth" to warmth.coerceIn(0.0, 1.0),
        "conciseness" to conciseness.coerceIn(0.0, 1.0),
        "humor" to humor.coerceIn(0.0, 1.0),
        "voicePitch" to voicePitch.coerceIn(0.5, 1.5),
        "voiceRate" to voiceRate.coerceIn(0.5, 1.6),
        "autoSpeakReplies" to autoSpeakReplies,
        "proactiveBriefings" to proactiveBriefings,
        "aiModel" to resolvedModel().modelId,
        "themeMode" to resolvedThemeMode().name,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "assistantName" to assistantName.ifBlank { "JARVIS" }.take(40),
        "preset" to resolvedPreset().name,
        "customInstructions" to customInstructions.take(1000),
        "warmth" to warmth.coerceIn(0.0, 1.0),
        "conciseness" to conciseness.coerceIn(0.0, 1.0),
        "humor" to humor.coerceIn(0.0, 1.0),
        "voicePitch" to voicePitch.coerceIn(0.5, 1.5),
        "voiceRate" to voiceRate.coerceIn(0.5, 1.6),
        "autoSpeakReplies" to autoSpeakReplies,
        "proactiveBriefings" to proactiveBriefings,
        "aiModel" to resolvedModel().modelId,
        "themeMode" to resolvedThemeMode().name,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val lastMessagePreview: String = "",
    val isPinned: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val sender: String = MessageSender.USER.name,
    val content: String = "",
    val actionBadge: String? = null,
    val source: String = MessageSource.TEXT.name,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isUser(): Boolean = sender == MessageSender.USER.name
}

@IgnoreExtraProperties
data class AssistantTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = "General",
    val priority: String = TaskPriority.MEDIUM.name,
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedPriority(): TaskPriority =
        TaskPriority.entries.firstOrNull { it.name == priority } ?: TaskPriority.MEDIUM
}

@IgnoreExtraProperties
data class AssistantReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val triggerAtMillis: Long = System.currentTimeMillis(),
    val isDone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
