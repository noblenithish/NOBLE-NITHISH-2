package com.example.di

import android.content.Context
import com.example.R
import com.example.core.ai.FirebaseJarvisAiService
import com.example.core.ai.JarvisAiService
import com.example.core.reminder.ReminderScheduler
import com.example.core.speech.AndroidSpeechInputService
import com.example.core.speech.AndroidSpeechOutputService
import com.example.core.speech.SpeechInputService
import com.example.core.speech.SpeechOutputService
import com.example.data.local.OnboardingPreferences
import com.example.data.repository.AssistantRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val databaseId: String = appContext.getString(R.string.firestore_database_id)
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(databaseId)
    }

    val onboardingPreferences = OnboardingPreferences(appContext)
    val reminderScheduler = ReminderScheduler(appContext).apply { ensureChannel() }

    val userProfileRepository by lazy { UserProfileRepository(firestore) }
    val preferencesRepository by lazy { PreferencesRepository(firestore) }
    val chatRepository by lazy { ChatRepository(firestore) }
    val assistantRepository by lazy { AssistantRepository(firestore, reminderScheduler) }

    val aiService: JarvisAiService by lazy { FirebaseJarvisAiService() }
    val speechInput: SpeechInputService by lazy { AndroidSpeechInputService(appContext) }
    val speechOutput: SpeechOutputService by lazy { AndroidSpeechOutputService(appContext) }
}
