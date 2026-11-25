# JARVIS AI Performance (measured 25 Nov 2026)

## Automated suites (certified today)
- Android: **112 tests, 0 failures, 0 errors** (20 suites,
  `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --offline`)
  - Covers `TimeUtils` (incl. 12-hour clock), `CronSchedule`, `AgentOrchestrator` (incl.
    confirmation gate + autonomous sensitive-block), `AgentReliability`, `GeminiAgentClient`
    (fallback chain, 429/network/401 classification), Gmail/Memory/Scheduler/Notion tools,
    `MailGateway` routing + demo idempotency, `TaskExecutionPolicy`, `TaskRunGate`
    (single-execution), `EmailTaskContext`, browser URL/scraper, metrics math, Markdown.
- Backend: **22 tests, 0 failures** (`npm test`, ~2s)
  - `gmail-inbox` (MIME parsing, header-injection rejection, error mapping incl. scope vs
    admin denials), `shared-account` guardrails, `token-vault` (AES-256-GCM round-trip),
    `users` (signup/duplicate/login/wrong-password/logout/OAuth-link/single-use-code/
    sign-out-everywhere, all executed against HTTP + fake DB), `validation`, `worker` wiring.
- Compile: `:app:compileDebugKotlin --offline` **BUILD SUCCESSFUL**.

## Runtime telemetry (from real use, not benchmarks)
`RoomPerformanceRecorder` → `performance_samples` (Settings → Performance) tracks
`AGENT_TURN` (end-to-end), `LLM_CALL` (Gemini), `MEMORY_RETRIEVAL`, `SCHEDULED_RUN`
(median/p95/success-rate, newest 1000 samples). Backend contributes pg-boss attempt counts
and 429-only retries; Render logs carry Gmail denial codes for scope diagnosis.

## Optimizations landed
1. Exact `AlarmManager` triggers + WorkManager fallback with run-token gate, routines fire
   on time through Doze with zero double-runs (measured by `TaskRunGateTest`, 6 cases).
2. Room-first hybrid memory: semantic cloud recall never blocks the hot path; blank Mem0
   key means fully offline mode.
3. `applicationScope(SupervisorJob + IO)` instead of `GlobalScope`; cancelled on terminate.
4. `TaskExecutionDao.insertAndTrim` in one transaction, history bounded at 500 rows.
5. pg-boss `batchSize:1 + singletonKey + $transaction` claim, safe delivery; scale by sharding.
6. Timezone-aware 12-hour formatters refresh the device zone per call (fixed a test-env bug
   where static formatters froze the zone at class-load).
7. Auth endpoints rate-limited (30/15min) separately from the global 120/min limiter.

## Next opportunity (not yet done)
Agent-turn latency is dominated by sequential LLM→tool round trips (up to 8 steps). The
reliable win: stream the first response token to the UI while tool calls resolve, cutting
perceived latency without touching the execution semantics.
