package com.mlue.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mlue.app.DailyHabitTrackerApp
import com.mlue.app.data.DateCount
import com.mlue.app.data.GoalEntity
import com.mlue.app.data.GoalProgressDetails
import com.mlue.app.data.HabitEntity
import com.mlue.app.data.HabitRepository
import com.mlue.app.data.JournalEntryEntity
import com.mlue.app.data.SettingsRepository
import com.mlue.app.reminders.GoalDeadlineScheduler
import com.mlue.app.reminders.ReminderScheduler
import com.mlue.app.sensors.StepState
import com.mlue.app.sensors.StepTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import com.mlue.app.data.HabitCompletionEntity

class HabitViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as DailyHabitTrackerApp).container
    private val repository: HabitRepository = container.habitRepository
    private val settings: SettingsRepository = container.settings
    private val reminderScheduler: ReminderScheduler = container.reminderScheduler
    private val goalDeadlineScheduler: GoalDeadlineScheduler = container.goalDeadlineScheduler
    private val stepTracker: StepTracker = container.stepTracker



    private val habitsFlow = repository.getHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val goalsFlow = repository.getGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = goalsFlow

    val journalEntries: StateFlow<List<JournalEntryEntity>> = repository.getJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val habits: StateFlow<List<HabitEntity>> = combine(habitsFlow, sortOption) { habits, option ->
        when (option) {
            SortOption.NAME -> habits.sortedBy { it.name.lowercase() }
            SortOption.STREAK -> habits.sortedByDescending { it.currentStreak }
            SortOption.LAST_COMPLETED -> habits.sortedByDescending { it.lastCompletedDate ?: LocalDate.MIN }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklySummary: StateFlow<List<DateCount>> = repository.getAllCompletionsFlow()
        .map { completions ->
            val today = LocalDate.now()
            val startOfWeek = today.minusDays(6)

            val counts = mutableMapOf<String, Int>()
            var date = startOfWeek
            repeat(7) {
                counts[date.toString()] = 0
                date = date.plusDays(1)
            }

            completions.forEach { c ->
                val dateStr = c.completionDate.toString()
                if (counts.containsKey(dateStr)) {
                    counts[dateStr] = counts[dateStr]!! + 1
                }
            }

            counts.map { DateCount(it.key, it.value) }.sortedBy { it.date }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Per-day stats for the last 7 days: completed count + scheduled count.
     * Used by WeeklyBars for true percentage-based rendering.
     * Days with 0 scheduled habits are represented with scheduledCount=0.
     */
    val weeklyStats: StateFlow<List<DailyStats>> = combine(
        repository.getAllCompletionsFlow(),
        habitsFlow
    ) { completions, habits ->
        val today = LocalDate.now()
        (0..6).map { offset ->
            val day = today.minusDays((6 - offset).toLong())
            val completed = completions.count { it.completionDate == day }
            val scheduled = habits.count { habit ->
                habit.isScheduledOn(day)
            }
            DailyStats(date = day, completed = completed, scheduled = scheduled)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    val monthlyCount: StateFlow<Int> = repository.getAllCompletionsFlow()
        .map { completions ->
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            val startOfMonth = month.atDay(1)
            val endOfMonth = month.atEndOfMonth()
            
            completions.count { !it.completionDate.isBefore(startOfMonth) && !it.completionDate.isAfter(endOfMonth) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val insights: StateFlow<List<String>> = combine(habitsFlow, weeklyStats) { habits, stats ->
        buildInsights(habits, stats)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _habitHistory = MutableStateFlow<Map<Long, HabitHistory>>(emptyMap())
    val habitHistory: StateFlow<Map<Long, HabitHistory>> = _habitHistory.asStateFlow()

    // 30-day Consistency Score (Task 3)
    val consistencyScore: StateFlow<Int> = combine(
        repository.getAllCompletionsFlow(),
        habitsFlow
    ) { completions, habits ->
        val today = LocalDate.now()
        val startDay = today.minusDays(29) // Last 30 days including today
        var totalScheduled = 0
        var totalCompleted = 0

        val days = (0..29).map { startDay.plusDays(it.toLong()) }
        
        for (day in days) {
            val completedToday = completions.count { it.completionDate == day }
            val scheduledToday = habits.count { habit ->
                habit.isScheduledOn(day)
            }
            totalCompleted += completedToday
            totalScheduled += scheduledToday
        }

        if (totalScheduled == 0) 0 else ((totalCompleted.toFloat() / totalScheduled) * 100).toInt().coerceIn(0, 100)
    }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val goalProgress: StateFlow<Map<Long, GoalProgressDetails>> = repository.getGoalProgressMapFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val activeGoal: StateFlow<ActiveGoalState?> = combine(goalsFlow, goalProgress) { goals, progress ->
        val active = selectActiveGoal(goals, LocalDate.now())
        if (active == null) {
            null
        } else {
            ActiveGoalState(active, progress[active.goalId] ?: GoalProgressDetails(0, emptyList()))
        }
    }.stateIn<ActiveGoalState?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Sprint 3A: Behavioral Intelligence ────────────────────────────────────
    // All four flows are derived in-memory — no new DB queries, no DB migration.
    // Every flow is cached with stateIn + WhileSubscribed so it suspends when
    // the screen is not visible. distinctUntilChanged() prevents unnecessary
    // downstream recomposition when computed output hasn't meaningfully changed.

    // Single shared completions subscription — avoids two independent Room observers
    // for the same query (monthlyAnalytics and behavioralPatterns both need it).
    private val completionsFlow: StateFlow<List<HabitCompletionEntity>> =
        repository.getAllCompletionsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlyAnalytics: StateFlow<MonthlyAnalytics?> = combine(
        completionsFlow,
        habitsFlow
    ) { completions, habits ->
        computeMonthlyAnalytics(completions, habits, LocalDate.now())
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val behavioralPatterns: StateFlow<List<BehavioralPattern>> = combine(
        completionsFlow,
        habitsFlow
    ) { completions, habits ->
        detectBehavioralPatterns(completions, habits, LocalDate.now())
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goalHealthStates: StateFlow<Map<Long, GoalHealthState>> = combine(
        goalsFlow,
        goalProgress
    ) { goals, progress ->
        goals.associate { goal ->
            goal.goalId to computeGoalHealth(goal, progress[goal.goalId], LocalDate.now())
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── Sprint 3B: Adaptive Intelligence Layer ────────────────────────────────
    // Additive flows — no existing flow modified, no DB migration.
    // All new flows share completionsFlow/habitsFlow subscriptions.

    /** Per-habit momentum states — used for the 5dp card dot indicator. */
    val habitMomentums: StateFlow<Map<Long, HabitMomentum>> = combine(
        completionsFlow,
        habitsFlow
    ) { completions, habits ->
        val today = LocalDate.now()
        habits.associate { habit -> habit.id to computeMomentum(habit, completions, today) }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Rhythm observations — soft weekday/recovery/load patterns.
     * Null when data is insufficient (< 10 completions, < 14 days history).
     * Max 2 observations per cycle.
     */
    val rhythmInsights: StateFlow<RhythmInsight?> = combine(
        completionsFlow,
        habitsFlow
    ) { completions, habits ->
        detectRhythm(completions, habits, LocalDate.now())
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Prioritized, semantically-deduplicated insights feed.
     * Merges base weekly insights + behavioral patterns + monthly trend + rhythm.
     * Hard cap: 5 items. High-priority insights (priority ≤ 2) are sticky.
     * Lower-priority insights rotate daily via date-seeding.
     * Existing [insights] StateFlow is preserved unchanged.
     */
    val prioritizedInsights: StateFlow<List<String>> = combine(
        insights,
        behavioralPatterns,
        monthlyAnalytics,
        rhythmInsights
    ) { baseInsights, patterns, monthly, rhythm ->
        buildPrioritizedInsights(baseInsights, patterns, monthly, rhythm)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _selectedDayCompletedHabitIds = MutableStateFlow<List<Long>>(emptyList())
    val selectedDayCompletedHabitIds: StateFlow<List<Long>> = _selectedDayCompletedHabitIds.asStateFlow()

    private val _selectedDayJournalEntries = MutableStateFlow<List<JournalEntryEntity>>(emptyList())
    val selectedDayJournalEntries: StateFlow<List<JournalEntryEntity>> = _selectedDayJournalEntries.asStateFlow()


    val darkModeEnabled: Flow<Boolean> = settings.darkModeEnabled()
    
    fun getCachedTheme(): Boolean? = settings.getCachedTheme()

    val soundsEnabled: StateFlow<Boolean> = settings.soundsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val focusModeEnabled: StateFlow<Boolean> = settings.focusModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Behavior-aware sorted list for focus mode.
     * When focus OFF: same as [habits] (user's chosen sort order preserved).
     * When focus ON: filters to scheduled-today only, sorted incomplete-first then by streak.
     * Declared after focusModeEnabled to satisfy Kotlin property init ordering.
     */
    val adaptiveFocusedHabits: StateFlow<List<HabitEntity>> = combine(
        habitsFlow,
        focusModeEnabled
    ) { allHabits, focusMode ->
        if (!focusMode) return@combine allHabits
        val today = LocalDate.now()
        allHabits
            .filter { habit ->
                !habit.paused &&
                (habit.scheduledDays.isEmpty() || habit.scheduledDays.contains(today.dayOfWeek.value))
            }
            .sortedWith(
                compareBy<HabitEntity>(
                    { it.lastCompletedDate == today },
                    { -it.currentStreak }
                )
            )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val animationsEnabled: StateFlow<Boolean> = settings.animationsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _milestoneEvents = Channel<MilestoneEvent>(Channel.BUFFERED)
    val milestoneEvents = _milestoneEvents.receiveAsFlow()

    private val _goalCompletionEvents = Channel<GoalCompletionEvent>(Channel.BUFFERED)
    val goalCompletionEvents = _goalCompletionEvents.receiveAsFlow()

    val tokenCount: StateFlow<Int> = repository.tokenFlow()
        .map { it?.count ?: 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val stepState: StateFlow<StepState> = stepTracker.stepState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepState(false, 0))

    private val _calendarMonth = MutableStateFlow(YearMonth.now())
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    private val _calendarDays = MutableStateFlow<List<CalendarDayState>>(emptyList())
    val calendarDays: StateFlow<List<CalendarDayState>> = _calendarDays.asStateFlow()

    private var calendarDirty = true

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshBrokenStreaks(LocalDate.now())
            // Restore all alarms on startup — ensures reminders survive process death
            reminderScheduler.scheduleAll()
            goalDeadlineScheduler.scheduleAll()
            loadCalendar(YearMonth.now())
        }


    }

    fun addHabit(
        name: String,
        description: String?,
        category: String?,
        color: Int,
        scheduledDays: List<Int>,
        reminderEnabled: Boolean,
        reminderTime: LocalTime?,
        paused: Boolean,
        stepEnabled: Boolean,
        stepGoal: Int?,
        goalId: Long?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = repository.addHabit(
                name = name,
                description = description,
                category = category,
                color = color,
                scheduledDays = scheduledDays,
                reminderEnabled = reminderEnabled,
                reminderTime = reminderTime,
                paused = paused,
                stepEnabled = stepEnabled,
                stepGoal = stepGoal,
                goalId = goalId
            )
            // Schedule alarm using the returned ID + known parameters.
            // We construct a minimal HabitEntity matching what was just inserted
            // to avoid an extra DB round-trip or needing getHabitsOnce() on the repo.
            if (reminderEnabled && reminderTime != null && !paused) {
                reminderScheduler.scheduleHabit(
                    HabitEntity(
                        id = newId,
                        name = name,
                        description = description,
                        category = category,
                        color = color,
                        scheduledDays = scheduledDays,
                        reminderEnabled = true,
                        reminderTime = reminderTime,
                        paused = false,
                        stepEnabled = stepEnabled,
                        stepGoal = stepGoal,
                        goalId = goalId
                    )
                )
            }
        }
    }

    fun updateHabitFull(
        habitId: Long,
        name: String,
        description: String?,
        category: String?,
        color: Int,
        scheduledDays: List<Int>,
        reminderEnabled: Boolean,
        reminderTime: LocalTime?,
        paused: Boolean,
        stepEnabled: Boolean,
        stepGoal: Int?,
        goalId: Long?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getHabitsOnce().firstOrNull { it.id == habitId }
            if (existing != null) {
                val updated = existing.copy(
                    name = name,
                    description = description,
                    category = category,
                    color = color,
                    scheduledDays = scheduledDays,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    paused = paused,
                    stepEnabled = stepEnabled,
                    stepGoal = stepGoal,
                    goalId = goalId
                )
                repository.updateHabit(updated)
                // scheduleHabit handles both schedule and cancel based on reminderEnabled/paused
                reminderScheduler.scheduleHabit(updated)
            }
        }
    }

    fun addGoal(
        title: String,
        description: String?,
        startDate: LocalDate,
        deadline: LocalDate?,
        onSuccess: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.addGoal(title, description, startDate, deadline)
            goalDeadlineScheduler.scheduleAll()
            withContext(Dispatchers.Main) { onSuccess?.invoke(id) }
        }
    }

    fun updateGoal(goal: GoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateGoal(goal)
            goalDeadlineScheduler.scheduleAll()
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGoal(goal)
            goalDeadlineScheduler.scheduleAll()
        }
    }

    fun completeGoal(goal: GoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markGoalCompleted(goal.goalId, true, LocalDate.now())
            _goalCompletionEvents.send(GoalCompletionEvent(goal.title))
            goalDeadlineScheduler.scheduleAll()
        }
    }

    fun upsertJournalEntry(
        title: String,
        body: String,
        date: LocalDate,
        mood: String?,
        color: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertJournalEntry(title, body, date, mood, color)
        }
    }

    fun updateJournalEntry(entry: JournalEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateJournalEntry(entry)
        }
    }

    fun deleteJournalEntry(entry: JournalEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteJournalEntry(entry)
        }
    }

    fun markCompleted(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val milestoneTriggered = repository.markCompleted(habit, LocalDate.now())
            if (milestoneTriggered != null) {
                _milestoneEvents.send(MilestoneEvent(habit.name, milestoneTriggered, habit.id))
            }
            calendarDirty = true
            loadCalendar(_calendarMonth.value)
            // Reschedule next occurrence for this specific habit
            reminderScheduler.scheduleHabit(habit)
        }
    }

    fun toggleHabitCompletion(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val completedToday = habit.lastCompletedDate == today
            if (completedToday) {
                repository.markUncompleted(habit, today)
            } else {
                val milestoneTriggered = repository.markCompleted(habit, today)
                if (milestoneTriggered != null) {
                    _milestoneEvents.send(MilestoneEvent(habit.name, milestoneTriggered, habit.id))
                }
            }
            calendarDirty = true
            loadCalendar(_calendarMonth.value)
            reminderScheduler.scheduleHabit(habit)
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Cancel alarm BEFORE deleting so we have the habit ID to cancel against
            reminderScheduler.cancelHabit(habit.id)
            repository.deleteHabit(habit)
        }
    }



    fun tryBuyStreakFreeze(habit: HabitEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val frozen = repository.buyStreakFreeze(habit.id)
            withContext(Dispatchers.Main) { onResult(frozen) }
        }
    }

    fun updateSort(option: SortOption) {
        _sortOption.value = option
    }

    fun togglePaused(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val nowPaused = !habit.paused
            repository.pauseHabit(habit, nowPaused)
            if (nowPaused) {
                // Immediately cancel the alarm when pausing
                reminderScheduler.cancelHabit(habit.id)
            } else {
                // Restore alarm when unpausing — re-read from DB for fresh state
                val restored = repository.getHabitsOnce().firstOrNull { it.id == habit.id }
                if (restored != null) reminderScheduler.scheduleHabit(restored)
            }
        }
    }

    fun updateReminder(habit: HabitEntity, enabled: Boolean, time: LocalTime?) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = habit.copy(reminderEnabled = enabled, reminderTime = time)
            repository.updateHabit(updated)
            // scheduleHabit handles both enable (schedule) and disable (cancel)
            reminderScheduler.scheduleHabit(updated)
        }
    }

    fun loadCalendar(month: YearMonth) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!calendarDirty && month == _calendarMonth.value) return@launch
            val habits = repository.getHabitsOnce()
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            val completions = repository.weeklySummary(start, end)
            val completionMap = completions.associateBy { it.date }
            val allJournals = repository.getJournalEntries().first()
            val journalMap = allJournals.groupBy { it.date }

            val days = (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val scheduledCount = habits.count { habit ->
                    habit.isScheduledOn(date)
                }
                val completedCount = completionMap[date.toString()]?.count ?: 0
                val journalCount = journalMap[date]?.size ?: 0
                CalendarDayState(date, completedCount, scheduledCount, journalCount)
            }
            _calendarMonth.value = month
            _calendarDays.value = days
            calendarDirty = false
        }
    }

    fun loadDayDetail(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = repository.completedHabitIdsForDate(date)
            val entries = repository.getJournalEntriesByDate(date)
            _selectedDayCompletedHabitIds.value = completed
            _selectedDayJournalEntries.value = entries
        }
    }


    fun loadHabitHistory(habitId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val startOfMonth = YearMonth.from(today).atDay(1)
            val monthCount = repository.completionCountForHabit(startOfMonth, today, habitId)
            val weekStart = today.minusDays(6)
            val weekCount = repository.completionCountForHabit(weekStart, today, habitId)

            val updated = _habitHistory.value.toMutableMap()
            updated[habitId] = HabitHistory(weekCount = weekCount, monthCount = monthCount)
            _habitHistory.value = updated
        }
    }

    fun isScheduledToday(habit: HabitEntity): Boolean {
        return habit.isScheduledOn(LocalDate.now())
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setDarkMode(enabled) }
    }

    fun setSounds(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setSounds(enabled) }
    }

    fun setFocusMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setFocusMode(enabled) }
    }

    val onboardingCompleted: StateFlow<Boolean> = settings.onboardingCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hintFirstCompletionShown: StateFlow<Boolean?> = settings.hintFirstCompletionShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hintFirstStreakShown: StateFlow<Boolean?> = settings.hintFirstStreakShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hintInsightsShown: StateFlow<Boolean?> = settings.hintInsightsShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hintJournalShown: StateFlow<Boolean?> = settings.hintJournalShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hintGoalShown: StateFlow<Boolean?> = settings.hintGoalShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hintFocusShown: StateFlow<Boolean?> = settings.hintFocusShown().map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setOnboardingCompleted(completed) }
    }

    fun dismissHintFirstCompletion() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintFirstCompletion() } }
    fun dismissHintFirstStreak() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintFirstStreak() } }
    fun dismissHintInsights() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintInsights() } }
    fun dismissHintJournal() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintJournal() } }
    fun dismissHintGoal() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintGoal() } }
    fun dismissHintFocus() { viewModelScope.launch(Dispatchers.IO) { settings.dismissHintFocus() } }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.setHaptics(enabled) }
    }
}

