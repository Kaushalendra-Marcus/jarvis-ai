-- User accounts: app-level email/password identity, independent of Gmail OAuth.
-- DeviceSession becomes polymorphic: a session carries a user, a Google account, or both.

CREATE TABLE "User" (
    "id" UUID NOT NULL,
    "email" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

ALTER TABLE "GoogleAccount" ADD COLUMN "userId" UUID;

ALTER TABLE "DeviceSession" ALTER COLUMN "accountId" DROP NOT NULL;
ALTER TABLE "DeviceSession" ADD COLUMN "userId" UUID;

ALTER TABLE "GoogleAccount" ADD CONSTRAINT "GoogleAccount_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "DeviceSession" ADD CONSTRAINT "DeviceSession_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

CREATE INDEX "DeviceSession_userId_expiresAt_idx" ON "DeviceSession"("userId", "expiresAt");
