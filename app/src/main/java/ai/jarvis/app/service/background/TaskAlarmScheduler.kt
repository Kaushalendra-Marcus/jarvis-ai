package ai.jarvis.app.service.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Exact on-time trigger for scheduled routines.
 *
 * WorkManager chains are reliable but inexact under Doze: a 9:00 AM routine can
 * slip. This sets an exact alarm for the same occurrence as a second trigger;
 * [TaskRunGate] guarantees only one of the two paths executes. When exact alarms
 * are unavailable (permission revoked on Android 12+), the WorkManager chain
 * remains as the fallback.
 */
object TaskAlarmScheduler {
    const val ACTION_TASK_ALARM = "ai.jarvis.app.action.TASK_ALARM"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_RUN_TOKEN = "extra_run_token"

    fun scheduleExact(context: Context, taskId: String, runEpochMillis: Long, runToken: Long) {
        if (runEpochMillis <= System.currentTimeMillis() + 5_000L) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) return
        runCatching {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                runEpochMillis,
                alarmIntent(context, taskId, runToken)
            )
        }
    }

    fun cancel(context: Context, taskId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val cancelIntent = baseIntent(context, taskId)
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode(taskId),
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    private fun baseIntent(context: Context, taskId: String): Intent =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_TASK_ALARM
            data = Uri.parse("jarvis://task-alarm/$taskId")
        }

    private fun alarmIntent(context: Context, taskId: String, runToken: Long): PendingIntent {
        val intent = baseIntent(context, taskId).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_RUN_TOKEN, runToken)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun requestCode(taskId: String): Int = taskId.hashCode()
}