@androidx.compose.runtime.Immutable
data class ActiveGoalState(
    val goal: GoalEntity,
    val progressDetails: GoalProgressDetails
)

data class MilestoneEvent(val habitName: String, val streak: Int, val habitId: Long)
data class GoalCompletionEvent(val goalTitle: String)

/**
 * Per-day completion stats for the weekly summary chart.
 * [completed] = habits actually completed that day.
 * [scheduled] = habits that were due/scheduled that day (not paused).
 * [completionRate] = 0.0..1.0, safe against division-by-zero.
 */
data class DailyStats(
    val date: LocalDate,
    val completed: Int,
    val scheduled: Int
) {
    val completionRate: Float
        get() = if (scheduled == 0) 0f else (completed.toFloat() / scheduled.toFloat()).coerceIn(0f, 1f)
}

enum class SortOption { NAME, STREAK, LAST_COMPLETED }

@androidx.compose.runtime.Immutable
data class CalendarDayState(
    val date: LocalDate,
    val completedCount: Int,
    val scheduledCount: Int,
    val journalCount: Int = 0
) {
    val missedScheduled: Int
        get() = (scheduledCount - completedCount).coerceAtLeast(0)
}

@androidx.compose.runtime.Immutable
data class HabitHistory(
    val weekCount: Int,
    val monthCount: Int
)

