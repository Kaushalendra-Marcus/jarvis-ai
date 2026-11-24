import test from "node:test";
import assert from "node:assert/strict";

process.env.DATABASE_URL = "postgresql://unused:unused@localhost:5432/unused";
process.env.SESSION_SECRET = "a-local-session-secret-with-at-least-32-characters";
process.env.TOKEN_ENCRYPTION_KEY_B64 = Buffer.alloc(32, 11).toString("base64");
process.env.GOOGLE_CLIENT_ID = "test-client";
process.env.GOOGLE_CLIENT_SECRET = "test-secret";
process.env.GOOGLE_REDIRECT_URI = "http://localhost:3000/auth/google/callback";
process.env.ANDROID_REDIRECT_URI = "jarvis://oauth/callback";
process.env.PUBLIC_BASE_URL = "http://localhost:3000";
process.env.ACCESS_TOKEN_TTL_DAYS = "30";

const { hashPassword, verifyPassword } = await import("../src/services/password.js");
const { default: usersRouter } = await import("../src/routes/users.js");
const { default: authRouter } = await import("../src/routes/auth.js");
const { default: express } = await import("express");
const { hashOpaqueToken } = await import("../src/services/token-vault.js");

test("password hashing round-trips and rejects wrong input", async () => {
  const hash = await hashPassword("correct-horse-42");
  assert.match(hash, /^scrypt\$.+\$.+$/);
  assert.equal(await verifyPassword("correct-horse-42", hash), true);
  assert.equal(await verifyPassword("wrong-password", hash), false);
  assert.equal(await verifyPassword("correct-horse-42", "garbage"), false);
  assert.equal(await verifyPassword("correct-horse-42", null), false);
  await assert.rejects(() => hashPassword("short"), error => error.status === 400);
});

// In-memory prisma stand-in: implements exactly the methods the user/auth routes use.
function makeFakePrisma() {
  const state = {
    users: new Map(),
    sessions: new Map(), // tokenHash -> session
    codes: new Map(), // codeHash -> app auth code
    googleLinks: []
  };
  let nextId = 1;
  const id = () => `id-${nextId++}`;
  return {
    state,
    user: {
      findUnique: async ({ where }) => state.users.get(where.email) ?? null,
      create: async ({ data }) => {
        const user = { id: id(), email: data.email, passwordHash: data.passwordHash };
        state.users.set(data.email, user);
        return user;
      }
    },
    deviceSession: {
      create: async ({ data }) => {
        const session = { id: id(), revokedAt: null, accountId: null, userId: null, ...data };
        state.sessions.set(data.tokenHash, session);
        return session;
      },
      findUnique: async ({ where }) => {
        const session = state.sessions.get(where.tokenHash) ?? null;
        if (!session) return null;
        const user = [...state.users.values()].find(u => u.id === session.userId) ?? null;
        return {
          ...session,
          user: user ? { id: user.id, email: user.email } : null,
          account: session.accountId
            ? { id: session.accountId, email: session.accountEmail ?? null, disconnectedAt: null }
            : null
        };
      },
      update: async ({ where, data }) => {
        const session = [...state.sessions.values()].find(s => s.id === where.id);
        Object.assign(session, data);
        return session;
      },
      updateMany: async ({ where, data }) => {
        let count = 0;
        for (const session of state.sessions.values()) {
          if (where.tokenHash && session.tokenHash !== where.tokenHash) continue;
          if (where.userId && session.userId !== where.userId) continue;
          if (where.revokedAt === null && session.revokedAt !== null) continue;
          Object.assign(session, data);
          count += 1;
        }
        return { count };
      }
    },
    appAuthCode: {
      findUnique: async ({ where }) => state.codes.get(where.codeHash) ?? null,
      updateMany: async ({ where, data }) => {
        const code = [...state.codes.values()].find(c => c.id === where.id);
        if (!code || code.usedAt !== null) return { count: 0 };
        Object.assign(code, data);
        return { count: 1 };
      }
    },
    googleAccount: {
      update: async ({ where, data }) => {
        state.googleLinks.push({ where, data });
        return { id: where.id, ...data };
      }
    }
  };
}

async function startTestServer(fake) {
  const app = express();
  app.locals.prisma = fake;
  app.use(express.json());
  app.use("/api/v1/users", usersRouter);
  app.use("/api/v1/auth", authRouter);
  // Minimal copy of the production error mapper.
  // eslint-disable-next-line no-unused-vars
  app.use((error, _req, res, _next) => {
    if (error?.name === "ZodError") return res.status(400).json({ error: "Request validation failed." });
    const status = Number.isInteger(error?.status) ? error.status : 500;
    res.status(status).json({ error: status >= 500 ? "Server error." : error.message });
  });
  const server = await new Promise(resolve => {
    const s = app.listen(0, "127.0.0.1", () => resolve(s));
  });
  return { server, base: `http://127.0.0.1:${server.address().port}` };
}

const post = (base, path, body, token) => fetch(`${base}${path}`, {
  method: "POST",
  headers: {
    "content-type": "application/json",
    ...(token ? { authorization: `Bearer ${token}` } : {})
  },
  body: JSON.stringify(body)
});

