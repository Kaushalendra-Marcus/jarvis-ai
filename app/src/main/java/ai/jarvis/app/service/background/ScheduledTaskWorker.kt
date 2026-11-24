package ai.jarvis.app.service.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.jarvis.app.JarvisApplication
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.core.util.NetworkMonitor
import ai.jarvis.app.domain.metrics.PerformanceRecorder
import ai.jarvis.app.domain.model.ScheduledTaskStatus
import ai.jarvis.app.domain.orchestrator.AutonomousTaskResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes one scheduled agent task. A run is only reported as successful when the
 * agent returned a final answer; failed or retrying runs are persisted as such.
 */
class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val promptAction = inputData.getString(KEY_PROMPT_ACTION) ?: return Result.failure()
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Scheduled Agent Task"

        val app = applicationContext as? JarvisApplication ?: return Result.failure()
        val schedulerRepo = app.taskSchedulerRepository
        val task = schedulerRepo.getTaskById(taskId) ?: return Result.failure()

        // A paused task's periodic job may still fire once if it was already queued.
        // Manual "Run now" requests are always honoured.
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (!task.isEnabled && task.isRecurring && !isManual) {
            return Result.success()
        }

        // A manual trigger and a periodic run can target the same task. Ignore the
        // duplicate attempt instead of running the workflow twice.
        if (!runningTaskIds.add(taskId)) {
            return Result.success()
        }

        // The exact alarm and the WorkManager chain race for the same occurrence:
        // only the first to claim the run token executes.
        val runToken = inputData.getLong(KEY_RUN_TOKEN, 0L)
        if (!TaskRunGate.claim(taskId, runToken)) {
            return Result.success()
        }

        val startedAt = System.currentTimeMillis()
        try {
            val emailContext = EmailTaskContextFactory.create(
                promptAction = promptAction,
                gmailConnected = app.mailService.isConnected(),
                accountEmail = ""
            )
            val outcome = when {
                emailContext?.skipMessage != null ->
                    AutonomousTaskResult.Skipped(emailContext.skipMessage ?: "Email task skipped.")

                !NetworkMonitor.isOnline(applicationContext) ->
                    AutonomousTaskResult.Failure(
                        message = "No internet connection. The task will retry when a network is available.",
                        retryable = true
                    )

                else -> app.agentOrchestrator.executeAutonomousTask(
                    promptAction = promptAction,
                    additionalContext = emailContext?.additionalInstructions
                )
            }
            return persistAndScheduleNext(taskId, taskTitle, task.isRecurring, outcome, startedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Never swallow an unexpected failure: persist it and let WorkManager retry.
            return persistAndScheduleNext(
                taskId = taskId,
                taskTitle = taskTitle,
                isRecurring = task.isRecurring,
                outcome = AutonomousTaskResult.Failure(
                    message = "Unexpected execution error (${error.javaClass.simpleName}). " +
                        "Check the task instruction and try again.",
                    retryable = true
                ),
                startedAt = startedAt
            )
        } finally {
            runningTaskIds.remove(taskId)
        }
    }

    private suspend fun persistAndScheduleNext(
        taskId: String,
        taskTitle: String,
        isRecurring: Boolean,
        outcome: AutonomousTaskResult,
        startedAt: Long
    ): Result {
        val app = applicationContext as JarvisApplication
        val schedulerRepo = app.taskSchedulerRepository
        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring, runAttemptCount)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)
        val finishedAt = System.currentTimeMillis()

        schedulerRepo.updateTaskStatus(
            id = taskId,
            lastExecutionEpoch = finishedAt,
            status = status,
            completed = disposition != WorkDisposition.RETRY
        )
        // Every attempt (including retries) is kept in the history timeline.
        schedulerRepo.recordExecution(
            taskId = taskId,
            taskTitle = taskTitle,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = ScheduledTaskStatus.stateOf(status).name,
            message = ScheduledTaskStatus.messageOf(status),
            attempt = runAttemptCount + 1
        )
        app.performanceRecorder.record(
            PerformanceRecorder.SCHEDULED_RUN,
            finishedAt - startedAt,
            outcome is AutonomousTaskResult.Success
        )

        // A finished (not retrying) scheduled run queues the next occurrence from the cron expression.
        // Manual "Run now" runs never move the schedule.
        if (disposition != WorkDisposition.RETRY && isRecurring && !inputData.getBoolean(KEY_MANUAL, false)) {
            runCatching { schedulerRepo.scheduleNextRun(taskId) }
        }

        return when (disposition) {
            WorkDisposition.SUCCESS -> {
                showNotification(taskTitle, TaskExecutionPolicy.notificationMessage(outcome))
                Result.success()
            }
            WorkDisposition.RETRY -> {
                // Release the claim so the backoff retry can reclaim this occurrence.
                TaskRunGate.release(taskId, inputData.getLong(KEY_RUN_TOKEN, 0L))
                Result.retry()
            }
            WorkDisposition.FAILURE -> {
                showNotification(taskTitle, TaskExecutionPolicy.notificationMessage(outcome))
                Result.failure()
            }
        }
    }

    private fun prefs() = applicationContext.getSharedPreferences(
        SecurePreferences.PREFS_SETTINGS_FILE, android.content.Context.MODE_PRIVATE
    )

    private fun notificationsEnabled(): Boolean =
        prefs().getBoolean(SecurePreferences.KEY_TASK_NOTIFICATIONS, true)

    private fun ensureAlarmChannel(): String {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundOn = prefs().getBoolean(SecurePreferences.KEY_TASK_ALARM_SOUND, true)
        val vibrateOn = prefs().getBoolean(SecurePreferences.KEY_TASK_VIBRATE, true)
        val signature = "sound=$soundOn;vibrate=$vibrateOn"
        if (prefs().getString(KEY_APPLIED_CHANNEL_CONFIG, "") != signature) {
            notificationManager.deleteNotificationChannel(ALARM_CHANNEL_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = if (soundOn) {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                } else null
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Jarvis Routine Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible alarms when scheduled routines finish"
                    enableVibration(vibrateOn)
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                    setSound(
                        soundUri,
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setBypassDnd(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
            prefs().edit().putString(KEY_APPLIED_CHANNEL_CONFIG, signature).apply()
        }
        return ALARM_CHANNEL_ID
    }

    private fun showNotification(title: String, message: String) {
        if (!notificationsEnabled()) return
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ensureAlarmChannel()

        val openAppIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % 10000).toInt(),
            openAppIntent,
            flags
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    companion object {
        const val KEY_TASK_ID = "key_task_id"
        const val KEY_PROMPT_ACTION = "key_prompt_action"
        const val KEY_TASK_TITLE = "key_task_title"
        const val KEY_MANUAL = "key_manual"
        const val KEY_RUN_TOKEN = "key_run_token"
        private const val ALARM_CHANNEL_ID = "jarvis_task_alarms"
        private const val KEY_APPLIED_CHANNEL_CONFIG = "alarm_channel_config"

        private val runningTaskIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