private fun buildInsights(
    habits: List<HabitEntity>,
    weeklyStats: List<DailyStats>
): List<String> {
    if (habits.isEmpty() || weeklyStats.isEmpty()) return emptyList()

    val activeHabits = habits.filter { !it.paused }
    val insights = mutableListOf<String>()

    // --- Weekly completion rate ---
    val totalScheduled = weeklyStats.sumOf { it.scheduled }
    val totalCompleted = weeklyStats.sumOf { it.completed }
    if (totalScheduled > 0) {
        val rate = (totalCompleted * 100) / totalScheduled
        val rateLabel = when {
            rate >= 90 -> "Outstanding"
            rate >= 70 -> "Strong"
            rate >= 50 -> "Moderate"
            rate >= 25 -> "Low"
            else -> "Very low"
        }
        insights.add("$rateLabel week — $rate% of scheduled habits completed.")
    }

    // --- Best and worst performing days ---
    val daysWithScheduled = weeklyStats.filter { it.scheduled > 0 }
    if (daysWithScheduled.size >= 2) {
        val bestDay = daysWithScheduled.maxByOrNull { it.completionRate }
        val worstDay = daysWithScheduled.minByOrNull { it.completionRate }
        if (bestDay != null && bestDay.completed > 0) {
            val dayName = bestDay.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            val pct = (bestDay.completionRate * 100).toInt()
            insights.add("$dayName was your strongest day this week ($pct% complete).")
        }
        if (worstDay != null && worstDay != bestDay && worstDay.completed < worstDay.scheduled) {
            val dayName = worstDay.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            insights.add("$dayName had the most missed habits — consider adjusting that day's schedule.")
        }
    }

    // --- Perfect days ---
    val perfectDays = weeklyStats.count { it.scheduled > 0 && it.completed >= it.scheduled }
    if (perfectDays > 0) {
        insights.add("$perfectDays perfect ${if (perfectDays == 1) "day" else "days"} this week — every scheduled habit completed.")
    }

    // --- Active streaks ---
    val streaking = activeHabits.filter { it.currentStreak >= 3 }
    if (streaking.isNotEmpty()) {
        val top = streaking.maxByOrNull { it.currentStreak }!!
        insights.add("${top.name} is on a ${top.currentStreak}-day streak. Keep it going!")
    }

    // --- Missed habit pattern ---
    val missedDays = weeklyStats.count { it.scheduled > 0 && it.completed == 0 }
    if (missedDays >= 3) {
        insights.add("$missedDays days this week had no completions. A shorter habit list might help consistency.")
    }

    return insights.take(5)
}

