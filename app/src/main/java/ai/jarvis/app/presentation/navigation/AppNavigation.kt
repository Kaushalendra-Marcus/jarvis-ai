package ai.jarvis.app.presentation.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import ai.jarvis.app.R
import ai.jarvis.app.presentation.agent.AgentScreen
import ai.jarvis.app.presentation.agent.AgentViewModel
import ai.jarvis.app.presentation.memory.MemoryScreen
import ai.jarvis.app.presentation.memory.MemoryViewModel
import ai.jarvis.app.presentation.settings.SettingsScreen
import ai.jarvis.app.presentation.settings.SettingsViewModel
import ai.jarvis.app.presentation.tasks.TasksScreen
import ai.jarvis.app.presentation.tasks.TasksViewModel
import ai.jarvis.app.presentation.browser.ControlledBrowserScreen
import ai.jarvis.app.service.web.BrowserAgentController
import ai.jarvis.app.presentation.theme.*

sealed class Screen(
    val route: String,
    val label: String,
    val iconRes: Int,
    val anim: TabAnim = TabAnim.SCALE
) {
    object Agent : Screen("agent", "Home", iconRes = R.drawable.ic_orb_home, anim = TabAnim.SCALE)
    object Memory : Screen("memory", "Memory", iconRes = R.drawable.ic_orb_memory, anim = TabAnim.ROTATE)
    object Tasks : Screen("tasks", "Tasks", iconRes = R.drawable.ic_custom_task_list, anim = TabAnim.BOUNCE)
    object Settings : Screen("settings", "Settings", iconRes = R.drawable.ic_custom_setting, anim = TabAnim.ROTATE)
    object Integrations : Screen("integrations", "Connect", iconRes = R.drawable.ic_orb_share, anim = TabAnim.PULSE)
    object Browser : Screen("browser?url={url}", "Web", iconRes = R.drawable.ic_orb_globe)
    object Mailbox : Screen("mailbox", "Mail", iconRes = R.drawable.ic_orb_inbox)
    object Chat : Screen("chat", "Chat", iconRes = R.drawable.ic_orb_chat)
}

/** Per-tab switch animation. */
enum class TabAnim { SCALE, ROTATE, BOUNCE, PULSE }

