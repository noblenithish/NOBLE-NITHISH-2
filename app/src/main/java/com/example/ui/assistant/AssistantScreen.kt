package com.example.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TaskPriority
import java.text.SimpleDateFormat
import java.util.*

private data class QuickAssistCard(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

private val everydayHelpers = listOf(
    QuickAssistCard(
        Icons.Rounded.WbSunny,
        "Daily Briefing",
        "Summarize priorities & plan",
        "Give me a crisp daily briefing based on my open tasks and active reminders."
    ),
    QuickAssistCard(
        Icons.Rounded.Timer,
        "Focus Sprint",
        "45-min deep work plan",
        "Help me structure a 45-minute deep work sprint with a quick break checklist."
    ),
    QuickAssistCard(
        Icons.Rounded.EditNote,
        "Draft Message",
        "Clean email or update",
        "Help me draft a concise, professional follow-up message."
    ),
    QuickAssistCard(
        Icons.Rounded.RestaurantMenu,
        "Meal Ideas",
        "3 quick healthy dinners",
        "Suggest 3 quick, balanced dinner ideas I can make in under 25 minutes."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onAskJarvis: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddTask by remember { mutableStateOf(false) }
    var showAddReminder by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showAddReminder = true
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.assistant_title)) })
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            showAddReminder = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                    text = { Text("Reminder") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("add_reminder_fab")
                )
                ExtendedFloatingActionButton(
                    onClick = { showAddTask = true },
                    icon = { Icon(Icons.Rounded.AddTask, contentDescription = null) },
                    text = { Text("Task") },
                    modifier = Modifier.testTag("add_task_fab")
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.error?.let { err ->
                item {
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
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::clearError) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            item {
                Text("Everyday Help", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(everydayHelpers) { helper ->
                        ElevatedCard(
                            onClick = { onAskJarvis(helper.prompt) },
                            modifier = Modifier
                                .width(166.dp)
                                .testTag("helper_card_${helper.title}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    imageVector = helper.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(helper.title, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    helper.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Upcoming Reminders (${state.reminders.count { !it.isDone }})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.reminders.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.Alarm,
                        message = "No reminders scheduled yet. Tap Reminder below or tell JARVIS \"Remind me in 20 minutes to stretch\"."
                    )
                }
            } else {
                items(state.reminders, key = { it.id }) { rem ->
                    val timeFmt = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
                        .format(Date(rem.triggerAtMillis))
                    Card(shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rem.isDone,
                                onCheckedChange = { viewModel.toggleReminder(rem) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rem.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (rem.isDone) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = timeFmt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reminder")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Tasks (${state.tasks.count { !it.isCompleted }} open)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.tasks.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.Checklist,
                        message = "Your task queue is clear. Add a task manually or ask JARVIS in Chat or Voice."
                    )
                }
            } else {
                items(state.tasks, key = { it.id }) { task ->
                    val priorityColor = when (task.resolvedPriority()) {
                        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
                        TaskPriority.LOW -> MaterialTheme.colorScheme.primary
                    }
                    Card(shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.toggleTask(task) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                )
                                if (task.notes.isNotBlank()) {
                                    Text(
                                        text = task.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = priorityColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = task.priority,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = priorityColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = task.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete task")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(84.dp)) }
        }
    }

    if (showAddTask) {
        var title by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Work") }
        var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text("New Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TaskPriority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addTask(title, notes, category, priority)
                        showAddTask = false
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("save_task_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTask = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddReminder) {
        var title by remember { mutableStateOf("") }
        var mins by remember { mutableIntStateOf(15) }

        AlertDialog(
            onDismissRequest = { showAddReminder = false },
            title = { Text("Schedule Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Remind me to...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reminder_title_input")
                    )
                    Text("Trigger in:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 15, 30, 60).forEach { m ->
                            FilterChip(
                                selected = mins == m,
                                onClick = { mins = m },
                                label = { Text("${m}m") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addReminder(title, mins)
                        showAddReminder = false
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("save_reminder_button")
                ) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminder = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptySectionCard(icon: ImageVector, message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
