# JARVIS AI Flow diagrams (paste into Eraser)

How to use: copy ONE mermaid block below → open your Eraser workspace →
new diagram → paste the code into Eraser's AI chat (or https://www.eraser.io/ai/mermaid-diagram-editor)
→ Generate. Eraser redraws it as a native, editable diagram.

## 1. System architecture + agent loop (flowchart)

```mermaid
flowchart LR
  User(["User: voice / text"]) --> UI["Compose UI"]
  UI --> ORCH["AgentOrchestrator: ReAct loop, max 8 steps"]
  ORCH --> MEM["Recall: Room offline memory + Mem0 semantic memory"]
  ORCH --> LLM["Plan: Gemini 3.5-flash-lite<br/>fallbacks: Groq, DeepSeek, Claude"]
  LLM -->|functionCall| TOOLS["ToolRegistry"]
  TOOLS --> GMAIL_T["Gmail tools: search, read, draft, send"]
  TOOLS --> MEM_T["Memory + scheduler tools"]
  TOOLS --> WEB_T["Web + 10 controlled-browser tools"]
  TOOLS --> EXT["Notion + custom MCP/OpenAPI"]
  ORCH --> GATE{"Sensitive action?"}
  GATE -->|yes: send, click, type| APPROVE["Approval dialog: shows full content"]
  APPROVE -->|approved| EXEC["executeConfirmed"]
  APPROVE -->|rejected| STOP["Cancelled, model told not to retry"]
  GATE -->|no| EXEC
  EXEC --> OBS["Observe result"]
  OBS --> ORCH
  ORCH --> REPLY["Answer + tool cards + history"]
  REPLY --> User

  subgraph Android["Android app - Kotlin - ai.jarvis.app"]
    UI
    ORCH
    LLM
    TOOLS
    MEM
  end

  GMAIL_T --> API["Backend API on Render"]
  API --> DB[("Postgres: users, device sessions,<br/>AES-256-GCM Gmail tokens")]
  API --> QUEUE["pg-boss queue + email worker"]
  QUEUE --> GMAIL_API["Gmail API: list, get, drafts.create, send"]
  API --> OAUTH["Google OAuth: consent, code exchange"]

  subgraph Backend["Backend - Node.js"]
    API
    DB
    QUEUE
  end

  subgraph Google["Google Cloud"]
    OAUTH
    GMAIL_API
  end
```

## 2. Gmail connect + send flow (sequence)

```mermaid
sequenceDiagram
  participant U as User (app)
  participant A as Android app
  participant B as Backend (Render)
  participant G as Google

  Note over U,G: Connect account
  U->>A: Settings → Mailbox → Sign in with Google
  A->>B: GET /auth/google
  B->>G: OAuth consent (openid, email, gmail.send, readonly, compose)
  G->>U: Approve scopes
  G->>B: code → /auth/google/callback
  B->>B: Exchange code, AES-encrypt refresh token, store
  B->>A: jarvis://oauth/callback?code=one-time-code
  A->>B: POST /api/v1/auth/exchange (+linkToken if logged in)
  B->>A: Device session token (SHA-256 stored)
  A->>U: Connected as you@gmail.com

  Note over U,G: Agent sends email (natural language)
  U->>A: Send mail to rahul@example.com about Project Update
  A->>A: Orchestrator → Gemini functionCall send_email
  A->>A: Preflight: show recipient, subject, body
  U->>A: Approve (Send)
  A->>B: POST /api/v1/emails/send + Idempotency-Key
  B->>B: scheduleEmail: dedupe by key, enqueue pg-boss
  B->>G: gmail.users.messages.send (stable Message-ID)
  G->>B: SENT
  B->>A: 202 accepted → queued for sending
  A->>U: Tool card SUCCESS + summary
```

## 3. Routine scheduling + alarm flow (sequence)

```mermaid
sequenceDiagram
  participant U as User
  participant A as Android app
  participant AL as AlarmManager (exact)
  participant W as WorkManager
  participant WR as ScheduledTaskWorker

  U->>A: Every morning at 8, brief me
  A->>A: CronSchedule.parse → next run epoch + token
  A->>W: One-time chain (fallback path)
  A->>AL: setExactAndAllowWhileIdle (on-time path)
  AL->>W: Alarm fires → enqueue KEEP (fills gaps only)
  W->>WR: Run with token
  WR->>WR: TaskRunGate.claim: winner runs, loser exits
  WR->>A: Agent executes (reads + drafts allowed, send blocked)
  WR->>U: HIGH-channel alarm: sound + vibration + summary
  WR->>A: scheduleNextRun (failure → retry with backoff)
```
