package com.example.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R

@Composable
fun VoiceModeScreen(viewModel: VoiceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.onOrbTap()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voiceOrb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "orbit"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    state.assistantName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.phase != VoiceOrbPhase.IDLE) {
                    FilledTonalIconButton(
                        onClick = viewModel::stopAllVoice,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("voice_stop_button")
                    ) {
                        Icon(Icons.Rounded.VolumeOff, contentDescription = "Stop voice")
                    }
                }
                FilterChip(
                    selected = state.handsFreeLoop,
                    onClick = viewModel::toggleHandsFree,
                    label = { Text(stringResource(R.string.voice_hands_free)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.AllInclusive, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("voice_hands_free_chip")
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            val tertiary = MaterialTheme.colorScheme.tertiary

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = {
                            if (hasMicPermission) {
                                viewModel.onOrbTap()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                    .testTag("voice_orb_button")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val activeColor = when (state.phase) {
                        VoiceOrbPhase.IDLE -> primary
                        VoiceOrbPhase.LISTENING -> tertiary
                        VoiceOrbPhase.THINKING -> primary
                        VoiceOrbPhase.SPEAKING -> secondary
                    }
                    val reactiveBoost = if (state.phase == VoiceOrbPhase.LISTENING) state.rmsLevel * 0.28f else 0f
                    val scale = pulse + reactiveBoost
                    val radius = size.minDimension * 0.32f * scale

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(activeColor.copy(alpha = 0.40f), activeColor.copy(alpha = 0.0f))
                        ),
                        radius = radius * 1.55f
                    )
                    drawCircle(
                        color = activeColor,
                        radius = radius,
                        style = Stroke(width = 5.dp.toPx())
                    )
                    drawArc(
                        color = activeColor.copy(alpha = 0.55f),
                        startAngle = rotation,
                        sweepAngle = 110f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - radius * 1.18f,
                            center.y - radius * 1.18f
                        ),
                        size = androidx.compose.ui.geometry.Size(radius * 2.36f, radius * 2.36f)
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.20f),
                        radius = radius * 0.82f
                    )
                }

                Icon(
                    imageVector = if (state.phase == VoiceOrbPhase.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = "Toggle Voice Interaction",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = when (state.phase) {
                    VoiceOrbPhase.IDLE -> if (hasMicPermission) "Tap orb to speak" else "Tap orb to grant microphone access"
                    VoiceOrbPhase.LISTENING -> "Listening..."
                    VoiceOrbPhase.THINKING -> "Synthesizing response..."
                    VoiceOrbPhase.SPEAKING -> "Speaking — tap orb to interrupt"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.liveTranscript.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"${state.liveTranscript}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 440.dp)
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = state.lastAssistantReply,
                    style = MaterialTheme.typography.bodyLarge
                )
                state.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
