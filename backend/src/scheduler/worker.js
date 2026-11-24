import { EMAIL_QUEUE } from "../services/email-queue.js";

export async function ensureEmailQueue(boss) {
  await boss.createQueue(EMAIL_QUEUE);
}

export async function startEmailWorker(boss, operations = {}) {
  await ensureEmailQueue(boss);
  const reconcileUnqueuedEmails = operations.reconcileUnqueuedEmails ??
    (await import("../services/email-service.js")).reconcileUnqueuedEmails;
  // Serverless Postgres (Neon) cold-starts the compute on first contact; the
  // first queries can fail while it wakes. Retry briefly so boot survives that.
  let attempt = 0;
  for (;;) {
    try {
      await reconcileUnqueuedEmails(boss);
      break;
    } catch (error) {
      if (++attempt >= 3) throw error;
      await new Promise(resolve => setTimeout(resolve, 5000));
    }
  }
  await boss.work(EMAIL_QUEUE, { batchSize: 1 }, async jobs => {
    const processEmailJob = operations.processEmailJob ??
      (await import("../services/email-service.js")).processEmailJob;
    for (const job of jobs) {
      await processEmailJob(boss, job);
    }
  });
}
