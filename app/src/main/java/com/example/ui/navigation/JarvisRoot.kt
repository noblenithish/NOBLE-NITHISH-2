package com.example.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.R
import com.example.data.model.AppThemeMode
import com.example.data.model.AssistantPreferences
import com.example.di.AppContainer
import com.example.ui.assistant.AssistantScreen
import com.example.ui.assistant.AssistantViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.PersonalityScreen
import com.example.ui.settings.ProfileSettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.JarvisTheme
import com.example.ui.voice.VoiceModeScreen
import com.example.ui.voice.VoiceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    CHAT("chat", R.string.nav_chat, Icons.Rounded.ChatBubbleOutline),
    VOICE("voice", R.string.nav_voice, Icons.Rounded.GraphicEq),
    ASSISTANT("assistant", R.string.nav_assistant, Icons.Rounded.CheckCircleOutline),
    HISTORY("history", R.string.nav_history, Icons.Rounded.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.PersonOutline)
}

private const val ROUTE_PERSONALITY = "personality"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun JarvisRoot(container: AppContainer) {
    val onboardingDone by container.onboardingPreferences.onboardingDone
        .collectAsStateWithLifecycle(initialValue = null)
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var autoSignInAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingDone, currentUser) {
        if (onboardingDone == true && currentUser == null && !autoSignInAttempted) {
            autoSignInAttempted = true
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val activeUid = currentUser?.uid
    val themeMode = if (activeUid != null) {
        val prefs by remember(activeUid) {
            container.preferencesRepository.observePreferences(activeUid)
                .catch { emit(AssistantPreferences(userId = activeUid)) }
        }.collectAsStateWithLifecycle(initialValue = AssistantPreferences(userId = activeUid))
        prefs.resolvedThemeMode()
    } else {
        AppThemeMode.DARK
    }

    JarvisTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                onboardingDone == null -> Box(Modifier.fillMaxSize())
                onboardingDone == false -> OnboardingScreen(
                    onFinish = {
                        scope.launch { container.onboardingPreferences.setOnboardingDone(true) }
                    }
                )
                currentUser == null -> AuthScreen(onAuthSuccess = {})
                else -> {
                    val user = currentUser!!
                    LaunchedEffect(user.uid) {
                        container.userProfileRepository.ensureUserProfile(
                            userId = user.uid,
                            displayName = user.displayName ?: "JARVIS User",
                            email = user.email,
                            photoUrl = user.photoUrl?.toString()
                        )
                        container.preferencesRepository.getPreferencesOrDefault(user.uid)
                    }
                    key(user.uid) {
                        AuthenticatedShell(
                            currentUserId = user.uid,
                            container = container
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    container: AppContainer
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val factory = remember(currentUserId) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(
                    userId = currentUserId,
                    userProfileRepository = container.userProfileRepository,
                    preferencesRepository = container.preferencesRepository,
                    chatRepository = container.chatRepository,
                    assistantRepository = container.assistantRepository,
                    aiService = container.aiService,
                    speechOutput = container.speechOutput
                ) as T

                modelClass.isAssignableFrom(VoiceViewModel::class.java) -> VoiceViewModel(
                    userId = currentUserId,
                    userProfileRepository = container.userProfileRepository,
                    preferencesRepository = container.preferencesRepository,
                    chatRepository = container.chatRepository,
                    assistantRepository = container.assistantRepository,
                    aiService = container.aiService,
                    speechInput = container.speechInput,
                    speechOutput = container.speechOutput
                ) as T

                modelClass.isAssignableFrom(AssistantViewModel::class.java) -> AssistantViewModel(
                    userId = currentUserId,
                    assistantRepository = container.assistantRepository
                ) as T

                modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(
                    userId = currentUserId,
                    chatRepository = container.chatRepository
                ) as T

                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                    userId = currentUserId,
                    userProfileRepository = container.userProfileRepository,
                    preferencesRepository = container.preferencesRepository,
                    chatRepository = container.chatRepository
                ) as T

                else -> error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    val chatVm: ChatViewModel = viewModel(key = "chat_$currentUserId", factory = factory)
    val voiceVm: VoiceViewModel = viewModel(key = "voice_$currentUserId", factory = factory)
    val assistantVm: AssistantViewModel = viewModel(key = "assistant_$currentUserId", factory = factory)
    val historyVm: HistoryViewModel = viewModel(key = "history_$currentUserId", factory = factory)
    val settingsVm: SettingsViewModel = viewModel(key = "settings_$currentUserId", factory = factory)

    val showNavigationChrome = currentRoute in MainTab.entries.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showNavigationChrome && !useRail) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(MainTab.CHAT.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showNavigationChrome && useRail) {
                    NavigationRail {
                        MainTab.entries.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(MainTab.CHAT.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MainTab.CHAT.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(MainTab.CHAT.route) {
                        ChatScreen(
                            viewModel = chatVm,
                            onOpenVoiceMode = {
                                navController.navigate(MainTab.VOICE.route) {
                                    popUpTo(MainTab.CHAT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenPersonality = { navController.navigate(ROUTE_PERSONALITY) }
                        )
                    }
                    composable(MainTab.VOICE.route) {
                        VoiceModeScreen(viewModel = voiceVm)
                    }
                    composable(MainTab.ASSISTANT.route) {
                        AssistantScreen(
                            viewModel = assistantVm,
                            onAskJarvis = { prompt ->
                                chatVm.startNewConversation()
                                chatVm.sendMessage(prompt)
                                navController.navigate(MainTab.CHAT.route) {
                                    popUpTo(MainTab.CHAT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(MainTab.HISTORY.route) {
                        HistoryScreen(
                            viewModel = historyVm,
                            onOpenConversation = { convId ->
                                chatVm.selectConversation(convId)
                                navController.navigate(MainTab.CHAT.route) {
                                    popUpTo(MainTab.CHAT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(MainTab.SETTINGS.route) {
                        ProfileSettingsScreen(
                            viewModel = settingsVm,
                            onOpenPersonality = { navController.navigate(ROUTE_PERSONALITY) },
                            onSignedOut = {}
                        )
                    }
                    composable(ROUTE_PERSONALITY) {
                        PersonalityScreen(
                            viewModel = settingsVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
