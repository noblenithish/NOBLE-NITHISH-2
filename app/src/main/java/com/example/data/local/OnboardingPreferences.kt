package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis_onboarding_prefs")

class OnboardingPreferences(private val context: Context) {
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")

    val onboardingDone: Flow<Boolean> = context.jarvisDataStore.data.map { prefs ->
        prefs[keyOnboardingDone] ?: false
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[keyOnboardingDone] = done
        }
    }
}
