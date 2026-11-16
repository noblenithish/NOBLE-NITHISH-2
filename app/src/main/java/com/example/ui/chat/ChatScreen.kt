package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender

private val quickPrompts = listOf(
    "Give me my daily briefing",
    "Remind me in 20 minutes to stretch",
    "Add a high priority task to review notes",
    "Help me plan a focused 45-minute sprint"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenVoiceMode: () -> Unit,
    onOpenPersonality: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingReply) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_avatar),
                        contentDescription = stringResource(R.string.cd_jarvis_avatar),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(state.assistantName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.presetLabel} • ${state.modelLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenPersonality,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_personality_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Customize Personality")
                }
                IconButton(
                    onClick = { viewModel.startNewConversation() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_new_button")
                ) {
                    Icon(Icons.Rounded.AddComment, contentDescription = "New Conversation")
                }
            }
        )

        if (state.messages.isEmpty() && state.streamingReply == null && !state.isThinking) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_avatar),
                        contentDescription = stringResource(R.string.cd_jarvis_avatar),
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.chat_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chat_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onSpeak = { viewModel.speakMessage(message.content) }
                    )
                }
                state.streamingReply?.let { partial ->
                    item(key = "streaming_bubble") {
                        ChatBubble(
                            message = ChatMessage(
                                id = "streaming",
                                sender = MessageSender.ASSISTANT.name,
                                content = partial
                            ),
                            onSpeak = {},
                            isStreaming = true
                        )
                    }
                }
                if (state.isThinking && state.streamingReply == null) {
                    item(key = "thinking_indicator") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${state.assistantName} is thinking...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { suggestion ->
                AssistChip(
                    onClick = { viewModel.sendMessage(suggestion) },
                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.testTag("quick_prompt_chip")
                )
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenVoiceMode,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_voice_shortcut_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Switch to Voice Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message ${state.assistantName}...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val toSend = input
                        input = ""
                        viewModel.sendMessage(toSend)
                    },
                    enabled = input.isNotBlank() && !state.isThinking,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Message")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    isStreaming: Boolean = false
) {
    val isUser = message.isUser()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                message.actionBadge?.let { badge ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (!isUser && !isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.VolumeUp,
                                contentDescription = "Read aloud",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(message.content)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
