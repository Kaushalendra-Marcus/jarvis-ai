import { prisma } from "../database/prisma.js";
import { hashOpaqueToken } from "../services/token-vault.js";

export async function requireDeviceSession(req, res, next) {
  try {
    const authorization = req.get("authorization") ?? "";
    const match = /^Bearer ([A-Za-z0-9_-]{40,})$/.exec(authorization);
    if (!match) return res.status(401).json({ error: "Authentication required." });

    const session = await (req.app?.locals?.prisma ?? prisma).deviceSession.findUnique({
      where: { tokenHash: hashOpaqueToken(match[1]) },
      include: {
        account: { select: { id: true, email: true, disconnectedAt: true } },
        user: { select: { id: true, email: true } }
      }
    });
    if (!session || session.revokedAt || session.expiresAt <= new Date()) {
      return res.status(401).json({ error: "The app session is invalid or expired. Log in again." });
    }
    if (session.account?.disconnectedAt) {
      return res.status(401).json({ error: "The app session is invalid or expired. Reconnect Gmail." });
    }
    req.deviceSession = session;
    // A session carries an app user, a Google account, or both (linked at OAuth time).
    req.accountId = session.accountId ?? null;
    req.accountEmail = session.account?.email ?? null;
    req.userId = session.userId ?? null;
    req.userEmail = session.user?.email ?? null;
    next();
  } catch (error) {
    next(error);
  }
}

/** Gmail routes only: the session must also carry a connected Google account. */
export function requireGoogleAccount(req, res, next) {
  if (!req.accountId) {
    return res.status(401).json({ error: "Gmail is not connected. Sign in with Google first." });
  }
  next();
}
