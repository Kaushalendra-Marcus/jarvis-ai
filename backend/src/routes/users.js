import { Router } from "express";
import { rateLimit } from "express-rate-limit";
import { prisma } from "../database/prisma.js";
import { env } from "../config/env.js";
import { requireDeviceSession } from "../middleware/device-auth.js";
import { hashPassword, verifyPassword } from "../services/password.js";
import { createOpaqueToken, hashOpaqueToken } from "../services/token-vault.js";
import { userCredentialsSchema } from "../utils/validation.js";

const router = Router();

// Password guessing gets a much tighter budget than the global 120/min limiter.
const authLimiter = rateLimit({
  windowMs: 15 * 60_000,
  limit: 30,
  standardHeaders: "draft-8",
  legacyHeaders: false
});

// Test seam: handlers prefer an injected prisma (req.app.locals.prisma) so route
// tests can run without a database; production always uses the singleton.
const dbOf = req => req.app.locals.prisma ?? prisma;

async function createUserSession(db, userId) {
  const accessToken = createOpaqueToken();
  const expiresAt = new Date(Date.now() + env.accessTokenTtlDays * 86_400_000);
  await db.deviceSession.create({
    data: { userId, tokenHash: hashOpaqueToken(accessToken), expiresAt }
  });
  return { accessToken, expiresAt: expiresAt.toISOString() };
}

const sessionPayload = (user, session) => ({
  email: user.email,
  accessToken: session.accessToken,
  expiresAt: session.expiresAt
});

router.post("/signup", authLimiter, async (req, res, next) => {
  try {
    const input = userCredentialsSchema.parse(req.body);
    const db = dbOf(req);
    const existing = await db.user.findUnique({ where: { email: input.email } });
    if (existing) {
      return res.status(409).json({ error: "An account with this email already exists. Log in instead." });
    }
    const user = await db.user.create({
      data: { email: input.email, passwordHash: await hashPassword(input.password) }
    });
    // If the device held a guest (Google-only) session, retire it: one identity per device.
    const oldToken = /^Bearer ([A-Za-z0-9_-]{40,})$/.exec(req.get("authorization") ?? "")?.[1];
    if (oldToken) {
      await db.deviceSession.updateMany({
        where: { tokenHash: hashOpaqueToken(oldToken), revokedAt: null },
        data: { revokedAt: new Date() }
      });
    }
    const session = await createUserSession(db, user.id);
    res.set("Cache-Control", "no-store").status(201).json(sessionPayload(user, session));
  } catch (error) {
    next(error);
  }
});

router.post("/login", authLimiter, async (req, res, next) => {
  try {
    const input = userCredentialsSchema.parse(req.body);
    const db = dbOf(req);
    const user = await db.user.findUnique({ where: { email: input.email } });
    // Same message for unknown email and wrong password: no account enumeration.
    if (!user || !(await verifyPassword(input.password, user.passwordHash))) {
      return res.status(401).json({ error: "Invalid email or password." });
    }
    const session = await createUserSession(db, user.id);
    res.set("Cache-Control", "no-store").json(sessionPayload(user, session));
  } catch (error) {
    next(error);
  }
});

/** App-level identity behind the current device session (null when signed out). */
router.get("/me", requireDeviceSession, async (req, res) => {
  res.set("Cache-Control", "no-store").json({
    user: req.userEmail ? { email: req.userEmail } : null,
    gmail: req.accountEmail ? { email: req.accountEmail } : null
  });
});

/** Log out this device: revokes the current session, keeps server-side data intact. */
router.post("/logout", requireDeviceSession, async (req, res, next) => {
  try {
    const db = dbOf(req);
    await db.deviceSession.update({
      where: { id: req.deviceSession.id },
      data: { revokedAt: new Date() }
    });
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});

export default router;
