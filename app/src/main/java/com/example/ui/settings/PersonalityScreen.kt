package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PersonalityPreset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences

    var assistantNameDraft by remember(prefs.assistantName) { mutableStateOf(prefs.assistantName) }
    var customInstructionsDraft by remember(prefs.customInstructions) { mutableStateOf(prefs.customInstructions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.personality_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("personality_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = assistantNameDraft,
                onValueChange = { assistantNameDraft = it },
                label = { Text("Assistant Name") },
                singleLine = true,
                trailingIcon = {
                    if (assistantNameDraft.trim() != prefs.assistantName && assistantNameDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.updatePreferences { it.copy(assistantName = assistantNameDraft.trim()) }
                            }
                        ) { Text("Save") }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("assistant_name_input")
            )

            Text("Personality Preset", style = MaterialTheme.typography.titleMedium)
            PersonalityPreset.entries.forEach { preset ->
                val selected = prefs.resolvedPreset() == preset
                ElevatedCard(
                    onClick = { viewModel.updatePreferences { it.copy(preset = preset.name) } },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("preset_${preset.name}")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            preset.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customInstructionsDraft,
                onValueChange = { customInstructionsDraft = it },
                label = { Text("Custom Instructions (optional)") },
                placeholder = { Text("e.g., Always summarize action items at the end...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("custom_instructions_input")
            )
            if (customInstructionsDraft != prefs.customInstructions) {
                Button(
                    onClick = {
                        viewModel.updatePreferences { it.copy(customInstructions = customInstructionsDraft) }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Apply Custom Instructions")
                }
            }

            HorizontalDivider()
            Text("Tone Calibration", style = MaterialTheme.typography.titleMedium)
            PersonalitySlider(
                label = "Warmth (${(prefs.warmth * 100).toInt()}%)",
                value = prefs.warmth.toFloat(),
                onChangeFinished = { v -> viewModel.updatePreferences { it.copy(warmth = v.toDouble()) } }
            )
            PersonalitySlider(
                label = "Conciseness (${(prefs.conciseness * 100).toInt()}%)",
                value = prefs.conciseness.toFloat(),
                onChangeFinished = { v -> viewModel.updatePreferences { it.copy(conciseness = v.toDouble()) } }
            )
            PersonalitySlider(
                label = "Wit & Humor (${(prefs.humor * 100).toInt()}%)",
                value = prefs.humor.toFloat(),
                onChangeFinished = { v -> viewModel.updatePreferences { it.copy(humor = v.toDouble()) } }
            )

            HorizontalDivider()
            Text("Voice Tuning", style = MaterialTheme.typography.titleMedium)
            PersonalitySlider(
                label = String.format(Locale.US, "Voice Pitch (%.2fx)", prefs.voicePitch),
                value = prefs.voicePitch.toFloat(),
                range = 0.7f..1.3f,
                onChangeFinished = { v -> viewModel.updatePreferences { it.copy(voicePitch = v.toDouble()) } }
            )
            PersonalitySlider(
                label = String.format(Locale.US, "Speech Rate (%.2fx)", prefs.voiceRate),
                value = prefs.voiceRate.toFloat(),
                range = 0.7f..1.4f,
                onChangeFinished = { v -> viewModel.updatePreferences { it.copy(voiceRate = v.toDouble()) } }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Speak Replies in Voice Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automatically read responses aloud after listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.autoSpeakReplies,
                    onCheckedChange = { checked ->
                        viewModel.updatePreferences { it.copy(autoSpeakReplies = checked) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonalitySlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onChangeFinished: (Float) -> Unit
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onChangeFinished(localValue) },
            valueRange = range
        )
    }
}
