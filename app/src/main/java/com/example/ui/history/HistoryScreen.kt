package com.example.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenConversation: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filtered = remember(state.conversations, query) {
        if (query.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.lastMessagePreview.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.history_title)) })

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search conversations...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("history_search_input")
        )

        state.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
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

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (query.isBlank()) "No conversations yet" else "No matching conversations",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start a chat or voice session with JARVIS and it will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { conv ->
                    val ts = (conv.updatedAt ?: conv.createdAt ?: Timestamp.now()).toDate()
                    val dateLabel = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(ts)
                    ElevatedCard(
                        onClick = { onOpenConversation(conv.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_item_${conv.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (conv.isPinned) {
                                        Icon(
                                            imageVector = Icons.Rounded.PushPin,
                                            contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (conv.lastMessagePreview.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = conv.lastMessagePreview,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "$dateLabel • ${conv.messageCount} messages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.togglePin(conv) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (conv.isPinned) "Unpin conversation" else "Pin conversation",
                                    tint = if (conv.isPinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete conversation"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