private fun selectActiveGoal(goals: List<GoalEntity>, today: LocalDate): GoalEntity? {
    if (goals.isEmpty()) return null
    val withDeadline = goals.filter { it.deadline != null }
    val upcoming = withDeadline.filter { it.deadline?.isBefore(today) == false }
    val deadlineSorted = upcoming.ifEmpty { withDeadline }

    if (deadlineSorted.isNotEmpty()) {
        return deadlineSorted
            .sortedWith(
                compareBy<GoalEntity> { it.deadline }
                    .thenByDescending { it.goalId }
            )
            .first()
    }

    return goals.maxByOrNull { it.goalId }
}

// ── Sprint 3A: Analytics computation functions ────────────────────────────────
// All functions are pure (no IO, no suspend). They run inside combine() lambdas
// which execute on background dispatchers — never on the main thread.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Count scheduled occurrences of [habit] in the [start]..[end] range.
 * Uses isScheduledOn per-day — consistent with all other temporal guards.
 * O(days) — fast for any realistic analytics window.
 */
private fun habitScheduledInRange(habit: HabitEntity, start: LocalDate, end: LocalDate): Int {
    if (end.isBefore(start)) return 0
    // Fast-path: if habit didn't exist yet in this entire range, skip the loop.
    if (habit.createdDate.isAfter(end)) return 0
    val effectiveStart = maxOf(start, habit.createdDate)
    val daysBetween = ChronoUnit.DAYS.between(effectiveStart, end).toInt()
    if (habit.scheduledDays.isEmpty()) return daysBetween + 1
    var count = 0
    var date = effectiveStart
    repeat(daysBetween + 1) {
        if (habit.isScheduledOn(date)) count++
        date = date.plusDays(1)
    }
    return count
}

