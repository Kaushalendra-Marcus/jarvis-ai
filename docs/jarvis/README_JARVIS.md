# JARVIS AI — Just A Rather Very Intelligent System

Premium personal AI agent for Android + Node.js backend. Voice/text → reasoning → tools → completion.

## Branding
- Name: **JARVIS AI**, package kept as `ai.jarvis.app` to avoid migration (Req.1)
- Theme: midnight `#0E1015`, lime `#C8F04A`, teal `#0F766E`. No purple, no emoji, vector icons only.
- Launcher: midnight field + lime spark (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`)

## Quick start
### Android
1. `cp local.properties.example local.properties` — paste `gemini.api.key` (no key ships in APK, Req.9)
2. `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
3. `./gradlew :app:testDebugUnitTest` — 17 unit tests

### Backend
1. `cd backend && cp .env.example .env` — fill `DATABASE_URL, GOOGLE_* , TOKEN_ENCRYPTION_KEY_B64`
2. `docker compose up -d postgres && npx prisma migrate deploy`
3. `npm test` — 5 suites, `npm run dev` (`ROLE=all`)

## What works (Aurviz mapping)
- Voice/text (SpeechRecognizer + TTS en-IN/hi-IN), NLU + 16 function tools (Gemini 3.5-flash-lite + fallbacks)
- Memory: Room offline + Mem0 semantic (`HybridSemanticMemoryStore`), view/edit/delete in Memory tab
- Gmail OAuth end-to-end (`/auth/google → /exchange → device session`), connect/disconnect in Integrations
- Controlled browser (`ControlledBrowserScreen`), recurring cron (`CronSchedule` + WorkManager + pg-boss)
- Multi-step ReAct loop (8 interactive / 6 background) with live `RUNNING → SUCCESS/FAILED/AWAITING_CONFIRMATION` status
- Confirmations for send/click/type, idempotency keys, AES-256-GCM tokens

## Incomplete / future
- Google Calendar: `UNVERIFIED` placeholder, needs Calendar OAuth scope
- Custom MCP/OpenAPI: registry + blocked-by-default tools land, full schema fetch pending
- Mem0: Room-first, Mem0 merge when `mem0.api.key` set in Settings (offline-safe)

## Performance (measured)
- Backend `npm test`: Zod + token-vault + worker suites < 2s locally
- Android: `RoomPerformanceRecorder` tracks `LLM_CALL, AGENT_TURN, MEMORY_RETRIEVAL` in `performance_samples`
- Optimization: `insertAndTrim` @Transaction, `batchSize:1` worker (safe, increase with sharding for scale)
