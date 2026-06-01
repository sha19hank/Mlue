package com.mlue.app.ui.screens

import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlue.app.viewmodel.HabitViewModel
import android.app.DatePickerDialog

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun AddHabitScreen(
    navController: NavController,
    viewModel: HabitViewModel,
    habitId: Long? = null,
    prefilledGoalId: Long? = null,
    prefillTitle: String? = null
) {
    val habits by viewModel.habits.collectAsState()
    val habitToEdit = remember(habitId, habits) {
        if (habitId != null) habits.firstOrNull { it.id == habitId } else null
    }
    var name by rememberSaveable { mutableStateOf(prefillTitle ?: "") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    
    val setSaver = androidx.compose.runtime.saveable.Saver<Set<Int>, List<Int>>(
        save = { it.toList() },
        restore = { it.toSet() }
    )
    var selectedDays by rememberSaveable(stateSaver = setSaver) { mutableStateOf(setOf<Int>()) }
    var reminderEnabled by rememberSaveable { mutableStateOf(false) }
    
    val timeSaver = androidx.compose.runtime.saveable.Saver<java.time.LocalTime, String>(
        save = { it.toString() },
        restore = { 
            try {
                java.time.LocalTime.parse(it) 
            } catch (e: Exception) {
                android.util.Log.e("MlueState", "Failed to restore LocalTime: $it", e)
                java.time.LocalTime.of(9, 0)
            }
        }
    )
    var reminderTime by rememberSaveable(stateSaver = timeSaver) { mutableStateOf(java.time.LocalTime.of(9, 0)) }
    var selectedColor by rememberSaveable { mutableStateOf(DEFAULT_COLORS.first()) }
    var showNameError by remember { mutableStateOf(false) }
    var showDaysError by remember { mutableStateOf(false) }
    var goalExpanded by remember { mutableStateOf(false) }

    // Android 13+ notification permission — requested only when user enables reminders
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // User denied: revert toggle and show a calm single-line notice
            reminderEnabled = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Enable notifications in Settings to use reminders"
                )
            }
        }
        // If granted, reminderEnabled stays true — user enabled the toggle before we asked
    }
    var selectedGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val goals by viewModel.goals.collectAsState()
    val context = LocalContext.current

    val isLightMode = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
    // Static opaque tokens — surfaceVariant.copy(alpha) causes OEM color drift under long sessions
    val fieldFillColor = if (isLightMode)
        androidx.compose.ui.graphics.Color(0xFFFBF8F4)
    else
        com.mlue.app.ui.theme.DarkSurfaceVariant
    val fieldFocusedFillColor = if (isLightMode)
        androidx.compose.ui.graphics.Color(0xFFFBF8F4)
    else
        com.mlue.app.ui.theme.DarkSurface
    val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = fieldFillColor,
        focusedContainerColor = fieldFocusedFillColor,
        // Light: slightly stronger hairline for field topology definition
        // Dark: unchanged outline — tonal separation handles it
        unfocusedBorderColor = if (isLightMode)
            com.mlue.app.ui.theme.LightTopologyBorderStrong
        else
            androidx.compose.material3.MaterialTheme.colorScheme.outline,
        focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    )
    
    var showGoalEditor by remember { mutableStateOf(false) }
    var goalTitle by rememberSaveable { mutableStateOf("") }
    var goalDescription by rememberSaveable { mutableStateOf("") }
    var goalStartDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var goalDeadline by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    val isFirstHabit = habitId == null && habits.isEmpty()

    var isInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(habitToEdit, prefilledGoalId) {
        if (!isInitialized) {
            if (habitToEdit != null) {
                name = habitToEdit.name
                description = habitToEdit.description ?: ""
                category = habitToEdit.category ?: ""
                selectedDays = habitToEdit.scheduledDays.toSet()
                reminderEnabled = habitToEdit.reminderEnabled
                reminderTime = habitToEdit.reminderTime ?: LocalTime.of(9, 0)
                selectedColor = habitToEdit.color.takeIf { it != 0 } ?: DEFAULT_COLORS.first()
                selectedGoalId = habitToEdit.goalId
                isInitialized = true
            } else if (prefilledGoalId != null) {
                selectedGoalId = prefilledGoalId
                isInitialized = true
            } else if (habitId == null) {
                isInitialized = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (habitId != null) "Edit Habit" else "Add Habit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp)
        ) {
            if (isFirstHabit) {
                Text(
                    text = "Start with one habit you can repeat consistently.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Soft suggestion chips (inspiration only)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val suggestions = listOf("Sleep before 12", "Drink water", "Read 10 pages", "Morning walk")
                    suggestions.forEach { suggestion ->
                        androidx.compose.material3.Surface(
                            onClick = { name = suggestion },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = suggestion,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (showNameError) showNameError = false
                },
                label = { Text("Habit name") },
                isError = showNameError,
                supportingText = if (showNameError) { { Text("Please enter a habit name") } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                shape = fieldShape
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                shape = fieldShape
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                shape = fieldShape
            )

            Spacer(modifier = Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = goalExpanded,
                onExpandedChange = { goalExpanded = !goalExpanded }
            ) {
                val selectedLabel = goals.firstOrNull { it.goalId == selectedGoalId }?.title ?: "No goal"
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Goal (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedContainerColor = fieldFillColor,
                        focusedContainerColor = fieldFocusedFillColor,
                        unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    ),
                    shape = fieldShape
                )
                ExposedDropdownMenu(
                    expanded = goalExpanded,
                    onDismissRequest = { goalExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No goal") },
                        onClick = {
                            selectedGoalId = null
                            goalExpanded = false
                        }
                    )
                    val activeGoals = remember(goals) { goals.filter { !it.isCompleted } }
                    activeGoals.forEach { goal ->
                        DropdownMenuItem(
                            text = { Text(goal.title) },
                            onClick = {
                                selectedGoalId = goal.goalId
                                goalExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Create new goal...") },
                        onClick = {
                            goalExpanded = false
                            goalTitle = ""
                            goalDescription = ""
                            goalStartDate = LocalDate.now()
                            goalDeadline = null
                            showGoalEditor = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Schedule",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = if (showDaysError) com.mlue.app.ui.theme.LightDangerRed else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            val daysErrorBorderColor = if (showDaysError) com.mlue.app.ui.theme.LightDangerRed.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showDaysError) Modifier.border(
                            width = 1.dp,
                            color = daysErrorBorderColor,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ).padding(8.dp) else Modifier
                    )
            ) {
                DAYS.forEach { (label, value) ->
                    val selected = selectedDays.contains(value)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDays = if (selected) selectedDays - value else selectedDays + value
                            if (showDaysError && selectedDays.isNotEmpty()) showDaysError = false
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (showDaysError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select at least one day",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = com.mlue.app.ui.theme.LightDangerRed
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Color",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DEFAULT_COLORS.forEach { colorInt ->
                    val selected = selectedColor == colorInt
                    Surface(
                        color = Color(colorInt),
                        shape = androidx.compose.material3.MaterialTheme.shapes.small,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(2.dp)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = androidx.compose.material3.MaterialTheme.shapes.small
                            )
                            .clickable { selectedColor = colorInt }
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(text = "Reminder")
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { enabling ->
                        if (enabling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = context.checkSelfPermission(
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                // Set toggle optimistically; launcher reverts it if denied
                                reminderEnabled = true
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                                return@Switch
                            }
                        }
                        reminderEnabled = enabling
                    }
                )
            }
            if (reminderEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val dialog = TimePickerDialog(
                        context,
                        { _, hour, minute -> reminderTime = LocalTime.of(hour, minute) },
                        reminderTime.hour,
                        reminderTime.minute,
                        false
                    )
                    dialog.show()
                }) {
                    Text(text = "Reminder time: ${reminderTime}")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val nameValid = name.isNotBlank()
                    val daysValid = selectedDays.isNotEmpty()
                    if (!nameValid || !daysValid) {
                        showNameError = !nameValid
                        showDaysError = !daysValid
                    } else {
                        if (habitId != null) {
                            viewModel.updateHabitFull(
                                habitId = habitId,
                                name = name,
                                description = description.ifBlank { null },
                                category = category.ifBlank { null },
                                color = selectedColor,
                                scheduledDays = selectedDays.toList().sorted(),
                                reminderEnabled = reminderEnabled,
                                reminderTime = if (reminderEnabled) reminderTime else null,
                                paused = habitToEdit?.paused ?: false,
                                stepEnabled = false,
                                stepGoal = null,
                                goalId = selectedGoalId
                            )
                        } else {
                            viewModel.addHabit(
                                name = name,
                                description = description.ifBlank { null },
                                category = category.ifBlank { null },
                                color = selectedColor,
                                scheduledDays = selectedDays.toList().sorted(),
                                reminderEnabled = reminderEnabled,
                                reminderTime = if (reminderEnabled) reminderTime else null,
                                paused = false,
                                stepEnabled = false,
                                stepGoal = null,
                                goalId = selectedGoalId
                            )
                        }
                        navController.popBackStack()
                    }
                }
            ) {
                Text(if (habitId != null) "Save Changes" else "Save Habit")
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
                            viewModel.addGoal(
                                title = goalTitle,
                                description = goalDescription.ifBlank { null },
                                startDate = goalStartDate,
                                deadline = goalDeadline
                            ) { newId ->
                                selectedGoalId = newId
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
            title = { Text("New goal") },
            text = {
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
}

private val DAYS = listOf(
    "Mon" to 1,
    "Tue" to 2,
    "Wed" to 3,
    "Thu" to 4,
    "Fri" to 5,
    "Sat" to 6,
    "Sun" to 7
)

private val DEFAULT_COLORS = listOf(
    0xFF3B82F6.toInt(),
    0xFF10B981.toInt(),
    0xFFF59E0B.toInt(),
    0xFFEF4444.toInt(),
    0xFF8B5CF6.toInt(),
    0xFF14B8A6.toInt()
)