/**
 * Compute monthly behavioral analytics from raw completions and habit list.
 * Returns null only when both active habits and completions are completely empty
 * (fresh app with no data). All other states return a populated [MonthlyAnalytics].
 */
private fun computeMonthlyAnalytics(
    completions: List<HabitCompletionEntity>,
    habits: List<HabitEntity>,
    today: LocalDate
): MonthlyAnalytics? {
    val activeHabits = habits.filter { !it.paused }
    if (activeHabits.isEmpty() && completions.isEmpty()) return null

    val currentMonth = YearMonth.from(today)
    val prevMonth = currentMonth.minusMonths(1)
    val monthStart = currentMonth.atDay(1)
    val monthEnd = today
    val prevMonthStart = prevMonth.atDay(1)
    val prevMonthEnd = prevMonth.atEndOfMonth()

    // ── Current month completions ──
    val currentCompletions = completions.filter {
        !it.completionDate.isBefore(monthStart) && !it.completionDate.isAfter(monthEnd)
    }
    val prevCompletions = completions.filter {
        !it.completionDate.isBefore(prevMonthStart) && !it.completionDate.isAfter(prevMonthEnd)
    }

    val totalCompleted = currentCompletions.size
    val totalScheduled = activeHabits.sumOf { habitScheduledInRange(it, monthStart, monthEnd) }
    val completionPercent = if (totalScheduled > 0) (totalCompleted * 100) / totalScheduled else 0

    // ── Previous month for trend ──
    val prevScheduled = activeHabits.sumOf { habitScheduledInRange(it, prevMonthStart, prevMonthEnd) }
    val prevPercent = if (prevScheduled > 0) (prevCompletions.size * 100) / prevScheduled else 0
    val trend = completionPercent - prevPercent

    // ── Strongest habit (most completions this month) ──
    val completionsByHabit = currentCompletions.groupBy { it.habitId }
    val strongestId = completionsByHabit.maxByOrNull { it.value.size }?.key
    val strongestHabitName = habits.firstOrNull { it.id == strongestId }?.name

    // ── Most missed weekday ──
    // Per-date loop: counts scheduled habits per day using isScheduledOn so that habits
    // created mid-month are only counted from their creation date forward.
    // This is O(days × habits) — acceptable for a ≤ 31-day month window.
    val daysBetween = ChronoUnit.DAYS.between(monthStart, monthEnd).toInt()
    // missCountByDow[dow] = number of days with that DOW where completions < scheduled
    val missCountByDow = (1..7).associateWith { dow ->
        var missed = 0
        var date = monthStart
        repeat(daysBetween + 1) {
            if (date.dayOfWeek.value == dow) {
                // Only habits that were active AND scheduled on this specific date count
                val scheduledOnDate = activeHabits.count { h -> h.isScheduledOn(date) }
                if (scheduledOnDate > 0) {
                    val completedCount = currentCompletions.count { it.completionDate == date }
                    if (completedCount < scheduledOnDate) missed++
                }
            }
            date = date.plusDays(1)
        }
        missed
    }
    val mostMissedDow = missCountByDow.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    val mostMissedWeekday = mostMissedDow?.let {
        DayOfWeek.of(it).name.lowercase().replaceFirstChar { c -> c.uppercase() }
    }

    // ── Best active streak (proxy for peak monthly consistency) ──
    val bestActiveStreak = habits.maxOfOrNull { it.currentStreak } ?: 0

    val reflection = buildReflectionSummary(completionPercent, trend, totalCompleted, strongestHabitName)

    return MonthlyAnalytics(
        completionPercent = completionPercent,
        totalCompleted = totalCompleted,
        totalScheduled = totalScheduled,
        strongestHabitName = strongestHabitName,
        mostMissedWeekday = mostMissedWeekday,
        bestActiveStreak = bestActiveStreak,
        trendVsPreviousMonth = trend,
        reflectionSummary = reflection
    )
}

