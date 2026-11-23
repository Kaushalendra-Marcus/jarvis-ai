package ai.lumo.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import ai.lumo.app.R
import ai.lumo.app.presentation.agent.AgentScreen
import ai.lumo.app.presentation.agent.AgentViewModel
import ai.lumo.app.presentation.memory.MemoryScreen
import ai.lumo.app.presentation.memory.MemoryViewModel
import ai.lumo.app.presentation.settings.SettingsScreen
import ai.lumo.app.presentation.settings.SettingsViewModel
import ai.lumo.app.presentation.tasks.TasksScreen
import ai.lumo.app.presentation.tasks.TasksViewModel
import ai.lumo.app.presentation.browser.ControlledBrowserScreen
import ai.lumo.app.service.web.BrowserAgentController
import ai.lumo.app.presentation.theme.*

sealed class Screen(val route: String, val label: String, val icon: Int) {
    object Agent : Screen("agent", "Home", R.drawable.ic_orb_home)
    object Memory : Screen("memory", "Memory", R.drawable.ic_orb_memory)
    object Tasks : Screen("tasks", "Tasks", R.drawable.ic_orb_routine)
    object Settings : Screen("settings", "Settings", R.drawable.ic_orb_tune)
    object Integrations : Screen("integrations", "Links", R.drawable.ic_orb_share)
    object Browser : Screen("browser?url={url}", "Web", R.drawable.ic_orb_globe)
    object Mailbox : Screen("mailbox", "Mail", R.drawable.ic_orb_inbox)
    object Chat : Screen("chat", "Chat", R.drawable.ic_orb_chat)
}

@Composable
fun AppNavigation(
    agentViewModel: AgentViewModel,
    memoryViewModel: MemoryViewModel,
    tasksViewModel: TasksViewModel,
    settingsViewModel: SettingsViewModel,
    mailboxViewModel: ai.lumo.app.presentation.mailbox.MailboxViewModel,
    browserAgentController: BrowserAgentController,
    browserStore: ai.lumo.app.service.web.BrowserStore,
    integrationRegistry: ai.lumo.app.domain.integrations.IntegrationRegistry,
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
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { s ->
                            val sel = cur == s.route
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        navController.navigate(s.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    painterResource(s.icon),
                                    contentDescription = s.label,
                                    tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    s.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
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
                MemoryScreen(
                    viewModel = memoryViewModel,
                    onOpenSettings = { openTab(Screen.Settings.route) }
                )
            }
            composable(Screen.Tasks.route) { TasksScreen(viewModel = tasksViewModel) }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel, onConnectGmail = onConnectGmail,
                    onDisconnectGmail = settingsViewModel::disconnectGmail,
                    onOpenMailbox = { navController.navigate(Screen.Mailbox.route) { launchSingleTop = true } })
            }
            composable(Screen.Integrations.route) {
                ai.lumo.app.presentation.integrations.IntegrationsScreen(registry = integrationRegistry, onConnectGmail = onConnectGmail)
            }
            composable(Screen.Mailbox.route) {
                ai.lumo.app.presentation.mailbox.MailboxScreen(viewModel = mailboxViewModel,
                    onBack = { navController.popBackStack() },
                    onAskAgent = { p, a -> navController.navigate(Screen.Chat.route) { launchSingleTop = true }; agentViewModel.sendPrompt(p, attachment = a) })
            }
            composable(Screen.Chat.route) {
                ai.lumo.app.presentation.agent.ChatScreen(
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
