package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.reminder.ReminderScheduler
import com.example.data.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserProfileRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            val safeName = displayName.ifBlank { "JARVIS User" }.take(100)
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", userId)
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty."))
        val docRef = db.collection("users").document(userId)
        return try {
            docRef.update(
                mapOf(
                    "displayName" to trimmed.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}

class PreferencesRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observePreferences(userId: String): Flow<AssistantPreferences> {
        val docRef = db.collection("users").document(userId).collection("preferences").document("default")
        return docRef.snapshots()
            .map { snap ->
                snap.toObject(AssistantPreferences::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: AssistantPreferences(userId = userId)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun getPreferencesOrDefault(userId: String): AssistantPreferences {
        val docRef = db.collection("users").document(userId).collection("preferences").document("default")
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(AssistantPreferences::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: AssistantPreferences(userId = userId)
            } else {
                val defaults = AssistantPreferences(userId = userId)
                docRef.set(defaults.toCreateMap(userId)).await()
                defaults
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            AssistantPreferences(userId = userId)
        }
    }

    suspend fun savePreferences(userId: String, prefs: AssistantPreferences): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("preferences").document("default")
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(prefs.toUpdateMap()).await()
            } else {
                docRef.set(prefs.toCreateMap(userId)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        val colRef = db.collection("users").document(userId).collection("conversations")
        return colRef
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.isPinned }
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> {
        val colRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
            .collection("messages")
        return colRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun createConversation(userId: String, title: String = "New Conversation"): Result<String> {
        val convId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("conversations").document(convId)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.ifBlank { "New Conversation" }.take(120),
            "lastMessagePreview" to "",
            "isPinned" to false,
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        userId: String,
        conversationId: String,
        sender: MessageSender,
        content: String,
        source: MessageSource = MessageSource.TEXT,
        actionBadge: String? = null
    ): Result<String> {
        val convRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
        val msgId = UUID.randomUUID().toString()
        val msgRef = convRef.collection("messages").document(msgId)
        val safeContent = content.trim().take(20_000)
        if (safeContent.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty."))

        return try {
            val convSnap = convRef.get().await()
            val batch = db.batch()
            if (!convSnap.exists()) {
                val initialTitle = if (sender == MessageSender.USER) safeContent.take(48) else "New Conversation"
                batch.set(
                    convRef,
                    mapOf(
                        "userId" to userId,
                        "title" to initialTitle.ifBlank { "New Conversation" },
                        "lastMessagePreview" to safeContent.take(180),
                        "isPinned" to false,
                        "messageCount" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val currentTitle = convSnap.getString("title").orEmpty()
                val nextTitle = if ((currentTitle.isBlank() || currentTitle == "New Conversation" || currentTitle == "Voice Session") &&
                    sender == MessageSender.USER
                ) {
                    safeContent.take(48)
                } else {
                    currentTitle.ifBlank { "New Conversation" }
                }
                val currentCount = convSnap.getLong("messageCount") ?: 0L
                batch.update(
                    convRef,
                    mapOf(
                        "title" to nextTitle.take(120),
                        "lastMessagePreview" to safeContent.take(180),
                        "messageCount" to (currentCount + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val msgPayload = buildMap<String, Any> {
                put("userId", userId)
                put("conversationId", conversationId)
                put("sender", sender.name)
                put("content", safeContent)
                if (!actionBadge.isNullOrBlank()) put("actionBadge", actionBadge.take(120))
                put("source", source.name)
                put("createdAt", FieldValue.serverTimestamp())
            }
            batch.set(msgRef, msgPayload)
            batch.commit().await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun getRecentMessages(userId: String, conversationId: String, limit: Int = 12): List<ChatMessage> {
        val colRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
            .collection("messages")
        return try {
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .reversed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            emptyList()
        }
    }

    suspend fun togglePin(userId: String, conversationId: String, pinned: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "isPinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> {
        val convRef = db.collection("users").document(userId).collection("conversations").document(conversationId)
        return try {
            val messages = convRef.collection("messages").get().await()
            val batch = db.batch()
            messages.documents.forEach { batch.delete(it.reference) }
            batch.delete(convRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllConversations(userId: String): Result<Unit> {
        val colRef = db.collection("users").document(userId).collection("conversations")
        return try {
            val convs = colRef.get().await()
            for (conv in convs.documents) {
                deleteConversation(userId, conv.id)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            Result.failure(e)
        }
    }
}

class AssistantRepository(
    private val db: FirebaseFirestore,
    private val reminderScheduler: ReminderScheduler? = null
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        ReminderScheduler(context.applicationContext)
    )

    fun observeTasks(userId: String): Flow<List<AssistantTask>> {
        val colRef = db.collection("users").document(userId).collection("tasks")
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AssistantTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<AssistantTask> { it.isCompleted }
                            .thenByDescending { it.resolvedPriority().ordinal }
                    )
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeReminders(userId: String): Flow<List<AssistantReminder>> {
        val colRef = db.collection("users").document(userId).collection("reminders")
        return colRef
            .orderBy("triggerAtMillis", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AssistantReminder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.isDone }
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun createTask(
        userId: String,
        title: String,
        notes: String = "",
        category: String = "General",
        priority: TaskPriority = TaskPriority.MEDIUM
    ): Result<String> {
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isBlank()) return Result.failure(IllegalArgumentException("Task title cannot be empty."))
        val taskId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("tasks").document(taskId)
        val payload = mapOf(
            "userId" to userId,
            "title" to cleanTitle,
            "notes" to notes.trim().take(1000),
            "category" to category.trim().ifBlank { "General" }.take(50),
            "priority" to priority.name,
            "isCompleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleTask(userId: String, taskId: String, completed: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("tasks").document(taskId)
        return try {
            docRef.update(
                mapOf(
                    "isCompleted" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createReminder(userId: String, title: String, triggerAtMillis: Long): Result<String> {
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isBlank()) return Result.failure(IllegalArgumentException("Reminder title cannot be empty."))
        val reminderId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("reminders").document(reminderId)
        val safeTrigger = triggerAtMillis.coerceAtLeast(1L)
        val payload = mapOf(
            "userId" to userId,
            "title" to cleanTitle,
            "triggerAtMillis" to safeTrigger,
            "isDone" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            reminderScheduler?.schedule(reminderId, cleanTitle, safeTrigger)
            Result.success(reminderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleReminder(userId: String, reminderId: String, done: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("reminders").document(reminderId)
        return try {
            docRef.update(
                mapOf(
                    "isDone" to done,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (done) reminderScheduler?.cancel(reminderId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("reminders").document(reminderId)
        return try {
            docRef.delete().await()
            reminderScheduler?.cancel(reminderId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