/**
 * Detect lightweight behavioral patterns from the last 28 days.
 * Returns up to 3 patterns, sorted by priority (1 = most important).
 * Language is always observational and supportive — never diagnostic.
 */
private fun detectBehavioralPatterns(
    completions: List<HabitCompletionEntity>,
    habits: List<HabitEntity>,
    today: LocalDate
): List<BehavioralPattern> {
    val patterns = mutableListOf<BehavioralPattern>()
    val activeHabits = habits.filter { !it.paused }
    val recent = completions.filter { !it.completionDate.isBefore(today.minusDays(27)) }

    // Pattern 1: 7-day consistency window — all 7 days had at least one completion
    if (activeHabits.isNotEmpty()) {
        val last7 = (0..6).map { today.minusDays(it.toLong()) }
        val activeDays7 = last7.count { date -> recent.any { it.completionDate == date } }
        if (activeDays7 >= 7) {
            patterns.add(BehavioralPattern(
                message = "Seven consistent days — your rhythm is holding.",
                priority = 1
            ))
        }
    }

    // Pattern 2: Recovery — a gap of 4+ days followed by recent return
    if (recent.size >= 3) {
        val sortedDates = recent.map { it.completionDate }.distinct().sorted()
        var hadGap = false
        for (i in 1 until sortedDates.size) {
            if (ChronoUnit.DAYS.between(sortedDates[i - 1], sortedDates[i]).toInt() >= 4) {
                hadGap = true
                break
            }
        }
        val returnedRecently = recent.any { !it.completionDate.isBefore(today.minusDays(3)) }
        if (hadGap && returnedRecently) {
            patterns.add(BehavioralPattern(
                message = "After a quieter stretch, you found your way back — that counts.",
                priority = 1
            ))
        }
    }

    // Pattern 3: Most missed weekday (≥ 65% miss rate over last 4 weeks)
    // Uses per-date isScheduledOn guard — habits created mid-window are only counted
    // from their creation date, keeping miss-rate historically accurate.
    if (activeHabits.isNotEmpty() && recent.isNotEmpty()) {
        val missRateByDow = (1..7).associateWith { dow ->
            val daysOfType = (0..27).map { today.minusDays(it.toLong()) }
                .filter { it.dayOfWeek.value == dow }
            if (daysOfType.isEmpty()) return@associateWith 0f
            val missedDays = daysOfType.count { date ->
                // Only count days where at least one habit was actually scheduled
                val scheduledOnDate = activeHabits.count { h -> h.isScheduledOn(date) }
                if (scheduledOnDate == 0) return@count false
                val completed = recent.count { it.completionDate == date }
                completed < scheduledOnDate
            }
            missedDays.toFloat() / daysOfType.size.toFloat()
        }
        val worstEntry = missRateByDow.maxByOrNull { it.value }
        if (worstEntry != null && worstEntry.value >= 0.65f) {
            val dayName = DayOfWeek.of(worstEntry.key).name
                .lowercase().replaceFirstChar { it.uppercase() }
            patterns.add(BehavioralPattern(
                message = "${dayName}s tend to slip by a little more — worth knowing.",
                priority = 2
            ))
        }
    }

    // Pattern 4: Focused list advantage — short habit list with high recent completion
    if (activeHabits.size in 1..4) {
        val last7Scheduled = activeHabits.sumOf { h ->
            habitScheduledInRange(h, today.minusDays(6), today)
        }
        val last7Completed = recent.count { !it.completionDate.isBefore(today.minusDays(6)) }
        val rate = if (last7Scheduled > 0) last7Completed.toFloat() / last7Scheduled else 0f
        if (rate >= 0.75f) {
            patterns.add(BehavioralPattern(
                message = "A focused habit list is working well — consistency stays high.",
                priority = 2
            ))
        }
    }

    // Pattern 5: Quiet habits — active but no completions in 14 days
    val quietHabits = activeHabits.filter { habit ->
        val hasRecentCompletion = recent.any { c ->
            c.habitId == habit.id && !c.completionDate.isBefore(today.minusDays(13))
        }
        !hasRecentCompletion && habit.createdDate.isBefore(today.minusDays(7))
    }
    when (quietHabits.size) {
        1 -> patterns.add(BehavioralPattern(
            message = "${quietHabits[0].name} has been quiet lately — worth a revisit when ready.",
            priority = 3
        ))
        in 2..Int.MAX_VALUE -> patterns.add(BehavioralPattern(
            message = "A few habits have been quiet lately — no pressure, just a gentle note.",
            priority = 3
        ))
    }

    // ── Sprint 3B: Energy / Load patterns ─────────────────────────────────────
    // Phrased as soft possibility — never as advice, never implying the app "knows" the user.

    // Pattern 6: Rapid habit addition — 3+ habits created in last 7 days
    // Cross-reference with recent completion rate to see if load correlates with dip.
    val newHabitsThisWeek = activeHabits.count { habit ->
        !habit.createdDate.isBefore(today.minusDays(6))
    }
    if (newHabitsThisWeek >= 3) {
        val last7Completed = recent.count { !it.completionDate.isBefore(today.minusDays(6)) }
        val last7Scheduled = activeHabits.sumOf { h ->
            habitScheduledInRange(h, today.minusDays(6), today)
        }
        val recentRate = if (last7Scheduled > 0) last7Completed.toFloat() / last7Scheduled else 1f
        if (recentRate < 0.55f) {
            patterns.add(BehavioralPattern(
                message = "Adding several habits at once can sometimes spread focus — smaller steps tend to hold steadier.",
                priority = 3
            ))
        }
    }

    // Pattern 7: Steep consistency drop — this 7-day window significantly worse than prior 7
    if (activeHabits.isNotEmpty()) {
        val last7Completed = recent.count { !it.completionDate.isBefore(today.minusDays(6)) }
        val prior7Completed = recent.count {
            it.completionDate.isBefore(today.minusDays(6)) &&
            !it.completionDate.isBefore(today.minusDays(13))
        }
        val last7Scheduled = activeHabits.sumOf { h ->
            habitScheduledInRange(h, today.minusDays(6), today)
        }
        val prior7Scheduled = activeHabits.sumOf { h ->
            habitScheduledInRange(h, today.minusDays(13), today.minusDays(7))
        }
        val recentRate = if (last7Scheduled > 0) last7Completed.toFloat() / last7Scheduled else 1f
        val priorRate = if (prior7Scheduled > 0) prior7Completed.toFloat() / prior7Scheduled else 1f
        if (priorRate - recentRate >= 0.3f && priorRate >= 0.5f) {
            // Only fire if prior period was reasonably consistent (not recovering from a gap)
            patterns.add(BehavioralPattern(
                message = "Routines seem to flow more easily with a focused, smaller list.",
                priority = 3
            ))
        }
    }

    return patterns.sortedBy { it.priority }.take(3)
}

