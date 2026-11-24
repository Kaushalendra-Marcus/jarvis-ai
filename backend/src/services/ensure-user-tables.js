/**
 * Applies the user-accounts DDL idempotently at boot.
 *
 * The deployed backend starts with `node src/index.js` (no prisma CLI in the
 * production image), so `prisma migrate deploy` cannot run there. This runs the
 * same statements as prisma/migrations/20261124000000_user_accounts/migration.sql
 * guarded by IF NOT EXISTS checks, making first boot after deploy self-migrating
 * and every later boot a no-op. Local development still uses `prisma migrate dev`.
 */
export async function ensureUserTables(pool) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query(`
      CREATE TABLE IF NOT EXISTS "User" (
        "id" UUID NOT NULL,
        "email" TEXT NOT NULL,
        "passwordHash" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updatedAt" TIMESTAMP(3) NOT NULL,
        CONSTRAINT "User_pkey" PRIMARY KEY ("id")
      )`);
    await client.query(`CREATE UNIQUE INDEX IF NOT EXISTS "User_email_key" ON "User"("email")`);
    await client.query(`ALTER TABLE "GoogleAccount" ADD COLUMN IF NOT EXISTS "userId" UUID`);
    await client.query(`ALTER TABLE "DeviceSession" ALTER COLUMN "accountId" DROP NOT NULL`);
    await client.query(`ALTER TABLE "DeviceSession" ADD COLUMN IF NOT EXISTS "userId" UUID`);
    await client.query(`
      DO $$ BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'GoogleAccount_userId_fkey') THEN
          ALTER TABLE "GoogleAccount" ADD CONSTRAINT "GoogleAccount_userId_fkey"
            FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;
        END IF;
      END $$`);
    await client.query(`
      DO $$ BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'DeviceSession_userId_fkey') THEN
          ALTER TABLE "DeviceSession" ADD CONSTRAINT "DeviceSession_userId_fkey"
            FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
      END $$`);
    await client.query(
      `CREATE INDEX IF NOT EXISTS "DeviceSession_userId_expiresAt_idx" ON "DeviceSession"("userId", "expiresAt")`
    );
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}