test("signup creates a user session, duplicate is rejected, login/logout work", async t => {
  const fake = makeFakePrisma();
  const { server, base } = await startTestServer(fake);
  t.after(() => server.close());

  const signup = await post(base, "/api/v1/users/signup", { email: "Test@Example.com", password: "s3cure-pass" });
  assert.equal(signup.status, 201);
  const created = await signup.json();
  assert.equal(created.email, "test@example.com");
  assert.match(created.accessToken, /^[A-Za-z0-9_-]{40,}$/);

  const duplicate = await post(base, "/api/v1/users/signup", { email: "test@example.com", password: "s3cure-pass" });
  assert.equal(duplicate.status, 409);

  const badPassword = await post(base, "/api/v1/users/signup", { email: "other@example.com", password: "short" });
  assert.equal(badPassword.status, 400);

  const login = await post(base, "/api/v1/users/login", { email: "test@example.com", password: "s3cure-pass" });
  assert.equal(login.status, 200);
  const session = await login.json();
  assert.equal(session.email, "test@example.com");

  const wrongPassword = await post(base, "/api/v1/users/login", { email: "test@example.com", password: "nope-nope-nope" });
  assert.equal(wrongPassword.status, 401);
  const unknown = await post(base, "/api/v1/users/login", { email: "ghost@example.com", password: "s3cure-pass" });
  assert.equal(unknown.status, 401);
  // Same message for both: no account enumeration.
  assert.equal((await wrongPassword.json()).error, (await unknown.json()).error);

  const me = await fetch(`${base}/api/v1/users/me`, {
    headers: { authorization: `Bearer ${session.accessToken}` }
  });
  assert.equal(me.status, 200);
  assert.deepEqual(await me.json(), { user: { email: "test@example.com" }, gmail: null });

  const logout = await fetch(`${base}/api/v1/users/logout`, {
    method: "POST",
    headers: { authorization: `Bearer ${session.accessToken}` }
  });
  assert.equal(logout.status, 204);

  const afterLogout = await fetch(`${base}/api/v1/users/me`, {
    headers: { authorization: `Bearer ${session.accessToken}` }
  });
  assert.equal(afterLogout.status, 401);
});

test("oauth exchange with a user linkToken keeps one session and links gmail", async t => {
  const fake = makeFakePrisma();
  const { server, base } = await startTestServer(fake);
  t.after(() => server.close());

  const signup = await post(base, "/api/v1/users/signup", { email: "link@example.com", password: "s3cure-pass" });
  const { accessToken: userToken } = await signup.json();

  const googleCode = "g".repeat(40);
  const { state } = fake;
  const accountId = "account-1";
  // Seed the one-time app auth code the OAuth callback would have created.
  const appCode = {
    id: "code-1",
    codeHash: hashOpaqueToken(googleCode),
    accountId,
    expiresAt: new Date(Date.now() + 300_000),
    usedAt: null
  };
  state.codes.set(appCode.codeHash, appCode);

  const exchange = await post(base, "/api/v1/auth/exchange", { code: googleCode, linkToken: userToken });
  assert.equal(exchange.status, 200);
  const linked = await exchange.json();
  // Same device token back: one identity, not a swapped Google-only session.
  assert.equal(linked.accessToken, userToken);
  const linkedSession = [...state.sessions.values()].find(s => s.tokenHash === hashOpaqueToken(userToken));
  assert.equal(linkedSession.accountId, accountId);
  assert.deepEqual(state.googleLinks, [{ where: { id: accountId }, data: { userId: linkedSession.userId } }]);
  assert.ok(linkedSession.userId);

  // Single-use code is consumed: replay is rejected.
  const replay = await post(base, "/api/v1/auth/exchange", { code: googleCode });
  assert.equal(replay.status, 401);
});

test("sign out everywhere revokes every session of the user", async t => {
  const fake = makeFakePrisma();
  const { server, base } = await startTestServer(fake);
  t.after(() => server.close());

  const first = await post(base, "/api/v1/users/signup", { email: "multi@example.com", password: "s3cure-pass" });
  const token1 = (await first.json()).accessToken;
  const second = await post(base, "/api/v1/users/login", { email: "multi@example.com", password: "s3cure-pass" });
  const token2 = (await second.json()).accessToken;

  const me = token => fetch(`${base}/api/v1/users/me`, {
    headers: { authorization: `Bearer ${token}` }
  });
  assert.equal((await me(token1)).status, 200);
  assert.equal((await me(token2)).status, 200);

  const signOutAll = await fetch(`${base}/api/v1/users/sessions`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${token1}` }
  });
  assert.equal(signOutAll.status, 204);
  assert.equal((await me(token1)).status, 401);
  assert.equal((await me(token2)).status, 401);

  // Guest (Google-only, no app user) sessions are rejected with the login prompt.
  const guestToken = "z".repeat(43);
  fake.state.sessions.set(hashOpaqueToken(guestToken), {
    id: "guest-session",
    tokenHash: hashOpaqueToken(guestToken),
    accountId: "account-9",
    accountEmail: "guest@gmail.com",
    userId: null,
    revokedAt: null,
    expiresAt: new Date(Date.now() + 300_000)
  });
  const guest = await fetch(`${base}/api/v1/users/sessions`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${guestToken}` }
  });
  assert.equal(guest.status, 401);
  assert.match((await guest.json()).error, /Log in with your Jarvis account/);
});