/**
 * Compute goal health state from existing progress details and goal metadata.
 * When a deadline exists: compares completion % to time-elapsed %.
 * When no deadline: evaluates on completion % alone.
 */
private fun computeGoalHealth(
    goal: GoalEntity,
    progressDetails: GoalProgressDetails?,
    today: LocalDate
): GoalHealthState {
    val percent = progressDetails?.overallPercent ?: 0
    val deadline = goal.deadline

    val momentum: GoalMomentum
    val likelihood: String
    val velocityLabel: String

    if (deadline == null || deadline.isBefore(today)) {
        // No active deadline — evaluate on completion % only
        momentum = when {
            percent >= 75 -> GoalMomentum.STRONG
            percent >= 40 -> GoalMomentum.ON_TRACK
            percent >= 15 -> GoalMomentum.SLOW
            else          -> GoalMomentum.NEEDS_ATTENTION
        }
        likelihood = when (momentum) {
            GoalMomentum.STRONG          -> "Looking strong"
            GoalMomentum.ON_TRACK        -> "Good progress"
            GoalMomentum.SLOW            -> "A bit more consistency helps"
            GoalMomentum.NEEDS_ATTENTION -> "Ready when you are"
        }
        velocityLabel = when (momentum) {
            GoalMomentum.STRONG          -> "Strong momentum"
            GoalMomentum.ON_TRACK        -> "Steady pace"
            GoalMomentum.SLOW            -> "Slow progress"
            GoalMomentum.NEEDS_ATTENTION -> "Getting started"
        }
    } else {
        // Active deadline — compare progress % to time elapsed %
        val totalDays = ChronoUnit.DAYS.between(goal.startDate, deadline).toInt().coerceAtLeast(1)
        val elapsedDays = ChronoUnit.DAYS.between(goal.startDate, today)
            .coerceIn(0, totalDays.toLong()).toInt()
        val timeElapsedPercent = (elapsedDays.toFloat() / totalDays.toFloat() * 100).toInt()
        val daysRemaining = ChronoUnit.DAYS.between(today, deadline).coerceAtLeast(0).toInt()

        momentum = when {
            percent >= 100                              -> GoalMomentum.STRONG
            daysRemaining <= 5 && percent < 80         -> GoalMomentum.NEEDS_ATTENTION
            percent >= timeElapsedPercent + 10         -> GoalMomentum.STRONG
            percent >= timeElapsedPercent - 15         -> GoalMomentum.ON_TRACK
            percent < timeElapsedPercent - 30          -> GoalMomentum.NEEDS_ATTENTION
            else                                       -> GoalMomentum.SLOW
        }
        likelihood = when (momentum) {
            GoalMomentum.STRONG          -> "Likely to finish on time"
            GoalMomentum.ON_TRACK        -> "On pace for the deadline"
            GoalMomentum.SLOW            -> "Current pace may need a nudge"
            GoalMomentum.NEEDS_ATTENTION -> "Deadline is approaching"
        }
        velocityLabel = when (momentum) {
            GoalMomentum.STRONG          -> "Strong momentum"
            GoalMomentum.ON_TRACK        -> "Steady pace"
            GoalMomentum.SLOW            -> "Slow progress"
            GoalMomentum.NEEDS_ATTENTION -> "Needs attention"
        }
    }

    return GoalHealthState(
        goalId = goal.goalId,
        momentum = momentum,
        completionLikelihood = likelihood,
        velocityLabel = velocityLabel
    )
}

/**
 * Upgraded monthly reflection summary with category-aware phrasing and deeper emotional nuance.
 * Category detection is lightweight — keyword matching on habit names, not taxonomy.
 * Tone: calm, editorial, observational. Never chatbot, never corporate.
 */
