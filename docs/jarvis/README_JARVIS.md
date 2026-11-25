# JARVIS AI — Just A Rather Very Intelligent System

Premium personal AI agent for Android + Node.js backend. Voice/text → reasoning → tools → completion.

- Full submission doc: [`README.md`](../../README.md) (setup, architecture, performance,
  completed/incomplete features, challenges, AI-tool disclosure).
- Architecture detail: [`ARCHITECTURE_JARVIS.md`](ARCHITECTURE_JARVIS.md).
- Performance detail: [`PERFORMANCE_JARVIS.md`](PERFORMANCE_JARVIS.md).

## Branding
- Name: **JARVIS AI**, package `ai.jarvis.app`.
- Theme: midnight `#0E1015`, lime `#C8F04A`, teal `#0F766E`. Vector icons only.
- Launcher: midnight field + lime spark.

## Quick start
### Android
1. `cp local.properties.example local.properties` — set `sdk.dir` (optional `gemini.api.key`;
   no key ships in the APK, and Settings → AI model accepts one anytime).
2. `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (~19 MB).
3. `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest` — **112 tests, 0 failures**
   (certified 25 Nov 2026).

### Backend
1. `cd backend && cp .env.example .env` — fill `DATABASE_URL`, `SESSION_SECRET`,
   `TOKEN_ENCRYPTION_KEY_B64`, `GOOGLE_CLIENT_ID/SECRET`, redirect URIs.
2. `docker compose up -d postgres && npx prisma migrate deploy`
   (production boots self-apply the accounts DDL; no CLI needed on the host).
3. `npm test` — **22 tests, 0 failures** (certified 25 Nov 2026). Run: `npm run dev`.

## First run (phone)
1. Onboarding → **My Gmail** or **Demo mailbox** (offline samples, nothing real sent).
2. Settings → Account: create an account or log in.
3. Settings → Mailbox → **Sign in with Google**, approve **all** scope checkboxes.
   Testing-mode OAuth: accounts must be listed under Audience → Test users, and the
   Gmail API must be enabled in the Cloud project.

## What works (Aurviz mapping)
- Voice/text (SpeechRecognizer + TTS), NLU + function tools (Gemini + fallbacks/Groq/Claude).
- Memory: Room offline + Mem0 semantic hybrid; view/edit/delete in Memory tab.
- Gmail OAuth end-to-end with approval-gated send/draft, Links status sync, live mailbox.
- Controlled browser (10 tools), cron + exact-alarm routines, multi-step ReAct tasks.
- App accounts (signup/login/logout/sign-out-everywhere), 12-hour clock, X-style settings,
  routine alarms with sound/vibration toggles, chat history drawer.

## Incomplete / future
- Google Calendar: `UNVERIFIED` placeholder, needs the Calendar OAuth scope.
- Custom MCP/OpenAPI: registered but fail-closed until schema validation lands.
- Google OAuth Testing mode (100 test users) — production needs restricted-scope verification.
- School/work Workspace accounts with admin-blocked Gmail API cannot connect by design;
  the app detects this (`GMAIL_BLOCKED`) and suggests a personal account.
