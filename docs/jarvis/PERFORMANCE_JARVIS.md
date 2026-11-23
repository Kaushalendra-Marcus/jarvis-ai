# JARVIS AI — Performance (measured 22 Nov 2026)

## Real results
- Android: **99 unit tests, 0 failures** (`./gradlew :app:testDebugUnitTest --offline`, 23s)
  - Covers `CronSchedule, AgentOrchestrator, GeminiAgentClient, Memory/Scheduler/Gmail tools, TaskExecutionPolicy, ControlledWebScraper, MarkdownParser`
- Backend: **18 tests, 0 fail** (`npm test`, 625ms)
  - `gmail-inbox, shared-account, token-vault (AES-256-GCM 49ms), validation (Zod), worker queue`
- Compile: `:app:compileDebugKotlin --offline` **BUILD SUCCESSFUL 33s**

## Runtime telemetry
`RoomPerformanceRecorder` → `performance_samples` tracks `AGENT_TURN, LLM_CALL, MEMORY_RETRIEVAL, tool(name)`.

## Optimizations landed (Req.10/12)
1. `GlobalScope` → `applicationScope(SupervisorJob+IO)` + `onTerminate cancel` — no leaks
2. `TaskExecutionDao.insertAndTrim @Transaction` — atomic history, bounded 500 rows
3. Backend `pg-boss batchSize:1 + singletonKey + $transaction claim` — safe, scale via sharding
4. Mem0 hybrid: Room-first (offline-safe), remote merge only when key set — no latency on hot path
5. Voice: en-IN/hi-IN locale preference, `MAX_RESULTS 3` for Hinglish alternates