@Composable
fun AppNavigation(
    agentViewModel: AgentViewModel,
    memoryViewModel: MemoryViewModel,
    tasksViewModel: TasksViewModel,
    settingsViewModel: SettingsViewModel,
    mailboxViewModel: ai.jarvis.app.presentation.mailbox.MailboxViewModel,
    browserAgentController: BrowserAgentController,
    browserStore: ai.jarvis.app.service.web.BrowserStore,
    integrationRegistry: ai.jarvis.app.domain.integrations.IntegrationRegistry,
    onConnectGmail: () -> Unit,
    shortcut: String? = null,
    onShortcutHandled: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    LaunchedEffect(browserAgentController, navController) {
        browserAgentController.browserScreenRequests.collect {
            if (navController.currentDestination?.route != Screen.Browser.route) {
                navController.navigate("browser?url=") { launchSingleTop = true }
            }
        }
    }
    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(shortcut) {
        when (shortcut) {
            "voice" -> { openTab(Screen.Agent.route); agentViewModel.startVoiceInput() }
            "inbox" -> { openTab(Screen.Agent.route); agentViewModel.sendPrompt("Triage inbox. Flag + draft 2.") }
            "tasks" -> openTab(Screen.Tasks.route)
            "settings" -> openTab(Screen.Settings.route)
            "browser" -> navController.navigate("browser?url=") { launchSingleTop = true }
        }
        if (shortcut != null) onShortcutHandled()
    }
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            openTab(Screen.Agent.route)
            agentViewModel.sendPrompt("5-line version:", attachment = sharedText.take(8_000))
            onSharedTextHandled()
        }
    }
    val items = listOf(Screen.Agent, Screen.Memory, Screen.Tasks, Screen.Integrations, Screen.Settings)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val cur = entry?.destination?.route
            if (cur in items.map { it.route }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF0B0B0D))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { s ->
                            val sel = cur == s.route
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f).clip(CircleShape)
                                    .clickable {
                                        navController.navigate(s.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                AnimatedTabIcon(
                                    screen = s,
                                    selected = sel,
                                    tint = if (sel) Color.White else Color.White.copy(alpha = 0.38f),
                                    iconSize = 24.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = navController, startDestination = Screen.Agent.route,
            modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 28 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(220)) { it / 28 } }
        ) {
            composable(Screen.Agent.route) {
                AgentScreen(viewModel = agentViewModel,
                    onOpenBrowser = { navController.navigate("browser?url=") },
                    onOpenMailbox = { navController.navigate(Screen.Mailbox.route) { launchSingleTop = true } },
                    onOpenSettings = { openTab(Screen.Settings.route) },
                    onOpenChat = { navController.navigate(Screen.Chat.route) { launchSingleTop = true } })
            }
            composable(Screen.Memory.route) {
                MemoryScreen(viewModel = memoryViewModel)
            }
            composable(Screen.Tasks.route) { TasksScreen(viewModel = tasksViewModel) }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel, onConnectGmail = onConnectGmail,
                    onDisconnectGmail = settingsViewModel::disconnectGmail,
                    onOpenMailbox = { navController.navigate(Screen.Mailbox.route) { launchSingleTop = true } })
            }
            composable(Screen.Integrations.route) {
                val gmailState by settingsViewModel.gmailConnectionState.collectAsState()
                ai.jarvis.app.presentation.integrations.IntegrationsScreen(
                    registry = integrationRegistry,
                    onConnectGmail = onConnectGmail,
                    isGmailLive = gmailState.startsWith("Connected as "),
                    onDisconnectGmail = settingsViewModel::disconnectGmail,
                    onAuthorizeApp = { url ->
                        navController.navigate("browser?url=${Uri.encode(url)}") { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Mailbox.route) {
                ai.jarvis.app.presentation.mailbox.MailboxScreen(viewModel = mailboxViewModel,
                    onBack = { navController.popBackStack() },
                    onAskAgent = { p, a -> navController.navigate(Screen.Chat.route) { launchSingleTop = true }; agentViewModel.sendPrompt(p, attachment = a) })
            }
            composable(Screen.Chat.route) {
                ai.jarvis.app.presentation.agent.ChatScreen(
                    viewModel = agentViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Browser.route) { e ->
                ControlledBrowserScreen(initialUrl = e.arguments?.getString("url").orEmpty(),
                    controller = browserAgentController, store = browserStore,
                    onBack = { navController.popBackStack() },
                    onAskAgent = { p, a -> navController.navigate(Screen.Chat.route) { launchSingleTop = true }; agentViewModel.sendPrompt(p, attachment = a) })
            }
        }
    }
}

/** Tab icon with a one-shot switch animation that is specific to each tab. */
@Composable
private fun AnimatedTabIcon(
    screen: Screen,
    selected: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    iconSize: androidx.compose.ui.unit.Dp = 15.dp
) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (!selected) return@LaunchedEffect
        when (screen.anim) {
            TabAnim.SCALE -> {
                scale.snapTo(0.6f)
                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
            }
            TabAnim.ROTATE -> {
                rotation.snapTo(0f)
                rotation.animateTo(360f, tween(500, easing = FastOutSlowInEasing))
            }
            TabAnim.BOUNCE -> {
                offsetY.snapTo(-10f)
                offsetY.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 600f))
            }
            TabAnim.PULSE -> {
                scale.snapTo(1f)
                scale.animateTo(1.3f, tween(150))
                scale.animateTo(1f, tween(150))
            }
        }
    }
    val modifier = Modifier.size(iconSize).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = rotation.value
        translationY = offsetY.value
    }
    Icon(
        painterResource(screen.iconRes),
        contentDescription = screen.label,
        tint = tint,
        modifier = modifier
    )
}
