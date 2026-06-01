package com.mlue.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlue.app.data.GoalEntity
import com.mlue.app.data.HabitEntity
import com.mlue.app.data.JournalEntryEntity
import com.mlue.app.viewmodel.CalendarDayState
import com.mlue.app.viewmodel.HabitViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, viewModel: HabitViewModel) {
    val month by viewModel.calendarMonth.collectAsState()
    val days by viewModel.calendarDays.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val completedIds by viewModel.selectedDayCompletedHabitIds.collectAsState()
    val journalEntries by viewModel.selectedDayJournalEntries.collectAsState()

    val monthSaver = androidx.compose.runtime.saveable.Saver<java.time.YearMonth, String>(
        save = { it.toString() },
        restore = { 
            try { 
                java.time.YearMonth.parse(it) 
            } catch (e: Exception) { 
                android.util.Log.e("MlueState", "Failed to restore YearMonth: $it", e)
                java.time.YearMonth.now() 
            } 
        }
    )
    var displayedMonth by rememberSaveable(stateSaver = monthSaver) { mutableStateOf(java.time.YearMonth.now()) }
    
    val dateSaver = androidx.compose.runtime.saveable.Saver<java.time.LocalDate?, String>(
        save = { it?.toString() ?: "" },
        restore = { 
            if (it.isNotEmpty()) {
                try {
                    java.time.LocalDate.parse(it)
                } catch (e: Exception) {
                    android.util.Log.e("MlueState", "Failed to restore LocalDate: $it", e)
                    null
                }
            } else null 
        }
    )
    var selectedDate by rememberSaveable(stateSaver = dateSaver) { mutableStateOf<java.time.LocalDate?>(null) }
    var isSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(displayedMonth) {
        if (displayedMonth != month) {
            viewModel.loadCalendar(displayedMonth)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                // 104dp bottom: dock(68) + dock-margin(20) + breathing(16)
                .padding(bottom = 104.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Zone 1: top breathing space — pushes stats row away from the TopAppBar
            Spacer(modifier = Modifier.height(24.dp))

            CalendarHeaderStats(days = days, habits = habits)

            // Zone 2: intentional gap between stats and calendar — airy, not tight
            Spacer(modifier = Modifier.height(24.dp))

            val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

            Surface(
                color = if (isLightMode) com.mlue.app.ui.theme.LightSurfaceElevated else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                // Light: warm topology hairline. Dark: no border — tonal depth is the separator
                border = if (isLightMode)
                    androidx.compose.foundation.BorderStroke(0.75.dp, com.mlue.app.ui.theme.LightTopologyBorder)
                else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLightMode) Modifier.shadow(
                            elevation = 4.dp,
                            shape = MaterialTheme.shapes.large,
                            spotColor = Color(0xFF2A140A).copy(alpha = 0.08f),
                            ambientColor = Color(0xFF2A140A).copy(alpha = 0.04f)
                        ) else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = "${displayedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${displayedMonth.year}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next month")
                        }
                    }

                    Crossfade(
                        targetState = displayedMonth,
                        animationSpec = tween(durationMillis = com.mlue.app.ui.theme.AppMotion.durationMedium),
                        label = "monthFade"
                    ) { targetMonth ->
                        CalendarMonthGrid(
                            month = targetMonth,
                            days = if (targetMonth == month) days else emptyList(),
                            onDaySelected = { date ->
                                selectedDate = date
                                viewModel.loadDayDetail(date)
                                isSheetOpen = true
                            }
                        )
                    }
                }
            }

            // Zone 3: bottom clearance
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    val date = selectedDate
    if (isSheetOpen && date != null) {
        ModalBottomSheet(
            onDismissRequest = { isSheetOpen = false },
            sheetState = sheetState
        ) {
            DayDetailSheet(
                date = date,
                habits = habits,
                completedIds = completedIds,
                journalEntries = journalEntries,
                goals = goals
            )
        }
    }
}

