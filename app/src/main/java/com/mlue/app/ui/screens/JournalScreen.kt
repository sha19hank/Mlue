package com.mlue.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import com.mlue.app.ui.theme.AppMotion
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.mlue.app.data.JournalEntryEntity
import com.mlue.app.viewmodel.HabitViewModel
import java.time.LocalDate
import java.time.LocalTime

fun generateTitle(body: String, time: LocalTime): String {
    val firstSentence = body.split(Regex("[.!?\n]")).firstOrNull { it.isNotBlank() }?.trim()
    if (firstSentence != null) {
        return if (firstSentence.length > 30) firstSentence.take(27) + "..." else firstSentence
    }
    return when (time.hour) {
        in 5..11 -> "Morning Reflection"
        in 12..16 -> "Afternoon Thoughts"
        in 17..21 -> "Evening Reflection"
        else -> "Late Night Thoughts"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JournalScreen(
    viewModel: HabitViewModel,
    openDialogRequest: Boolean,
    onDialogRequestConsumed: () -> Unit,
    onEditorStateChange: (Boolean) -> Unit = {}
) {
    val entries by viewModel.journalEntries.collectAsState()
    val hintJournalShown by viewModel.hintJournalShown.collectAsState()

    // Use remember (NOT rememberSaveable) so that editor state never leaks
    // across config changes with a stale entry reference.
    var isSheetOpen by remember { mutableStateOf(false) }
    // Snapshot the selected entry at open time — isolates state from live list
    var editingEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<JournalEntryEntity?>(null) }

    LaunchedEffect(openDialogRequest) {
        if (openDialogRequest) {
            editingEntry = null
            isSheetOpen = true
            onDialogRequestConsumed()
        }
    }

    // Report editor open/closed state to parent so the global FAB can hide when editing.
    // LaunchedEffect ensures the callback fires after each state change, not during composition.
    LaunchedEffect(isSheetOpen) {
        onEditorStateChange(isSheetOpen)
    }

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val groupedEntries = remember(entries, today) {
        entries.groupBy { entry ->
            when (entry.date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> "Older"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Journal",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    )
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Small reflections help reveal patterns over time.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    editingEntry = null
                    isSheetOpen = true
                }) {
                    Text("Write your first entry")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // 104dp bottom padding: dock(68) + dock-bottom-margin(20) + breathing room(16)
                contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hintJournalShown == false) {
                    item(key = "hintJournal") {
                        com.mlue.app.ui.components.HintChip(
                            text = "Reflection adds context to your routines.",
                            onDismiss = { viewModel.dismissHintJournal() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                val keys = listOf("Today", "Yesterday", "Older").filter { groupedEntries.containsKey(it) }
                keys.forEach { sectionKey ->
                    item(key = "header_$sectionKey") {
                        Text(
                            text = sectionKey,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(groupedEntries[sectionKey] ?: emptyList(), key = { it.id }) { entry ->
                        JournalCard(
                            entry = entry,
                            onClick = {
                                // Snapshot: copy entry data at click time to prevent live reference mutation
                                editingEntry = entry.copy()
                                isSheetOpen = true
                            },
                            onDeleteRequest = { entryToDelete = entry.copy() }
                        )
                    }
                }
            }
        }
    }

    // Editor — keyed on the editing entry's ID so state is fully reset per entry
    // Transition: fade + slide together for a layered-paper feel
    AnimatedVisibility(
        visible = isSheetOpen,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = AppMotion.panelSlideSpring()
        ) + fadeIn(animationSpec = AppMotion.floatTween(AppMotion.durationLong)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = AppMotion.panelSlideSpring()
        ) + fadeOut(animationSpec = AppMotion.floatTween(AppMotion.exitDuration))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // key() forces full recomposition + state reset when editingEntry changes
            key(editingEntry?.id) {
                JournalEditor(
                    initialEntry = editingEntry,
                    onSave = { title, body, date, mood, color ->
                        val finalTitle = title.ifBlank { generateTitle(body, LocalTime.now()) }
                        val current = editingEntry
                        if (current != null) {
                            viewModel.updateJournalEntry(
                                current.copy(
                                    title = finalTitle,
                                    body = body,
                                    date = date,
                                    mood = mood,
                                    color = color
                                )
                            )
                        } else {
                            viewModel.upsertJournalEntry(finalTitle, body, date, mood, color)
                        }
                        isSheetOpen = false
                    },
                    onCancel = { isSheetOpen = false }
                )
            }
        }
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteJournalEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun JournalCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = AppMotion.pressBounce(),
        label = "scale_${entry.id}"
    )
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val moodEmoji = when (entry.mood) {
        "RAD" -> "😁"
        "GOOD" -> "🙂"
        "MEH" -> "😐"
        "BAD" -> "🙁"
        "AWFUL" -> "😢"
        else -> null
    }

    // Adaptive journal card tint — calm wash, not habit-card saturation
    // Warm colors (red/orange/amber): 0.10 alpha; cool/light colors: 0.13 alpha
    val cardColor = if (entry.color != 0) {
        val base = Color(entry.color)
        val isWarm = base.red > base.blue
        val alpha = if (isLightMode) (if (isWarm) 0.10f else 0.13f) else 0.15f
        if (isLightMode) Color(0xFFFFFDF9).copy(alpha = 0f).let { base.copy(alpha = alpha) }
        else base.copy(alpha = alpha)
    } else {
        if (isLightMode) com.mlue.app.ui.theme.LightSurface else MaterialTheme.colorScheme.surface
    }

    Surface(
        color = cardColor,
        shadowElevation = if (isLightMode) 0.dp else 1.dp,
        tonalElevation = if (isLightMode) 0.dp else 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = if (isLightMode && entry.color == 0) androidx.compose.foundation.BorderStroke(
            0.5.dp,
            com.mlue.app.ui.theme.LightTopologyBorder
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isLightMode) Modifier.shadow(
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    spotColor = Color(0xFF2A140A).copy(alpha = 0.08f),
                    ambientColor = Color(0xFF2A140A).copy(alpha = 0.04f)
                ) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = onClick
            )
    ) {
        // 4dp left accent bar for colored entries — editorial signal, not saturation boost
        Row(modifier = Modifier.fillMaxWidth()) {
            if (entry.color != 0) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            color = Color(entry.color).copy(alpha = 0.60f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 20.dp, bottomStart = 20.dp
                            )
                        )
                )
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: title + timestamp + preview snippet
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Title row — title + mood emoji inline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (moodEmoji != null) {
                        Text(text = moodEmoji, fontSize = 14.sp)
                    }
                }
                // Timestamp
                val timeString = java.text.SimpleDateFormat(
                    "hh:mm a",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(entry.timestamp))
                Text(
                    text = "${ entry.date }  ·  $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Body preview — 1 line max
                if (entry.body.isNotBlank()) {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: overflow menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteRequest()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditor(
    initialEntry: JournalEntryEntity?,
    onSave: (String, String, LocalDate, String?, Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Use rememberSaveable to protect drafts from process death.
    // Keying by initialEntry?.id prevents state bleed when switching edited entries.
    var title by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.title ?: "") }
    var body by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.body ?: "") }
    var mood by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.mood ?: "") }
    
    val dateSaver = androidx.compose.runtime.saveable.Saver<java.time.LocalDate, Long>(
        save = { it.toEpochDay() },
        restore = { java.time.LocalDate.ofEpochDay(it) }
    )
    var entryDate by rememberSaveable(initialEntry?.id, stateSaver = dateSaver) { mutableStateOf(initialEntry?.date ?: LocalDate.now()) }
    
    // Journal color — 0 = neutral (no tint). Reuses habit card palette.
    var selectedColor by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.color ?: 0) }

    // Static opaque token for focused container — no alpha copy of Material-derived surfaceVariant
    val editorFocusedBg = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
        com.mlue.app.ui.theme.LightSurfaceVariant
    else
        com.mlue.app.ui.theme.DarkSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                title = {
                    Text(
                        if (initialEntry != null) "Edit Entry" else "Reflect",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (body.isNotBlank()) {
                                onSave(title, body, entryDate, mood.ifBlank { null }, selectedColor)
                            }
                        },
                        enabled = body.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text("Title (Optional)", style = MaterialTheme.typography.titleLarge)
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = editorFocusedBg,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    disabledIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            TextField(
                value = body,
                onValueChange = { body = it },
                placeholder = {
                    Text("What's on your mind?", style = MaterialTheme.typography.bodyLarge)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = editorFocusedBg,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Mood",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
            val predefinedMoods = listOf(
                "RAD" to "😁", "GOOD" to "🙂", "MEH" to "😐", "BAD" to "🙁", "AWFUL" to "😢"
            )
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(predefinedMoods) { (moodName, emoji) ->
                    val isSelected = mood == moodName
                    FilterChip(
                        selected = isSelected,
                        onClick = { mood = if (isSelected) "" else moodName },
                        label = { Text(emoji, fontSize = 20.sp) },
                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.Transparent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Color",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val isNeutral = selectedColor == 0
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                                    com.mlue.app.ui.theme.LightSurfaceVariant
                                else
                                    com.mlue.app.ui.theme.DarkSurfaceVariant,
                                shape = CircleShape
                            )
                            .then(if (isNeutral) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .clickable { selectedColor = 0 }
                    )
                }
                items(JOURNAL_COLORS) { colorInt ->
                    val isSel = selectedColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(colorInt).copy(alpha = 0.75f), CircleShape)
                            .then(if (isSel) Modifier.border(2.dp, Color(colorInt), CircleShape) else Modifier)
                            .clickable { selectedColor = colorInt }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    val current = entryDate
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            entryDate = LocalDate.of(year, month + 1, dayOfMonth)
                        },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth
                    ).show()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "Date: $entryDate", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Journal color palette — same hues as AddHabitScreen DEFAULT_COLORS
// Calmer presentation via lower alpha on the card, not lower saturation here.
private val JOURNAL_COLORS = listOf(
    0xFF3B82F6.toInt(),  // Blue
    0xFF10B981.toInt(),  // Emerald
    0xFFF59E0B.toInt(),  // Amber
    0xFFEF4444.toInt(),  // Red
    0xFF8B5CF6.toInt(),  // Violet
    0xFF14B8A6.toInt()   // Teal
)
