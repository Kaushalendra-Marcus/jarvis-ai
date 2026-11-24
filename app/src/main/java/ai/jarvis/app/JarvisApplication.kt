package ai.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.data.local.AppDatabase
import ai.jarvis.app.data.local.JsonConversationStore
import ai.jarvis.app.data.local.FileConversationLibrary
import ai.jarvis.app.data.remote.ProviderAgentClient
import ai.jarvis.app.data.remote.BackendApiClient
import ai.jarvis.app.data.remote.BackendAuthManager
import ai.jarvis.app.data.remote.UserAccountManager
import ai.jarvis.app.data.repository.MemoryRepositoryImpl
import ai.jarvis.app.data.repository.TaskSchedulerRepositoryImpl
import ai.jarvis.app.data.tools.EmailTool
import ai.jarvis.app.data.mail.BackendMailService
import ai.jarvis.app.data.mail.DemoMailService
import ai.jarvis.app.data.mail.MailSourceManager
import ai.jarvis.app.data.mail.PrefsDemoMailStore
import ai.jarvis.app.domain.mail.MailGateway
import ai.jarvis.app.data.tools.GmailDraftTool
import ai.jarvis.app.data.tools.GmailReadTool
import ai.jarvis.app.data.tools.GmailSearchTool
import ai.jarvis.app.data.local.metrics.RoomPerformanceRecorder
import ai.jarvis.app.data.tools.BrowserAgentTool
import ai.jarvis.app.data.tools.MemoryTool
import ai.jarvis.app.data.tools.NotionTool
import ai.jarvis.app.data.tools.SchedulerTool
import ai.jarvis.app.data.tools.WebAutomationTool
import ai.jarvis.app.domain.orchestrator.AgentOrchestrator
import ai.jarvis.app.data.integrations.IntegrationRegistryImpl
import ai.jarvis.app.data.integrations.CustomIntegrationStore
import ai.jarvis.app.data.memory.HybridSemanticMemoryStore
import ai.jarvis.app.data.memory.Mem0SemanticMemoryStore
import ai.jarvis.app.data.memory.RoomSemanticMemoryStore
import ai.jarvis.app.domain.integrations.IntegrationRegistry
import ai.jarvis.app.domain.repository.MemoryRepository
import ai.jarvis.app.domain.repository.TaskSchedulerRepository
import ai.jarvis.app.domain.tool.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ai.jarvis.app.service.voice.VoiceManager
import ai.jarvis.app.service.web.ControlledWebScraper
import ai.jarvis.app.service.web.BrowserAgentController
import ai.jarvis.app.service.web.BrowserStore

class JarvisApplication : Application() {

    lateinit var securePreferences: SecurePreferences private set
    lateinit var backendAuthManager: BackendAuthManager private set
    lateinit var userAccountManager: UserAccountManager private set
    lateinit var mailService: MailGateway private set
    lateinit var mailSourceManager: MailSourceManager private set
    lateinit var database: AppDatabase private set
    lateinit var memoryRepository: MemoryRepository private set
    lateinit var taskSchedulerRepository: TaskSchedulerRepository private set
    lateinit var agentOrchestrator: AgentOrchestrator private set
    lateinit var voiceManager: VoiceManager private set
    lateinit var browserAgentController: BrowserAgentController private set
    lateinit var performanceRecorder: RoomPerformanceRecorder private set
    lateinit var browserStore: BrowserStore private set
    lateinit var applicationScope: CoroutineScope private set
    lateinit var integrationRegistry: IntegrationRegistry private set
    lateinit var semanticMemoryStore: ai.jarvis.app.domain.memory.SemanticMemoryStore private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize security, Gmail authorization, and database
        securePreferences = SecurePreferences(this)
        val backendApi = BackendApiClient(BuildConfig.API_BASE_URL)
        backendAuthManager = BackendAuthManager(backendApi, securePreferences)
        userAccountManager = UserAccountManager(backendApi, securePreferences)
        ai.jarvis.app.service.background.TaskRunGate.init(this)
        mailSourceManager = MailSourceManager(securePreferences, backendAuthManager)
        // Wake the hosted backend early so the first Gmail action is not slow.
        applicationScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        applicationScope.launch {
            backendApi.warmUp()
            backendAuthManager.refreshConnectionState()
        }
        database = AppDatabase.getInstance(this)
        memoryRepository = MemoryRepositoryImpl(database.memoryDao())
        // Hybrid semantic memory (Req.5): Room offline-first, Mem0 merged when key is set.
        val localSemantic = RoomSemanticMemoryStore(database.memoryDao())
        val remoteSemantic = Mem0SemanticMemoryStore(
            apiKeyProvider = securePreferences::getMem0ApiKey,
            userIdProvider = securePreferences::getMem0UserId
        )
        semanticMemoryStore = HybridSemanticMemoryStore(localSemantic, remoteSemantic)
        taskSchedulerRepository = TaskSchedulerRepositoryImpl(
            this,
            database.scheduledTaskDao(),
            database.taskExecutionDao()
        )
        performanceRecorder = RoomPerformanceRecorder(database.performanceDao())

