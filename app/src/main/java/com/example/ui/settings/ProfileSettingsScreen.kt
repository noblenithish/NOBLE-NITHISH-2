package com.example.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AiModelOption
import com.example.data.model.AppThemeMode
import com.example.ui.auth.signOut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPersonality: () -> Unit,
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_jarvis_avatar),
                            contentDescription = stringResource(R.string.cd_jarvis_avatar),
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.profile?.displayName?.ifBlank { "JARVIS User" } ?: "JARVIS User",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = state.profile?.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showEditNameDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_name_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit display name")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onOpenPersonality,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_personality_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Customize Personality & Voice (${prefs.resolvedPreset().displayName})")
                    }
                }
            }

            state.statusMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = viewModel::clearStatus) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = viewModel::clearStatus) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            Text("Gemini AI Model", style = MaterialTheme.typography.titleMedium)
            AiModelOption.entries.forEach { option ->
                val selected = prefs.resolvedModel() == option
                Card(
                    onClick = { viewModel.updatePreferences { it.copy(aiModel = option.modelId) } },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.updatePreferences { it.copy(aiModel = option.modelId) } }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppThemeMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = prefs.resolvedThemeMode() == mode,
                        onClick = { viewModel.updatePreferences { it.copy(themeMode = mode.name) } },
                        shape = SegmentedButtonDefaults.itemShape(idx, AppThemeMode.entries.size)
                    ) {
                        Text(mode.label)
                    }
                }
            }

            HorizontalDivider()
            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("clear_history_button")
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear Conversation History")
            }

            Button(
                onClick = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = onSignedOut
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showEditNameDialog) {
        var nameDraft by remember { mutableStateOf(state.profile?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Update Display Name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(nameDraft)
                        showEditNameDialog = false
                    },
                    enabled = nameDraft.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Conversation History?") },
            text = { Text("This permanently deletes all saved chat and voice transcripts from your cloud workspace.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearConversationHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}
