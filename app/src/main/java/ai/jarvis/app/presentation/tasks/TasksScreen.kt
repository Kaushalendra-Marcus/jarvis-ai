package ai.jarvis.app.presentation.tasks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.jarvis.app.core.util.TimeUtils
import ai.jarvis.app.domain.model.ExecutionLogFormatter
import ai.jarvis.app.domain.model.ScheduledTask
import ai.jarvis.app.presentation.components.MarkdownText
import androidx.compose.ui.unit.Dp
import ai.jarvis.app.domain.model.ScheduledTaskStatus
import ai.jarvis.app.domain.model.TaskExecutionRecord
import ai.jarvis.app.domain.model.TaskExecutionState
import androidx.compose.runtime.saveable.rememberSaveable
import ai.jarvis.app.presentation.theme.*

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var taskToEdit by remember { mutableStateOf<ScheduledTask?>(null) }

    val activeCount = tasks.count { it.isEnabled }
    val runCount = history.size
    val doneCount = tasks.count {
        ScheduledTaskStatus.stateOf(it.lastExecutionStatus) == TaskExecutionState.SUCCESS
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Routines", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Set once — runs on its own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatPill(
                icon = Icons.Default.Schedule,
                text = "$activeCount active",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                icon = Icons.Default.CheckCircle,
                text = "$doneCount done",
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Tasks (${tasks.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("History ($runCount)") }
            )
        }

        if (selectedTab == 1) {
            TaskHistoryList(history)
        } else if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No routines yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "On the Home tab, try:\n“Every morning at 8, brief me.”",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks, key = { it.id }) { task ->
                    ScheduledTaskCard(
                        task = task,
                        onEdit = { taskToEdit = task },
                        onCancel = { viewModel.cancelTask(task.id) },
                        onToggleEnabled = { enabled -> viewModel.setTaskEnabled(task.id, enabled) },
                        onTriggerNow = {
                            viewModel.triggerTaskNow(task.id) { result ->
                                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }

    taskToEdit?.let { task ->
        EditScheduledTaskDialog(
            task = task,
            onDismiss = { taskToEdit = null },
            onSave = { updated -> viewModel.updateTask(updated); taskToEdit = null }
        )
    }
}

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun EditScheduledTaskDialog(
    task: ScheduledTask,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var action by remember(task.id) { mutableStateOf(task.promptAction) }
    // 12-hour clock in the UI; stored as 24-hour hourOfDay on save.
    var hour12 by remember(task.id) {
        mutableStateOf((task.hourOfDay % 12).let { if (it == 0) "12" else it.toString() })
    }
    var isPm by remember(task.id) { mutableStateOf(task.hourOfDay >= 12) }
    var minute by remember(task.id) { mutableStateOf(task.minute.toString()) }
    fun hourOfDay24(): Int? {
        val h = hour12.toIntOrNull() ?: return null
        if (h !in 1..12) return null
        return when {
            h == 12 && !isPm -> 0
            h == 12 -> 12
            isPm -> h + 12
            else -> h
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit routine", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text("Instruction") },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour12,
                        onValueChange = { hour12 = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (1-12)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPm,
                        onClick = { isPm = false },
                        label = { Text("AM") }
                    )
                    FilterChip(
                        selected = isPm,
                        onClick = { isPm = true },
                        label = { Text("PM") }
                    )
                }
            }
        },
        confirmButton = {
            val h24 = hourOfDay24()
            val m = minute.toIntOrNull()
            Button(
                onClick = {
                    onSave(task.copy(title = title.trim(), promptAction = action.trim(), hourOfDay = h24!!, minute = m!!))
                },
                enabled = title.isNotBlank() && action.isNotBlank() && h24 != null && m != null && m in 0..59
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScheduledTaskCard(
    task: ScheduledTask,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTriggerNow: () -> Unit
) {
    val isPaused = task.isRecurring && !task.isEnabled
    val executionState = ScheduledTaskStatus.stateOf(task.lastExecutionStatus)
    val isFailed = executionState == TaskExecutionState.FAILED
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (task.isRecurring) Icons.Default.Repeat else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (task.isRecurring) {
                            ai.jarvis.app.domain.schedule.CronSchedule.parse(task.cronExpression)?.describe()
                                ?: "Daily ${TimeUtils.formatHourMinute(task.hourOfDay, task.minute)}"
                        } else "One-time",
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = task.promptAction,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (executionState != TaskExecutionState.NONE) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusLine(executionState)
                    Spacer(modifier = Modifier.weight(1f))
                    task.lastExecutionEpoch?.let { epoch ->
                        Text(
                            text = "Last run · ${TimeUtils.formatDateTime(epoch)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            var showDetails by remember(task.id) { mutableStateOf(false) }
            if (!task.lastExecutionStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = if (isFailed) ErrorRed else primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (showDetails) "Hide result" else "Result",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFailed) ErrorRed else primary
                    )
                }
                if (showDetails) {
                    ExecutionLogContent(
                        state = executionState,
                        message = ScheduledTaskStatus.messageOf(task.lastExecutionStatus)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isPaused -> "Paused"
                            isFailed -> "Last run failed"
                            task.isEnabled -> "Next · ${TimeUtils.formatDateTime(task.nextRunEpoch)}"
                            executionState == TaskExecutionState.SUCCESS && task.lastExecutionEpoch != null ->
                                "Done · ${TimeUtils.formatDateTime(task.lastExecutionEpoch)}"
                            else -> "Done"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (task.isRecurring) {
                    IconButton(onClick = { onToggleEnabled(isPaused) }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) SuccessGreen else WarningOrange
                        )
                    }
                }

                Button(
                    onClick = onTriggerNow,
                    colors = if (isFailed) ButtonDefaults.buttonColors(containerColor = ErrorRed) else ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (isFailed) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isFailed) "Retry" else "Run now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

@Composable
private fun StatusLine(state: TaskExecutionState) {
    val (label, color) = when (state) {
        TaskExecutionState.SUCCESS -> "Success" to SuccessGreen
        TaskExecutionState.FAILED -> "Failed" to ErrorRed
        TaskExecutionState.RETRYING -> "Retrying" to WarningOrange
        TaskExecutionState.SKIPPED -> "Skipped" to MaterialTheme.colorScheme.onSurfaceVariant
        TaskExecutionState.NONE -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Readable execution history timeline across all scheduled tasks (FR-13). */
@Composable
private fun TaskHistoryList(history: List<TaskExecutionRecord>) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "No runs yet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(history, key = { it.id }) { record ->
            var expanded by remember(record.id) { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        record.taskTitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusLine(record.state)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    relativeTime(record.startedAt) + " · took " + formatDuration(record.durationMs) +
                        if (record.attempt > 1) " · try ${record.attempt}" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.message.isNotBlank() || record.state != TaskExecutionState.SUCCESS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExecutionLogContent(
                        state = record.state,
                        message = record.message,
                        collapsedLines = if (expanded) Int.MAX_VALUE else 4
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "${ms} ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)


/**
 * Human-readable result of one run: a headline, a plain explanation (Markdown for
 * successful summaries), a suggested next step, and the raw technical text on demand.
 */
@Composable
private fun ExecutionLogContent(
    state: TaskExecutionState,
    message: String,
    collapsedLines: Int = Int.MAX_VALUE
) {
    val log = remember(state, message) { ExecutionLogFormatter.format(state, message) }
    val isProblem = state == TaskExecutionState.FAILED || state == TaskExecutionState.RETRYING
    val accent = when (state) {
        TaskExecutionState.FAILED -> ErrorRed
        TaskExecutionState.RETRYING -> WarningOrange
        TaskExecutionState.SUCCESS -> SuccessGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val primary = MaterialTheme.colorScheme.primary
    var showTechnical by remember(message) { mutableStateOf(false) }

    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                log.headline,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            if (state == TaskExecutionState.SUCCESS) {
                Box(modifier = Modifier.heightIn(max = if (collapsedLines == Int.MAX_VALUE) Dp.Unspecified else 96.dp)) {
                    MarkdownText(markdown = log.body)
                }
            } else {
                Text(log.body, style = MaterialTheme.typography.bodyMedium)
            }
            log.hint?.let {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isProblem && log.technical != null) {
                Text(
                    if (showTechnical) "Hide technical details" else "Technical details",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showTechnical = !showTechnical }
                )
                if (showTechnical) {
                    Text(
                        log.technical,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "Today, 11:17", "Yesterday, 09:00" or a short date, so history reads naturally. */
private fun relativeTime(epochMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val time = TimeUtils.formatTime(epochMillis)
    val sameDay = { a: java.util.Calendar, b: java.util.Calendar ->
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    }
    return when {
        sameDay(now, then) -> "Today, $time"
        sameDay(now.apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }, then) -> "Yesterday, $time"
        else -> TimeUtils.formatDateTime(epochMillis)
    }
}