private fun buildReflectionSummary(
    completionPercent: Int,
    trend: Int,
    totalCompleted: Int,
    strongestHabit: String?
): String {
    if (totalCompleted == 0) return "A quieter start — patterns will emerge with time."

    // Soft category detection from strongest habit name
    val habitCategory = strongestHabit?.lowercase()?.let { name ->
        when {
            name.containsAny("walk", "run", "gym", "exercise", "yoga", "stretch", "workout") -> "health"
            name.containsAny("meditat", "breath", "journal", "gratitude", "reflect") -> "mindfulness"
            name.containsAny("read", "learn", "study", "practice", "skill") -> "growth"
            name.containsAny("sleep", "wake", "morning", "evening", "night") -> "rhythm"
            else -> null
        }
    }

    return when {
        // High completion + improving trend
        completionPercent >= 85 && trend > 5 -> when (habitCategory) {
            "health"      -> "Health routines stayed especially steady this month."
            "mindfulness" -> "A calm, consistent month — the quieter habits held firm."
            "growth"      -> "Learning routines kept a strong rhythm through the month."
            else          -> "This month kept a strong, steady rhythm."
        }

        // High completion, stable
        completionPercent >= 85 -> when (habitCategory) {
            "health"  -> "Physical routines stayed consistent — that foundation is real."
            "rhythm"  -> "Daily rhythm stayed grounded — a quiet kind of stability."
            else      -> "Consistency stayed high — a solid foundation."
        }

        // Good completion + improving
        completionPercent >= 65 && trend > 10 ->
            "Consistency recovered steadily after a slower start."

        // Good completion, stable
        completionPercent >= 65 -> when (habitCategory) {
            "mindfulness" -> "A grounded month — more presence than absence."
            else          -> "A balanced month — more days on than off."
        }

        // Notable upward trend
        trend > 15 -> "Things picked up noticeably from last month — momentum is building."

        // Decline — framed gently, never as failure
        trend < -15 -> "A lighter month — sometimes stepping back creates space to return stronger."

        // Named habit anchor
        strongestHabit != null && completionPercent >= 40 ->
            "$strongestHabit held the most consistent thread through the month."

        // Gentle default
        else -> "Small, steady steps — they add up more than they seem."
    }
}

/** Extension helper for multi-keyword matching — keeps when-branches readable. */
private fun String.containsAny(vararg keywords: String) = keywords.any { contains(it) }

/**
 * Merges base insights + behavioral patterns + monthly trend + rhythm observations
 * into a single semantically-deduplicated, priority-ordered list capped at 5 items.
 *
 * Stickiness rule (Sprint 3B):
 *  - High-priority candidates (priority ≤ 2) are always included first.
 *  - Lower-priority candidates (priority ≥ 3) rotate daily using today's epoch day as seed.
 *  This prevents important observations from being buried, while keeping the lower feed
 *  from feeling repetitive across days without feeling random.
 */
private fun buildPrioritizedInsights(
    baseInsights: List<String>,
    patterns: List<BehavioralPattern>,
    monthly: MonthlyAnalytics?,
    rhythm: RhythmInsight?
): List<String> {
    data class Candidate(val message: String, val priority: Int)

    val candidates = mutableListOf<Candidate>()

    // Base weekly insights: priority 3+ (existing behavioral stats)
    baseInsights.forEachIndexed { i, s -> candidates.add(Candidate(s, 3 + i)) }

    // Behavioral patterns: their own priority (1–3)
    patterns.forEach { p -> candidates.add(Candidate(p.message, p.priority)) }

    // Monthly trend signal: priority 2 if meaningful shift (>= 10% delta)
    monthly?.let { m ->
        if (kotlin.math.abs(m.trendVsPreviousMonth) >= 10) {
            val msg = if (m.trendVsPreviousMonth > 0)
                "Consistency improved by ${m.trendVsPreviousMonth}% from last month."
            else
                "This month ran a little quieter than last — that's okay."
            candidates.add(Candidate(msg, 2))
        }
    }

    // Rhythm observations: priority 2 (observational, high quality)
    rhythm?.observations?.forEach { obs ->
        candidates.add(Candidate(obs, 2))
    }

    val sorted = candidates.sortedBy { it.priority }

    // Stickiness: high-priority items (≤ 2) always lead and are not rotated.
    val highPriority = sorted.filter { it.priority <= 2 }
    val lowPriority = sorted.filter { it.priority > 2 }

    // Date-seed rotation on low-priority pool — same day = same order (feels stable),
    // different day = gently shifted (feels alive). Not random — deterministic.
    val rotatedLow = if (lowPriority.size > 1) {
        val offset = (LocalDate.now().toEpochDay() % lowPriority.size).toInt()
        lowPriority.drop(offset) + lowPriority.take(offset)
    } else lowPriority

    val combined = highPriority + rotatedLow

    // Semantic deduplication — prevents two insights about the same theme
    val result = mutableListOf<String>()
    val usedKeys = mutableSetOf<String>()
    for (c in combined) {
        val key = semanticInsightKey(c.message)
        if (key !in usedKeys) {
            result.add(c.message)
            usedKeys.add(key)
            if (result.size >= 5) break
        }
    }

    return result
}

/**
 * Assigns a semantic category key to an insight message.
 * Messages in the same category are treated as duplicates — only the highest-priority
 * one is kept. This prevents two weekday observations, two streak mentions, etc.
 */
private fun semanticInsightKey(msg: String): String {
    val lower = msg.lowercase()
    // Weekday mentions — each weekday is its own category
    listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        .forEach { day -> if (lower.contains(day)) return "weekday_$day" }
    // Thematic categories
    if (lower.contains("streak"))                               return "streak"
    if (lower.contains("seven") && lower.contains("day"))       return "seven_day_rhythm"
    if (lower.contains("week") && lower.contains("complet"))    return "weekly_completion"
    if (lower.contains("perfect"))                              return "perfect_days"
    if (lower.contains("improv") || lower.contains("quieter"))  return "monthly_trend"
    if (lower.contains("found your way") || lower.contains("quieter stretch")) return "recovery"
    if (lower.contains("focused") || lower.contains("shorter")) return "list_size"
    if (lower.contains("quiet") && lower.contains("habit"))     return "quiet_habit"
    if (lower.contains("consist") || lower.contains("rhythm"))  return "consistency"
    // Fallback: use first 40 chars as loose key
    return lower.take(40)
}
