package ai.lumo.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.lumo.app.core.security.SecurePreferences
import ai.lumo.app.presentation.theme.SuccessGreen
import ai.lumo.app.presentation.theme.WarningOrange

/**
 * The mailbox chooser. Two options: your own Gmail, or the offline demo mailbox.
 */
@Composable
fun MailboxChooser(
    source: String,
    connectionState: String,
    onSelect: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onOpenDemoMailbox: () -> Unit
) {
    val connected = connectionState.startsWith("Connected as ")
    val trouble = connectionState.startsWith("Could not reach") || connectionState.contains("expired", ignoreCase = true)

    // 1. Own Gmail
    MailOption(
        selected = source == SecurePreferences.SOURCE_OWN,
        title = "My Gmail",
        badge = null,
        description = "Your real inbox. Sign in with Google.",
        onClick = { onSelect(SecurePreferences.SOURCE_OWN) }
    ) {
        Steps(
            listOf(
                "Tap “Sign in with Google” and approve Gmail access.",
                "If Google blocks sign-in, use the demo mailbox instead."
            )
        )
        SignInRow(connected, trouble, connectionState, onSignIn, onSignOut, onRetry)
    }

    // 2. Demo mailbox
    MailOption(
        selected = source == SecurePreferences.SOURCE_DEMO,
        title = "Demo mailbox",
        badge = "Offline · no sign-in",
        description = "Sample emails on this phone. Nothing real is ever sent.",
        onClick = { onSelect(SecurePreferences.SOURCE_DEMO) }
    ) {
        Text(
            "Try “Summarize my emails”, then watch Inbox, Sent and Drafts change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpenDemoMailbox, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View demo mailbox")
        }
    }
}

@Composable
private fun MailOption(
    selected: Boolean,
    title: String,
    badge: String?,
    description: String,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected, onClick = onClick)
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignInRow(
    connected: Boolean,
    trouble: Boolean,
    status: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = when {
                connected -> SuccessGreen
                trouble -> WarningOrange
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(10.dp)
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (connected) status else if (trouble) status else "Not signed in",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (connected) {
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        } else {
            Button(onClick = onSignIn) { Text("Sign in with Google") }
            if (trouble) OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
