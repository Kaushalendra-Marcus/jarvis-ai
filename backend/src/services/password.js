import { randomBytes, scrypt as scryptCallback, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";

const scrypt = promisify(scryptCallback);
const SALT_BYTES = 16;
const KEY_BYTES = 64;
// scrypt parameters: memory-hard enough to slow brute force, light enough for a free-tier host.
const SCRYPT_OPTIONS = { N: 16384, r: 8, p: 1, maxmem: 32 * 1024 * 1024 };

/** scrypt password hash in modular format: "scrypt$salt$hash" (base64url). */
export async function hashPassword(password) {
  if (typeof password !== "string" || password.length < 8 || password.length > 128) {
    throw Object.assign(new Error("Password must be between 8 and 128 characters."), { status: 400 });
  }
  const salt = randomBytes(SALT_BYTES).toString("base64url");
  const derived = await scrypt(password, salt, KEY_BYTES, SCRYPT_OPTIONS);
  return `scrypt$${salt}$${derived.toString("base64url")}`;
}

/** Constant-time password check. Returns false (never throws) for malformed hashes. */
export async function verifyPassword(password, stored) {
  try {
    if (typeof password !== "string" || typeof stored !== "string") return false;
    const [version, salt, expected] = stored.split("$");
    if (version !== "scrypt" || !salt || !expected) return false;
    const derived = await scrypt(password, salt, KEY_BYTES, SCRYPT_OPTIONS);
    const actual = Buffer.from(derived.toString("base64url"), "utf8");
    const wanted = Buffer.from(expected, "utf8");
    return actual.length === wanted.length && timingSafeEqual(actual, wanted);
  } catch {
    return false;
  }
}
