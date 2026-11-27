# Jarvis AI - Interview Questions (Project Based)

30 questions from easy to hard, each with a short hint drawn from the actual code.

## Easy (1-10)

**1. What does Jarvis do, in one minute?**
Hint: Personal AI agent for Android. You talk or type, it plans with Gemini, uses tools (Gmail, memory, web, routines), asks before sensitive actions, and remembers you across restarts.

**2. What is the tech stack?**
Hint: Kotlin + Jetpack Compose (app), Gemini function calling (reasoning), Node.js + Express + Prisma (backend), Postgres + pg-boss (queue), Room (on-device DB).

**3. What is a ReAct loop?**
Hint: Reason + Act repeated: understand intent, recall memory, plan, call a tool, read the result, repeat until done. Ours runs up to 8 interactive steps, 6 in background.

**4. How does voice input work?**
Hint: Android SpeechRecognizer for speech to text, TTS for spoken replies, plus sound effects. Voice messages are flagged so the agent knows they were spoken.

**5. Where is user memory stored?**
Hint: Room database on the phone is the source of truth, Mem0 cloud recall merges in only when a key is set. Blank key means fully offline mode.

**6. What does the backend actually do?**
Hint: Three jobs: Google OAuth exchange, Gmail API proxy (search, read, send), and a durable email queue with history. Chat, memory and routines live on the phone.

**7. What is pg-boss and why use it?**
Hint: A Postgres backed job queue. Scheduled emails must send exactly once even if the server restarts, so jobs are claimed transactionally with singleton keys.

**8. Where do API keys and tokens live?**
Hint: Never in git or the APK. On device in Keystore backed encrypted prefs, on server Gmail refresh tokens are AES-256-GCM encrypted and sessions stored as SHA-256 hashes.

**9. What is the demo mailbox for?**
Hint: Built in sample inbox so reviewers can try every flow without connecting a real account. Nothing real is ever sent from it, and it is clearly labeled.

**10. How are scheduled routines triggered?**
Hint: Two triggers for reliability: an exact system alarm for on-time delivery plus a WorkManager chain as backup, with a run token gate so only one ever executes.

## Medium (11-20)

**11. Walk me through one agent turn end to end.**
Hint: User prompt -> retrieve relevant memories (Room + Mem0) -> system instruction -> Gemini plans with function calls -> permission check -> tool runs -> result observed -> continue or report. Tool calls stream live as RUNNING to SUCCESS/FAILED.

**12. How does Gemini function calling connect to your tools?**
Hint: Each tool (AgentTool) declares a name, description and schema. The registry exposes them to the model, Gemini returns a functionCall, the orchestrator executes it and feeds the result back for the next step.

**13. What happens before a sensitive action runs?**
Hint: It pauses into AWAITING_CONFIRMATION, the UI shows the full content (recipient, subject, body / click target), and nothing executes until the user taps approve.

**14. How does hybrid recall merge Room and Mem0?**
Hint: Both are queried, Mem0 semantic hits and Room keyword hits are merged, deduped by id plus content, and capped. Room recent memories are always included so offline context never disappears.

**15. How do routines accept both cron and plain English?**
Hint: CronSchedule parses cron expressions and phrases like daily/weekly at a clock time into one model with next-run math, plus a describe() for human readable display.

**16. Why both exact alarms and WorkManager, and what is the run token gate?**
Hint: Doze mode delays WorkManager, so exact alarms guarantee timing while WorkManager guarantees eventual execution. Whichever fires first claims a one shot run token; the loser stands down, so a routine never double runs.

**17. Explain the Gmail OAuth flow. What does the app actually hold?**
Hint: Browser opens the backend /auth/google, exchange happens server side, the app receives only an opaque device session token. The refresh token never touches the phone.

**18. What are idempotency keys for in email sends?**
Hint: Retries and double taps must not send twice. A stable key per email makes re-submits return the same record, and the queue claim is transactional.

**19. How does the hourly memory consolidation worker work?**
Hint: A periodic worker reads user prompts since its last run, extracts durable facts with pattern rules (name, email, preferences, remember that), and saves them through the hybrid store. Capped per run, deduped against stored keys, silent when there is nothing new.

**20. How do you handle Gemini rate limits or model failures?**
Hint: Multi model fallback chain plus extra providers (Groq, DeepSeek, Claude) selectable in Settings, with retry classification so only retryable errors (rate limits, network) are retried.

## Hard (21-30)

**21. How did you make morning routines survive Doze mode?**
Hint: Exact AlarmManager triggers fire through Doze, WorkManager chain covers missed windows, TaskRunGate dedupes, and 6 gate tests prove single execution. Verified scheduling path, not just hope.

**22. Why Room-first instead of backend-first memory? Trade-offs?**
Hint: Instant offline answers, zero server PII, no sync conflicts. Cost: no multi device memory and Mem0 is best effort. For an assessment and a personal device app, latency and privacy beat cross device sync.

**23. How is one user's memory isolated from another's?**
Hint: Room rows are per install, Mem0 calls carry a per user id, encrypted prefs are per user keys. A second login cannot recall the first user's facts through any code path.

**24. MCP tools bring their own tokens. How did you design auth so revoke works instantly?**
Hint: The registry reads each token live from its connected catalog entry instead of caching it, unregistered tools stay fail closed, and only https endpoints are allowed.

**25. What is your threat model?**
Hint: Stolen phone (encrypted prefs), leaked DB (encrypted refresh tokens, hashed sessions), replayed sends (idempotency + single use OAuth codes), and untrusted content: emails and web pages are data, never instructions, and sends/clicks always need a tap.

**26. The email worker uses batchSize 1. How would you scale to thousands of users?**
Hint: Shard by account id with more workers, keep the singleton key per email, move Gmail calls to a rate limited pool, add dead letter retries with backoff. Correctness first, throughput second.

**27. Agent turns are sequential LLM round trips. How would you cut perceived latency?**
Hint: Stream first tokens while tools resolve, parallelize independent tool calls in one step, cache recall per turn, warm up the backend on app start (already done), and shrink the prompt with top-k memory.

**28. How would you evaluate whether the agent is actually good?**
Hint: Beyond the 112 + 22 unit tests: task success rate on fixed scenarios (draft saved, routine fired), recall precision on planted memories, confirmation precision (never auto sends), and on-device latency medians already recorded in Settings, Performance.

**29. What breaks first at 10k users, backend and app?**
Hint: Backend: single pg-boss worker and Gmail per user rate limits. App: Room history tables (bounded at 500 rows already) and chat JSON files growing. Fix with worker sharding, quotas per account, and pagination.

**30. If you had one more week, what would you build and why?**
Hint: Calendar integration first (biggest missing user value, same OAuth pattern as Gmail), then streaming replies (biggest perceived speed win), then the parked hourly backend backup only if multi device becomes a requirement.