@Composable
fun CalendarHeaderStats(days: List<CalendarDayState>, habits: List<HabitEntity>) {
    val totalScheduled = days.sumOf { it.scheduledCount }
    val totalCompleted = days.sumOf { it.completedCount }
    val consistency = if (totalScheduled > 0) (totalCompleted * 100) / totalScheduled else 0
    val bestStreak = habits.maxOfOrNull { it.longestStreak } ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatCard(title = "Consistency", value = "$consistency%", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        StatCard(title = "Completions", value = "$totalCompleted", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        StatCard(
            title = "Best Streak",
            value = "$bestStreak 🔥",
            modifier = Modifier.weight(1f),
            highlight = bestStreak > 0
        )
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        // Explicit static token — no surfaceVariant alpha copy (OEM tonal bleed risk)
        color = if (highlight)
            MaterialTheme.colorScheme.primaryContainer
        else if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
            com.mlue.app.ui.theme.LightSurfaceVariant
        else
            com.mlue.app.ui.theme.DarkSurface,
        // Light: topology hairline separates stat cards from background
        // Dark: no border — DarkSurface tonal shift handles it
        border = if (!highlight && MaterialTheme.colorScheme.background.luminance() > 0.5f)
            androidx.compose.foundation.BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder)
        else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    days: List<CalendarDayState>,
    onDaySelected: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    val startOffset = ((firstDay.dayOfWeek.value + 6) % 7)
    val totalDays = month.lengthOfMonth()
    val stateMap = days.associateBy { it.date }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.values().forEach { day ->
                Text(
                    text = day.name.take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val rows = (startOffset + totalDays + 6) / 7
        var index = -startOffset
        repeat(rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(7) {
                    index++
                    if (index in 1..totalDays) {
                        val date = month.atDay(index)
                        val state = stateMap[date]
                        CalendarHeatmapCell(
                            date = date,
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onClick = { onDaySelected(date) }
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHeatmapCell(
    date: LocalDate,
    state: CalendarDayState?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val completed = state?.completedCount ?: 0
    val scheduled = state?.scheduledCount ?: 0

    val rate = if (scheduled == 0) 0f else completed.toFloat() / scheduled.toFloat()

    val backgroundColor = when {
        // Static token for empty cells — no surfaceVariant alpha derivation
        rate == 0f -> if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
            com.mlue.app.ui.theme.LightOutlineVariant.copy(alpha = 0.35f)
        else
            com.mlue.app.ui.theme.DarkSurfaceVariant.copy(alpha = 0.4f)
        rate <= 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        rate <= 0.50f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        rate <= 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    }

    val animatedColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(durationMillis = com.mlue.app.ui.theme.AppMotion.durationShort),
        label = "cellColor"
    )

    val isPerfect = rate >= 1f && scheduled > 0
    val isToday = date == LocalDate.now()

    Surface(
        modifier = modifier.clickable { onClick() },
        color = animatedColor,
        shape = RoundedCornerShape(12.dp),
        border = when {
            isPerfect -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            isToday -> androidx.compose.foundation.BorderStroke(0.75.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            else -> null
        },
        tonalElevation = if (isPerfect) 8.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isPerfect) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (rate > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DayDetailSheet(
    date: LocalDate,
    habits: List<HabitEntity>,
    completedIds: List<Long>,
    journalEntries: List<JournalEntryEntity>,
    goals: List<GoalEntity>
) {
    val completedHabits = remember(habits, completedIds, date) {
        habits.filter { completedIds.contains(it.id) && it.isActiveOn(date) }
    }
    val incompleteHabits = remember(habits, completedIds, date) {
        habits.filter { !completedIds.contains(it.id) && it.isScheduledOn(date) }
    }
    val goalsContributed = remember(goals, completedHabits) {
        goals.filter { goal -> completedHabits.any { it.goalId == goal.goalId } }
    }

    val total = completedHabits.size + incompleteHabits.size
    val rate = if (total == 0) 0 else (completedHabits.size * 100) / total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}, ${date.year}",
                style = MaterialTheme.typography.titleLarge
            )
            Surface(
                color = if (rate == 100 && total > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                    com.mlue.app.ui.theme.LightSurfaceVariant
                else
                    com.mlue.app.ui.theme.DarkSurfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "$rate% Complete",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (rate == 100 && total > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (journalEntries.isNotEmpty()) {
            Text("Reflections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            journalEntries.forEach { entry ->
                // Explicit static token — surfaceVariant.copy(alpha=0.5f) is a banned pattern
                Surface(
                    color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                        com.mlue.app.ui.theme.LightSurfaceVariant
                    else
                        com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            entry.mood?.let {
                                val moodEmoji = when (it) {
                                    "RAD" -> "😁"
                                    "GOOD" -> "🙂"
                                    "MEH" -> "😐"
                                    "BAD" -> "🙁"
                                    "AWFUL" -> "😢"
                                    else -> it
                                }
                                Text(moodEmoji)
                            }
                        }
                        if (entry.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                entry.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (goalsContributed.isNotEmpty()) {
            Text("Goals Progressed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(goalsContributed.joinToString(", ") { it.title }, style = MaterialTheme.typography.bodyMedium)
        }

        Text("Habits", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (completedHabits.isEmpty() && incompleteHabits.isEmpty()) {
            Text("No habits scheduled for this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            completedHabits.forEach {
                Text("✓ ${it.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            incompleteHabits.forEach {
                Text("○ ${it.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
