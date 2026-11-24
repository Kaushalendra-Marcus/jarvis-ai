package ai.jarvis.app.presentation.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.jarvis.app.core.util.TimeUtils
import ai.jarvis.app.R
import ai.jarvis.app.domain.model.Memory
import ai.jarvis.app.presentation.theme.*

private val SECTION_ORDER = listOf("preference", "work", "personal", "general")

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val smartRecall by viewModel.mem0Available.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<Memory?>(null) }
    var memoryToDelete by remember { mutableStateOf<Memory?>(null) }

    val sections = remember(memories) {
        memories.groupBy { it.category.lowercase().ifBlank { "general" } }
            .toSortedMap(compareBy { SECTION_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })
            .toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Teach Jarvis", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Tastes, names, rules — applied automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            Spacer(Modifier.height(6.dp))

            if (memories.isEmpty() && searchQuery.isBlank()) {
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
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nothing saved yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Say “remember my manager is Alex”\nor tap Teach Jarvis.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (sections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "No matches.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 128.dp, top = 6.dp)
                ) {
                    item(key = "about") {
                        AboutYouCard(
                            memories = memories,
                            smartRecall = smartRecall
                        )
                    }
                    sections.forEach { (category, group) ->
                        item(key = "header-$category") {
                            SectionHeader(category = category, count = group.size)
                        }
                        items(group, key = { it.id }) { memory ->
                            MemoryRow(
                                memory = memory,
                                onEdit = { memoryToEdit = memory },
                                onDelete = { memoryToDelete = memory }
                            )
                        }
                    }
                }
            }
        }

        if (showEditor || memoryToEdit != null) {
            MemoryEditorSheet(
                initial = memoryToEdit,
                onDismiss = { showEditor = false; memoryToEdit = null },
                onSave = { key, content, category ->
                    if (memoryToEdit != null) viewModel.updateMemory(memoryToEdit!!, key, content, category)
                    else viewModel.addManualMemory(key, content, category)
                    showEditor = false
                    memoryToEdit = null
                }
            )
        }

        memoryToDelete?.let { memory ->
            AlertDialog(
                onDismissRequest = { memoryToDelete = null },
                title = { Text("Forget this?", fontWeight = FontWeight.Bold) },
                text = { Text("“${memory.key}” goes away for good.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMemory(memory.id)
                            memoryToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) { Text("Forget") }
                },
                dismissButton = {
                    TextButton(onClick = { memoryToDelete = null }) { Text("Keep") }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(category: String, count: Int) {
    val (color, icon) = getCategoryStyling(category)
    val customRes = categoryDrawable(category)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (customRes != null) {
                Icon(
                    painterResource(customRes),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            category.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemoryRow(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (color, _) = getCategoryStyling(memory.category)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = memory.key,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(17.dp))
                }
            }
            Text(
                text = memory.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 16.dp, end = 6.dp)
            )
            Text(
                text = TimeUtils.formatDateTime(memory.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditorSheet(
    initial: Memory?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var key by remember { mutableStateOf(initial?.key.orEmpty()) }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var category by remember {
        mutableStateOf(initial?.category?.lowercase()?.takeIf { it in SECTION_ORDER } ?: "preference")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                if (initial == null) "Teach Jarvis" else "Edit memory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Jarvis applies this on its own next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SECTION_ORDER) { option ->
                    val selected = category == option
                    val (color, _) = getCategoryStyling(option)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) color else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable { category = option }
                    ) {
                        Text(
                            option.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Title — e.g. Manager") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Fact — e.g. Alex, alex@co.com") },
                shape = RoundedCornerShape(14.dp),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(key.trim(), content.trim(), category) },
                enabled = key.isNotBlank() && content.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (initial == null) "Save" else "Update") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Custom drawable per category, from design/icons. Null falls back to the vector icon. */
private fun categoryDrawable(category: String): Int? = when (category.lowercase()) {
    "general" -> R.drawable.ic_custom_general
    else -> null
}

private fun getCategoryStyling(category: String): Pair<Color, ImageVector> {
    return when (category.lowercase()) {
        "work" -> Pair(CategoryWork, Icons.Default.Work)
        "preference", "preferences" -> Pair(CategoryPreference, Icons.Default.Star)
        "personal" -> Pair(CategoryPersonal, Icons.Default.Person)
        else -> Pair(CategoryGeneral, Icons.Default.Psychology)
    }
}

/**
 * "About you" — the visible face of the memory layer. Counts what Jarvis has
 * learned, quotes the personalizing facts back, and states whether smart
 * recall (Mem0) is shaping answers beyond this phone.
 */
@Composable
private fun AboutYouCard(
    memories: List<ai.jarvis.app.domain.model.Memory>,
    smartRecall: Boolean
) {
    val areas = memories.map { it.category.lowercase() }.toSet().size
    val prefs = memories.filter { it.category.equals("preference", ignoreCase = true) }.take(2)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(R.drawable.ic_custom_about_you),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("About you", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (smartRecall) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.14f)
                ) {
                    Text(
                        if (smartRecall) "Smart recall on" else "On this phone",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (smartRecall) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                if (memories.isEmpty()) "Nothing learned yet — teach Jarvis below."
                else "Jarvis knows ${memories.size} ${if (memories.size == 1) "thing" else "things"} across $areas ${if (areas == 1) "area" else "areas"} — applied to every answer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            prefs.forEach {
                Text(
                    "• ${it.key}: ${it.content}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (!smartRecall) {
                Text(
                    "Smart recall is off — managed by your backend.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Behavior and facts recalled per question — even beyond keywords.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
