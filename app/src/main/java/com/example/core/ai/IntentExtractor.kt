package com.example.core.ai

import com.example.data.model.TaskPriority

sealed interface DetectedAssistantAction {
    data class CreateTask(val title: String, val priority: TaskPriority) : DetectedAssistantAction
    data class CreateReminder(
        val title: String,
        val triggerAtMillis: Long,
        val humanDelay: String
    ) : DetectedAssistantAction
}

object IntentExtractor {
    private val reminderRegex = Regex(
        """(?i)^(?:please\s+)?(?:jarvis,?\s+)?(?:remind me|set a reminder)(?:\s+in\s+(\d+)\s+(minute|minutes|min|mins|hour|hours|hr|hrs))?\s*(?:to\s+)?(.+)"""
    )
    private val taskRegex = Regex(
        """(?i)^(?:please\s+)?(?:jarvis,?\s+)?(?:add (?:a )?(high|medium|low)?\s*(?:priority\s+)?task(?:\s+to)?|create (?:a )?task(?:\s+to)?|todo:?)\s+(.+)"""
    )

    fun extract(input: String, nowMillis: Long = System.currentTimeMillis()): DetectedAssistantAction? {
        val trimmed = input.trim()

        reminderRegex.find(trimmed)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: 15L
            val unit = match.groupValues[2].lowercase()
            val body = match.groupValues[3].trim().removeSuffix(".")
            if (body.isNotBlank()) {
                val isHours = unit.startsWith("h")
                val delayMillis = if (isHours) amount * 3_600_000L else amount * 60_000L
                val delayLabel = if (match.groupValues[1].isBlank()) {
                    "15 minutes"
                } else {
                    "$amount ${if (isHours) if (amount == 1L) "hour" else "hours" else if (amount == 1L) "minute" else "minutes"}"
                }
                return DetectedAssistantAction.CreateReminder(
                    title = body,
                    triggerAtMillis = nowMillis + delayMillis,
                    humanDelay = delayLabel
                )
            }
        }

        taskRegex.find(trimmed)?.let { match ->
            val priority = when (match.groupValues[1].lowercase()) {
                "high" -> TaskPriority.HIGH
                "low" -> TaskPriority.LOW
                else -> TaskPriority.MEDIUM
            }
            val title = match.groupValues[2].trim().removeSuffix(".")
            if (title.isNotBlank()) {
                return DetectedAssistantAction.CreateTask(title = title, priority = priority)
            }
        }
        return null
    }
}
