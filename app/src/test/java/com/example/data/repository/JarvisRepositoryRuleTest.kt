package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AssistantPreferences
import com.example.data.model.MessageSender
import com.example.data.model.MessageSource
import com.example.data.model.PersonalityPreset
import com.example.data.model.TaskPriority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class JarvisRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveTask_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = AssistantRepository(firestore)
        val title = "Prepare brief ${UUID.randomUUID().toString().take(8)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createTask(uid, title, notes = "Focus", category = "Work", priority = TaskPriority.HIGH)
        }
        assertTrue(createResult.isSuccess)
        val taskId = createResult.getOrThrow()

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        assertTrue(tasks.any { it.id == taskId && it.title == title })

        val toggleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleTask(uid, taskId, true)
        }
        assertTrue(toggleRes.isSuccess)

        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteTask(uid, taskId)
        }
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun crossUserTaskRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AssistantRepository(firestore)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(aliceUid, "Alice Secret Task").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = AssistantRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeTasks(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user observation")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        } finally {
            signInTestUser(ALICE_EMAIL)
            withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.deleteTask(aliceUid, taskId) }
        }
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val fakeUid = "alice_unauth_check"
        auth.signOut()
        val repo = AssistantRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks(fakeUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun chatConversationAndMessageAppend_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val chatRepo = ChatRepository(firestore)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.createConversation(uid, "Morning Sync").getOrThrow()
        }

        val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.appendMessage(
                userId = uid,
                conversationId = convId,
                sender = MessageSender.USER,
                content = "Remind me to call Alex",
                source = MessageSource.TEXT,
                actionBadge = "Reminder set"
            )
        }
        assertTrue(msgRes.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            chatRepo.observeMessages(uid, convId).first { it.isNotEmpty() }
        }
        assertEquals(1, messages.size)
        assertEquals("Reminder set", messages.first().actionBadge)

        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.deleteConversation(uid, convId)
        }
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun preferencesSaveAndLoad_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val prefsRepo = PreferencesRepository(firestore)
        val prefDoc = firestore.collection("users").document(uid)
            .collection("preferences").document("default")

        // Ensure clean initial state even if an earlier emulator run left persisted preferences
        withTimeout(DEFAULT_TIMEOUT_MS) {
            prefDoc.delete().await()
        }

        try {
            val initial = withTimeout(DEFAULT_TIMEOUT_MS) {
                prefsRepo.getPreferencesOrDefault(uid)
            }
            assertEquals("JARVIS", initial.assistantName)

            val updated = initial.copy(
                assistantName = "ATLAS",
                preset = PersonalityPreset.EXECUTIVE.name,
                warmth = 0.4,
                conciseness = 0.9
            )
            val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                prefsRepo.savePreferences(uid, updated)
            }
            assertTrue(saveRes.isSuccess)

            val observed = withTimeout(FLOW_TIMEOUT_MS) {
                prefsRepo.observePreferences(uid).first { it.assistantName == "ATLAS" }
            }
            assertEquals(PersonalityPreset.EXECUTIVE.name, observed.preset)
        } finally {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                prefDoc.delete().await()
            }
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@jarvistest.com"
        const val BOB_EMAIL = "bob@jarvistest.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