        // 2. Initialize browser automation sessions
        val webScraper = ControlledWebScraper()
        browserAgentController = BrowserAgentController()
        browserStore = BrowserStore(this)

        // 3. Register Agent Tools
        mailService = MailGateway(
            real = BackendMailService(backendAuthManager),
            demo = DemoMailService(store = PrefsDemoMailStore(this)),
            demoEnabled = securePreferences::isDemoMode
        )
        val emailTool = EmailTool(mailService) { securePreferences.getDefaultEmail() }
        val gmailTools = listOf(
            GmailSearchTool(mailService),
            GmailReadTool(mailService),
            GmailDraftTool(mailService) { securePreferences.getDefaultEmail() }
        )
        val webTool = WebAutomationTool(webScraper)
        val browserTools = listOf(
            BrowserAgentTool(browserAgentController, "open_url", "Open a public HTTPS URL in the controlled in-app browser."),
            BrowserAgentTool(browserAgentController, "search_webpage", "Search the current page using its search box, or search the web if no page is open."),
            BrowserAgentTool(browserAgentController, "read_page", "Read visible text from the current browser page."),
            BrowserAgentTool(browserAgentController, "click_element", "Click a page element by CSS selector after explicit user approval."),
            BrowserAgentTool(browserAgentController, "enter_text", "Enter text into a page field after explicit user approval. Does not submit the form."),
            BrowserAgentTool(browserAgentController, "scroll_page", "Scroll the current page up or down."),
            BrowserAgentTool(browserAgentController, "navigate_back", "Navigate to the previous page in browser history."),
            BrowserAgentTool(browserAgentController, "navigate_forward", "Navigate to the next page in browser history."),
            BrowserAgentTool(browserAgentController, "extract_links", "List the links (text and URL) on the current page so you can choose where to go next."),
            BrowserAgentTool(browserAgentController, "get_browser_state", "Return the current page title, URL, loading state, and a short text preview.")
        )
        val schedulerTool = SchedulerTool(taskSchedulerRepository)
        val memoryTool = MemoryTool(memoryRepository, semanticMemoryStore)

        // Integrations platform (Req.7/8): built-ins here, custom MCP/OpenAPI discovered dynamically.
        // AgentOrchestrator only sees ToolRegistry — no hardcoding of custom integrations.
        integrationRegistry = IntegrationRegistryImpl(
            builtInTools = gmailTools + listOf(emailTool, webTool, schedulerTool, memoryTool) + browserTools,
            store = CustomIntegrationStore(this)
        )
        // Notion reads its token live from the connected catalog entry, so revoking
        // the token in Notion cuts access immediately with no app update.
        val notionTool = NotionTool(tokenProvider = {
            kotlinx.coroutines.runBlocking {
                runCatching { integrationRegistry.catalogToken("notion") }.getOrDefault("")
            }
        })

        val toolRegistry = ToolRegistry(
            gmailTools + listOf(emailTool, webTool, schedulerTool, memoryTool, notionTool) + browserTools
        )

        // 4. Initialize LLM client and Orchestrator
        val geminiClient = ProviderAgentClient(securePreferences)
        agentOrchestrator = AgentOrchestrator(
            geminiClient,
            toolRegistry,
            memoryRepository,
            JsonConversationStore(java.io.File(filesDir, "jarvis-chat-history.json")),
            performanceRecorder,
            conversationLibrary = FileConversationLibrary(java.io.File(filesDir, "jarvis-chats")),
            semanticMemoryStore = semanticMemoryStore
        )

        // 5. Initialize Voice Manager
        voiceManager = VoiceManager(this)

        // 5b. Make sure every enabled recurring task has its next run queued (also migrates old periodic jobs).
        applicationScope.launch {
            runCatching { taskSchedulerRepository.ensureScheduled() }
        }

        // 6. Setup Notification Channels
        createNotificationChannels()
    }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { applicationScope.cancel() }
        runCatching { voiceManager.shutdown() }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jarvis_agent_tasks",
                "Jarvis Background Tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for recurring and scheduled agent tasks"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
