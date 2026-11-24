package ai.jarvis.app.service.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import ai.jarvis.app.data.local.AppDatabase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires exact routine alarms and re-registers them after reboot.
 *
 * Alarm path: enqueues an immediate worker run with KEEP semantics on the same
 * unique work name as the scheduled chain, so a still-pending chain run is never
 * duplicated; [TaskRunGate] settles the rare same-moment race.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    rescheduleAll(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent?.action != TaskAlarmScheduler.ACTION_TASK_ALARM) return
        val taskId = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_ID) ?: return
        val runToken = intent.getLongExtra(TaskAlarmScheduler.EXTRA_RUN_TOKEN, 0L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                triggerNow(context, taskId, runToken)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun triggerNow(context: Context, taskId: String, runToken: Long) {
        val dao = AppDatabase.getInstance(context).scheduledTaskDao()
        val task = dao.getTaskById(taskId)?.toDomain() ?: return
        if (!task.isEnabled) return
        val spec = TaskSchedulingPolicy.forScheduledTask(task.id, 0L, task.isRecurring)
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(
                workDataOf(
                    ScheduledTaskWorker.KEY_TASK_ID to task.id,
                    ScheduledTaskWorker.KEY_PROMPT_ACTION to task.promptAction,
                    ScheduledTaskWorker.KEY_TASK_TITLE to task.title,
                    ScheduledTaskWorker.KEY_RUN_TOKEN to runToken
                )
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(
                        if (spec.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                    )
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoffSeconds, TimeUnit.SECONDS)
            .addTag("task_${task.id}")
            .build()
        // KEEP: a still-pending chain run wins; the alarm only fills gaps.
        // Same-moment races are settled by TaskRunGate inside the worker.
        WorkManager.getInstance(context).enqueueUniqueWork(
            spec.uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val tasks = AppDatabase.getInstance(context).scheduledTaskDao().getAllTasks().first()
        for (entity in tasks) {
            val task = entity.toDomain()
            if (!task.isEnabled || task.nextRunEpoch <= now) continue
            TaskRunGate.expect(task.id, task.nextRunEpoch)
            TaskAlarmScheduler.scheduleExact(context, task.id, task.nextRunEpoch, task.nextRunEpoch)
        }
    }
}
