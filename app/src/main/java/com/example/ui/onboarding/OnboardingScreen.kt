package com.example.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val badge: String,
    val title: String,
    val description: String,
    val showHeroArt: Boolean = false
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Rounded.AutoAwesome,
        badge = "CONVERSATIONAL AI",
        title = "Meet JARVIS",
        description = "Your intelligent all-in-one Android companion for natural conversations, instant synthesis, and effortless daily execution.",
        showHeroArt = true
    ),
    OnboardingPage(
        icon = Icons.Rounded.GraphicEq,
        badge = "VOICE INTERFACE",
        title = "Hands-Free Voice Mode",
        description = "Speak naturally with live reactive audio waves, interruptible spoken responses, and fine-tuned voice speed & pitch."
    ),
    OnboardingPage(
        icon = Icons.Rounded.TaskAlt,
        badge = "PERSONAL ASSISTANT",
        title = "Tasks, Reminders & Personality",
        description = "Turn everyday messages into scheduled reminders automatically and tune JARVIS's personality, warmth, and precision anytime."
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("onboarding_skip_button")
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = onboardingPages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (page.showHeroArt) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_jarvis_hero),
                            contentDescription = stringResource(R.string.cd_jarvis_hero),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp,
                        modifier = Modifier.size(124.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = page.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(onboardingPages.size) { idx ->
                val isSelected = pagerState.currentPage == idx
                val color by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    label = "dotColor"
                )
                val width by animateDpAsState(
                    if (isSelected) 28.dp else 8.dp,
                    label = "dotWidth"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (pagerState.currentPage < onboardingPages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinish()
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("onboarding_next_button")
        ) {
            Text(
                text = if (pagerState.currentPage == onboardingPages.lastIndex) {
                    stringResource(R.string.onboarding_get_started)
                } else {
                    stringResource(R.string.onboarding_continue)
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
