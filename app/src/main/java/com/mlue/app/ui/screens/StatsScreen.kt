package com.mlue.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlue.app.viewmodel.HabitViewModel
import com.mlue.app.viewmodel.DailyStats
import com.mlue.app.viewmodel.GoalMomentum
import com.mlue.app.ui.components.MonthlyAnalyticsCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.navigationBarsPadding
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: HabitViewModel,
    highlightGoalId: Long? = null,
    onHighlightConsumed: () -> Unit = {},
    openDialogRequest: Boolean = false,
    onDialogRequestConsumed: () -> Unit = {}
) {
    val weeklySummary by viewModel.weeklySummary.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()
    val monthlyCount by viewModel.monthlyCount.collectAsState()
    val focusMode by viewModel.focusModeEnabled.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val goalProgress by viewModel.goalProgress.collectAsState()

    val context = LocalContext.current
    var showGoalEditor by remember { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var goalTitle by rememberSaveable { mutableStateOf("") }
    var goalDescription by rememberSaveable { mutableStateOf("") }
    var goalStartDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var goalDeadline by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var goalToDelete by remember { mutableStateOf<com.mlue.app.data.GoalEntity?>(null) }
    
    val activeGoals by remember(goals) { derivedStateOf { goals.filter { !it.isCompleted } } }
    val completedGoals by remember(goals) { derivedStateOf { goals.filter { it.isCompleted } } }
    val consistencyScore by viewModel.consistencyScore.collectAsState()
    val haptics by viewModel.hapticsEnabled.collectAsState()
    val goalHealthStates by viewModel.goalHealthStates.collectAsState()
    val monthlyAnalytics by viewModel.monthlyAnalytics.collectAsState()
    val prioritizedInsights by viewModel.prioritizedInsights.collectAsState()
    val rhythmInsights by viewModel.rhythmInsights.collectAsState()
    val haptic = LocalHapticFeedback.current
    val hintInsightsShown by viewModel.hintInsightsShown.collectAsState()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Transient highlight state — consumes the route argument once per navigation intent
    var activeHighlightId by remember { mutableStateOf(highlightGoalId) }

    LaunchedEffect(activeHighlightId, goals) {
        if (activeHighlightId != null && goals.any { it.goalId == activeHighlightId }) {
            kotlinx.coroutines.delay(300)
            bringIntoViewRequester.bringIntoView()
            // After successfully applying the visual focus intent, safely consume the nav argument
            onHighlightConsumed()
        }
    }


    LaunchedEffect(openDialogRequest) {
        if (openDialogRequest) {
            editingGoalId = null
            goalTitle = ""
            goalDescription = ""
            goalStartDate = LocalDate.now()
            goalDeadline = null
            showGoalEditor = true
            onDialogRequestConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") }
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Clear highlight on ANY manual user interaction (scroll/tap/swipe) to ensure the 
                // highlight behaves strictly as an ephemeral navigation focus, not persistent state.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press && activeHighlightId != null) {
                                activeHighlightId = null
                            }
                        }
                    }
                }
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hintInsightsShown == false) {
                com.mlue.app.ui.components.HintChip(
                    text = "Insights improve as more habits are completed.",
                    onDismiss = { viewModel.dismissHintInsights() },
                    modifier = Modifier.padding(bottom = 8.dp, start = 0.dp, end = 0.dp)
                )
            }
            val isLightStats = MaterialTheme.colorScheme.background.luminance() > 0.5f
            // Explicit static token — tonalElevation alone triggers Material You tint on OEMs
            Surface(
                color = if (isLightStats) com.mlue.app.ui.theme.LightSurface
                        else com.mlue.app.ui.theme.DarkSurface,
                shape = MaterialTheme.shapes.medium,
                // Light: warm hairline separates this panel from background
                // Dark: no border — DarkSurface tonal shift is the separator
                border = if (isLightStats)
                    BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder)
                else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Active Goals",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    )
                    if (activeGoals.isEmpty()) {
                        Text(
                            text = "Goals group habits into larger outcomes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                editingGoalId = null
                                goalTitle = ""
                                goalDescription = ""
                                goalStartDate = LocalDate.now()
                                goalDeadline = null
                                showGoalEditor = true
                            }
                        ) {
                            Text("Create Goal")
                        }
                    } else {
                        activeGoals.forEach { goal ->
                            val isHighlighted = activeHighlightId == goal.goalId
                            val isLightStats2 = MaterialTheme.colorScheme.background.luminance() > 0.5f
                            Surface(
                                // Highlighted: primaryContainer; normal: static surface token
                                // Dark: DarkSurfaceVariant — one step brighter than DarkSurface for card emergence
                                color = if (isHighlighted)
                                    MaterialTheme.colorScheme.primaryContainer
                                else if (isLightStats2)
                                    com.mlue.app.ui.theme.LightSurfaceVariant
                                else
                                    com.mlue.app.ui.theme.DarkSurfaceVariant,  // was DarkSurface — lifts goal cards out of the background
                                shape = MaterialTheme.shapes.small,
                                border = if (isHighlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .then(if (isHighlighted) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = goal.title, style = MaterialTheme.typography.titleMedium)
                                            val deadline = goal.deadline?.toString() ?: "No deadline"
                                            Text(
                                                text = "Deadline: $deadline",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row {
                                            OutlinedButton(
                                                onClick = {
                                                    editingGoalId = goal.goalId
                                                    goalTitle = goal.title
                                                    goalDescription = goal.description ?: ""
                                                    goalStartDate = goal.startDate
                                                    goalDeadline = goal.deadline
                                                    showGoalEditor = true
                                                }
                                            ) {
                                                Text("Edit")
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(onClick = { goalToDelete = goal }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Goal",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    val progressDetails = goalProgress[goal.goalId]
                                    val overallPercent = progressDetails?.overallPercent ?: 0
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Overall Progress",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = "$overallPercent%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    val animatedProgress by animateFloatAsState(targetValue = overallPercent / 100f, label = "goalProgress")
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp)
                                    )

                                    // Goal health chip — Sprint 3A
                                    // Placed below progress bar: less disruptive, preserves card hierarchy.
                                    val healthState = goalHealthStates[goal.goalId]
                                    if (healthState != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        GoalHealthChip(
                                            momentum = healthState.momentum,
                                            label = healthState.velocityLabel,
                                            isLightMode = isLightStats2
                                        )
                                    }
                                    
                                    if (overallPercent == 100 && !goal.isCompleted) {
                                        Button(
                                            onClick = {
                                                if (haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                viewModel.completeGoal(goal)
                                                scope.launch { snackbarHostState.showSnackbar("Goal completed!") }
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        ) {
                                            Text("Complete Goal")
                                        }
                                    }

                                    if (progressDetails != null && progressDetails.habitProgresses.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Linked Habits",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            progressDetails.habitProgresses.forEach { hp ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = hp.habit.name,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    Text(
                                                        text = "${hp.completed}/${hp.expected} (${hp.percent}%)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        else
                                                            Color(0xFFB0B3B8) // Slightly brighter secondary text for dark mode
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val isLightStatsMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
                                        // Explicit static tokens — removes banned secondaryContainer + alpha bleed
                                        Surface(
                                            color = if (isLightStatsMode)
                                                com.mlue.app.ui.theme.LightSurfaceVariant
                                            else
                                                com.mlue.app.ui.theme.DarkSurface, // was DarkSurfaceVariant — rests inside the elevated card
                                            shape = MaterialTheme.shapes.small,
                                            border = if (isLightStatsMode) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        ) {
                                            Text(
                                                text = "No habits linked. Edit a habit on the Home screen to connect it to this goal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(8.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (completedGoals.isNotEmpty()) {
                Surface(
                    color = if (isLightStats) com.mlue.app.ui.theme.LightSurface
                            else com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (isLightStats) BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Completed Goals", style = MaterialTheme.typography.titleMedium)
                        completedGoals.forEach { goal ->
                            Surface(
                                color = if (isLightStats) com.mlue.app.ui.theme.LightSurfaceVariant else com.mlue.app.ui.theme.DarkSurface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = goal.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Completed: ${goal.completedDate ?: "Unknown"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }



            if (weeklySummary.isEmpty() && monthlyCount == 0) {
                Text(
                    text = "Patterns emerge with consistency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val isLightWeekly = MaterialTheme.colorScheme.background.luminance() > 0.5f
                // Explicit containerColor — tonalElevation alone can bleed Material You tint on OEMs
                Surface(
                    color = if (isLightWeekly) com.mlue.app.ui.theme.LightSurface
                            else com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (isLightWeekly) BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = "Weekly Summary", style = MaterialTheme.typography.titleLarge)
                        WeeklyBars(weeklyStats = weeklyStats)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (isLightStats) com.mlue.app.ui.theme.LightSurface
                            else com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (isLightStats) BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Monthly completions")
                        Text(text = monthlyCount.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (isLightStats) com.mlue.app.ui.theme.LightSurface
                            else com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (isLightStats) BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val totalScheduled = weeklyStats.sumOf { it.scheduled }
                        val totalCompleted = weeklyStats.sumOf { it.completed }
                        val weekRate = if (totalScheduled > 0)
                            (totalCompleted * 100) / totalScheduled else 0
                            
                        val weekLabel = when {
                            weekRate == 100 -> "Perfect consistency"
                            weekRate >= 80 -> "Strong momentum"
                            weekRate >= 50 -> "Building consistency"
                            else -> "Room to rebuild"
                        }
                        Text(
                            text = "Weekly Recap",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = weekLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$totalCompleted of $totalScheduled scheduled habits completed ($weekRate%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Monthly completions to date: $monthlyCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (isLightStats) com.mlue.app.ui.theme.LightSurface
                            else com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (isLightStats) BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val scoreLabel = when {
                            consistencyScore >= 90 -> "Excellent"
                            consistencyScore >= 75 -> "Strong"
                            consistencyScore >= 50 -> "Improving"
                            consistencyScore >= 30 -> "Inconsistent"
                            else -> "Restarting"
                        }
                        Text(
                            text = "Consistency Score",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "$consistencyScore · $scoreLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── Monthly Analytics — Sprint 3A ───────────────────────────────
                // Card is self-contained: handles null and empty-data cases internally.
                // Shown inside the else block (same gate as weekly stats) so it only
                // appears when there is at least some data to reflect on.
                monthlyAnalytics?.let { analytics ->
                    Spacer(modifier = Modifier.height(8.dp))
                    MonthlyAnalyticsCard(
                        analytics = analytics,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // ── Rhythm observation (Sprint 3B) ─────────────────────────────────
                    // Plain text only — no cards, chips, separators, or icon clutter.
                    // Max 1 observation. Null when data is insufficient.
                    rhythmInsights?.observations?.firstOrNull()?.let { obs ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = obs,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            if (!focusMode && prioritizedInsights.isNotEmpty()) {
                Surface(
                    color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                        com.mlue.app.ui.theme.LightSurface
                    else
                        com.mlue.app.ui.theme.DarkSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                        BorderStroke(0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder)
                    else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Insights", style = MaterialTheme.typography.titleMedium)
                        prioritizedInsights.forEach { insight ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = insight,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGoalEditor) {
        AlertDialog(
            onDismissRequest = { showGoalEditor = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (goalTitle.isNotBlank()) {
                            if (editingGoalId == null) {
                                viewModel.addGoal(
                                    title = goalTitle,
                                    description = goalDescription.ifBlank { null },
                                    startDate = goalStartDate,
                                    deadline = goalDeadline
                                ) { newGoalId ->
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Goal created successfully!",
                                            actionLabel = "Add Habit",
                                            duration = androidx.compose.material3.SnackbarDuration.Long
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            navController.navigate("add?goalId=$newGoalId") {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            } else {
                                val existingGoal = goals.firstOrNull { it.goalId == editingGoalId }
                                if (existingGoal != null) {
                                    viewModel.updateGoal(
                                        existingGoal.copy(
                                            title = goalTitle,
                                            description = goalDescription.ifBlank { null },
                                            startDate = goalStartDate,
                                            deadline = goalDeadline
                                        )
                                    )
                                }
                            }
                            showGoalEditor = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalEditor = false }) { Text("Cancel") }
            },
            title = { Text(if (editingGoalId == null) "New goal" else "Edit goal") },
            text = {
                val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
                // Static opaque tokens — surfaceVariant.copy(alpha) is a documented OEM regression trigger
                val fieldFillColor = if (isLightMode)
                    Color(0xFFFBF8F4)
                else
                    com.mlue.app.ui.theme.DarkSurfaceVariant
                val fieldFocusedFillColor = if (isLightMode)
                    Color(0xFFFBF8F4)
                else
                    com.mlue.app.ui.theme.DarkSurface
                val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = fieldFillColor,
                    focusedContainerColor = fieldFocusedFillColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goalTitle,
                        onValueChange = { goalTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors,
                        shape = fieldShape
                    )
                    OutlinedTextField(
                        value = goalDescription,
                        onValueChange = { goalDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors,
                        shape = fieldShape
                    )
                    TextButton(
                        onClick = {
                            val current = goalStartDate
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    goalStartDate = LocalDate.of(year, month + 1, dayOfMonth)
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth
                            ).show()
                        }
                    ) {
                        Text("Start: $goalStartDate")
                    }
                    TextButton(
                        onClick = {
                            val current = goalDeadline ?: LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    goalDeadline = LocalDate.of(year, month + 1, dayOfMonth)
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth
                            ).show()
                        }
                    ) {
                        Text("Deadline: ${goalDeadline ?: "None"}")
                    }
                    if (goalDeadline != null) {
                        TextButton(onClick = { goalDeadline = null }) {
                            Text("Clear deadline")
                        }
                    }
                }
            }
        )
    }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Delete Goal") },
            text = { Text("Are you sure you want to delete '${goal.title}'? Your habits will remain, but they will be disconnected from this goal.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGoal(goal)
                        goalToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WeeklyBars(weeklyStats: List<DailyStats>) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        weeklyStats.forEach { stats ->
            val fraction = stats.completionRate
            val isToday = stats.date == LocalDate.now()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Day label — 3-char, primary accent on today
                Text(
                    text = stats.date.dayOfWeek.name.take(3),
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))

                val animatedFraction by animateFloatAsState(
                    targetValue = if (stats.scheduled == 0) 0f
                                  else fraction.coerceAtLeast(0.02f),
                    animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween(
                        com.mlue.app.ui.theme.AppMotion.durationLong
                    ),
                    label = "weekBar_${stats.date}"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            color = if (isLightMode)
                                com.mlue.app.ui.theme.LightOutlineVariant.copy(alpha = 0.5f)
                            else
                                com.mlue.app.ui.theme.DarkSurfaceVariant.copy(alpha = 0.6f)
                        )
                ) {
                    if (stats.scheduled > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = if (fraction >= 1f)
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        else
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                MaterialTheme.colorScheme.primary
                                            )
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (stats.scheduled == 0) "—"
                           else "${stats.completed}/${stats.scheduled}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fraction >= 1f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

/**
 * Subtle momentum chip displayed below a goal's progress bar.
 *
 * Color system (Sprint 3A design rules):
 *  STRONG         → soft green tint (bg) + SuccessGreen (text)
 *  ON_TRACK       → primary accent (bg LightSurfaceVariant) + primary (text)
 *  SLOW           → neutral (onSurfaceVariant text, muted bg)
 *  NEEDS_ATTENTION → soft amber tint (bg) + LightSecondaryAmber (text) — never red
 */
@Composable
private fun GoalHealthChip(
    momentum: GoalMomentum,
    label: String,
    isLightMode: Boolean
) {
    val textColor = when (momentum) {
        GoalMomentum.STRONG          -> com.mlue.app.ui.theme.SuccessGreen
        GoalMomentum.ON_TRACK        -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        GoalMomentum.SLOW            -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        GoalMomentum.NEEDS_ATTENTION -> com.mlue.app.ui.theme.LightSecondaryAmber
    }
    val chipBg = when {
        momentum == GoalMomentum.STRONG && isLightMode          -> androidx.compose.ui.graphics.Color(0xFFEBF6EF)
        momentum == GoalMomentum.NEEDS_ATTENTION && isLightMode -> androidx.compose.ui.graphics.Color(0xFFFBF0E6)
        isLightMode -> com.mlue.app.ui.theme.LightSurfaceVariant
        else        -> com.mlue.app.ui.theme.DarkSurfaceVariant
    }

    androidx.compose.material3.Surface(
        color = chipBg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
