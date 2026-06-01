package com.mlue.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class HabitRepository(
    private val database: HabitDatabase,
    private val dao: HabitDao,
    private val goalDao: GoalDao,
    private val journalDao: JournalDao,
    private val settings: SettingsRepository
) {
    fun getHabits(): Flow<List<HabitEntity>> = dao.getHabits()

    fun getGoals(): Flow<List<GoalEntity>> = goalDao.getGoals()

    fun getAllCompletionsFlow(): Flow<List<HabitCompletionEntity>> = dao.getAllCompletionsFlow()

    suspend fun getGoalsOnce(): List<GoalEntity> = goalDao.getGoalsOnce()

    suspend fun addGoal(
        title: String,
        description: String?,
        startDate: LocalDate,
        deadline: LocalDate?
    ): Long {
        val goal = GoalEntity(
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            startDate = startDate,
            deadline = deadline
        )
        return goalDao.insertGoal(goal)
    }

    suspend fun updateGoal(goal: GoalEntity) {
        goalDao.updateGoal(goal)
    }

    suspend fun markGoalCompleted(goalId: Long, completed: Boolean, date: LocalDate?) {
        goalDao.markGoalCompleted(goalId, completed, date?.toString())
    }

    suspend fun deleteGoal(goal: GoalEntity) {
        database.withTransaction {
            dao.clearGoalIdForHabits(goal.goalId)
            goalDao.deleteGoal(goal)
        }
    }

    fun getJournalEntries(): Flow<List<JournalEntryEntity>> = journalDao.getEntries()

    suspend fun getJournalEntriesByDate(date: LocalDate): List<JournalEntryEntity> {
        return journalDao.getEntriesByDate(date.toString())
    }

    suspend fun upsertJournalEntry(
        title: String,
        body: String,
        date: LocalDate,
        mood: String?,
        color: Int = 0
    ) {
        val entry = JournalEntryEntity(
            title = title.trim(),
            body = body.trim(),
            date = date,
            mood = mood?.trim()?.ifBlank { null },
            color = color
        )
        journalDao.upsertEntry(entry)
    }

    suspend fun updateJournalEntry(entry: JournalEntryEntity) {
        journalDao.updateEntry(entry)
    }

    suspend fun deleteJournalEntry(entry: JournalEntryEntity) {
        journalDao.deleteEntry(entry)
    }

    suspend fun addHabit(
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
    ): Long {
        val habit = HabitEntity(
            name = name.trim(),
            description = description?.trim(),
            category = category?.trim()?.ifBlank { null },
            color = color,
            scheduledDays = scheduledDays,
            reminderEnabled = reminderEnabled,
            reminderTime = reminderTime,
            paused = paused,
            stepEnabled = stepEnabled,
            stepGoal = stepGoal,
            goalId = goalId
        )
        return dao.insertHabit(habit)
    }

    suspend fun markCompleted(staleHabit: HabitEntity, today: LocalDate): Int? {
        return database.withTransaction {
            val habit = dao.getHabitById(staleHabit.id) ?: return@withTransaction null
            
            if (!habit.isScheduledOn(today)) return@withTransaction null
            if (habit.lastCompletedDate == today) return@withTransaction null
            if (habit.lastCompletedDate != null && habit.lastCompletedDate.isAfter(today)) return@withTransaction null

            val completedToday = dao.hasCompletionForDate(habit.id, today.toString()) > 0
            if (completedToday) return@withTransaction null

            val lastCompleted = habit.lastCompletedDate
            val previousScheduled = previousScheduledDate(habit, today)
            val newStreak = if (lastCompleted != null && previousScheduled != null && lastCompleted == previousScheduled) {
                habit.currentStreak + 1
            } else {
                1
            }
            val longest = maxOf(habit.longestStreak, newStreak)
            val tokenAward = milestoneTokenAward(newStreak)
            val alreadyAwarded = settings.hasAwardedTokenForHabitOnDate(habit.id, today)

            val milestoneTrigger = newStreak > habit.highestCelebratedMilestone && newStreak in listOf(3, 7, 14, 30, 50, 100)
            val updatedMilestone = if (milestoneTrigger) newStreak else habit.highestCelebratedMilestone

            val token = if (tokenAward > 0 && !alreadyAwarded) dao.getTokenOnce() ?: TokenEntity() else null
            dao.updateHabit(
                habit.copy(
                    lastCompletedDate = today,
                    currentStreak = newStreak,
                    longestStreak = longest,
                    highestCelebratedMilestone = updatedMilestone
                )
            )
            dao.insertCompletion(HabitCompletionEntity(habitId = habit.id, completionDate = today))
            if (tokenAward > 0 && token != null && !alreadyAwarded) {
                dao.upsertToken(token.copy(count = token.count + tokenAward))
            }
            
            if (tokenAward > 0 && !alreadyAwarded) {
                settings.setAwardedTokenForHabitOnDate(habit.id, today)
            }
            settings.clearPreviousStreak(habit.id)
            
            if (milestoneTrigger) newStreak else null
        }
    }

    suspend fun markUncompleted(staleHabit: HabitEntity, today: LocalDate) {
        database.withTransaction {
            val habit = dao.getHabitById(staleHabit.id) ?: return@withTransaction
            
            val completedToday = dao.hasCompletionForDate(habit.id, today.toString()) > 0
            if (!completedToday) return@withTransaction

            dao.deleteCompletionForDate(habit.id, today.toString())
            
            val completions = dao.getCompletionsForHabitDesc(habit.id)
            val previousCompletedDate = completions.firstOrNull { it.completionDate.isBefore(today) }?.completionDate
            
            val newStreak = maxOf(0, habit.currentStreak - 1)
            
            dao.updateHabit(
                habit.copy(
                    lastCompletedDate = previousCompletedDate,
                    currentStreak = newStreak
                )
            )
        }
    }

    suspend fun refreshBrokenStreaks(today: LocalDate) {
        val habits = dao.getHabits().first()
        habits.forEach { habit ->
            val lastCompleted = habit.lastCompletedDate
            if (habit.paused) return@forEach
            if (lastCompleted != null && habit.currentStreak > 0 && missedScheduledDay(habit, lastCompleted, today)) {
                if (settings.isStreakFrozen(habit.id)) {
                    settings.setStreakFrozen(habit.id, false)
                    val missedDay = previousScheduledDate(habit, today) ?: today.minusDays(1)
                    dao.updateHabit(habit.copy(lastCompletedDate = missedDay))
                } else {
                    settings.setPreviousStreak(habit.id, habit.currentStreak)
                    dao.updateHabit(habit.copy(currentStreak = 0))
                }
            }
        }
    }

    suspend fun buyStreakFreeze(habitId: Long): Boolean {
        if (settings.isStreakFrozen(habitId)) return false
        var success = false
        database.withTransaction {
            val token = dao.getTokenOnce() ?: TokenEntity()
            if (token.count > 0) {
                dao.upsertToken(token.copy(count = token.count - 1))
                success = true
            }
        }
        if (!success) return false
        settings.setStreakFrozen(habitId, true)
        return true
    }

    suspend fun updateHabit(habit: HabitEntity) {
        dao.updateHabit(habit)
    }

    suspend fun updateHighestCelebratedMilestone(habitId: Long, milestone: Int) {
        dao.updateHighestCelebratedMilestone(habitId, milestone)
    }

    suspend fun pauseHabit(habit: HabitEntity, paused: Boolean) {
        if (paused) {
            settings.setPausedStreak(habit.id, habit.currentStreak)
            dao.updateHabit(habit.copy(paused = true))
        } else {
            val previous = settings.getPausedStreak(habit.id) ?: habit.currentStreak
            dao.updateHabit(habit.copy(paused = false, currentStreak = previous))
            settings.clearPausedStreak(habit.id)
        }
    }

    suspend fun deleteHabit(habit: HabitEntity) {
        dao.deleteHabit(habit)
    }

    suspend fun getHabitsOnce(): List<HabitEntity> = dao.getHabitsOnce()

    suspend fun weeklySummary(start: LocalDate, end: LocalDate): List<DateCount> {
        return dao.getCompletionCounts(start.toString(), end.toString())
    }

    suspend fun monthlyCompletionCount(start: LocalDate, end: LocalDate): Int {
        return dao.getCompletionCountInRange(start.toString(), end.toString())
    }

    suspend fun completionCountForHabit(start: LocalDate, end: LocalDate, habitId: Long): Int {
        return dao.getCompletionCountForHabitInRange(habitId, start.toString(), end.toString())
    }

    suspend fun completedHabitIdsForDate(date: LocalDate): List<Long> {
        return dao.getCompletedHabitIdsForDate(date.toString())
    }

    fun tokenFlow(): Flow<TokenEntity?> = dao.getTokenFlow()

    private fun milestoneTokenAward(streak: Int): Int {
        var base = 1 // 1 token per completion
        if (streak % 7 == 0 && streak > 0) base += 5
        if (streak % 30 == 0 && streak > 0) base += 15
        if (streak % 100 == 0 && streak > 0) base += 50
        return base
    }

    suspend fun goalProgressPercent(goal: GoalEntity, today: LocalDate): Int {
        val details = goalProgressDetails(goal, today)
        return details.overallPercent
    }

    suspend fun goalProgressDetails(goal: GoalEntity, today: LocalDate): GoalProgressDetails {
        val habits = dao.getHabitsByGoalId(goal.goalId)
        if (habits.isEmpty()) return GoalProgressDetails(0, emptyList())

        val startDate = goal.startDate
        val habitProgresses = habits.map { habit ->
            val totalOccurrences = scheduledOccurrencesInRange(habit, startDate, goal.deadline ?: today)
            val completed = if (totalOccurrences > 0) {
                dao.getCompletionCountForHabitInRange(
                    habit.id,
                    startDate.toString(),
                    today.toString()
                )
            } else 0
            
            val percent = if (totalOccurrences > 0) {
                (completed.toDouble() / totalOccurrences.toDouble() * 100).toInt().coerceIn(0, 100)
            } else 0
            
            HabitProgress(habit, completed, totalOccurrences, percent)
        }

        val totalPercent = habitProgresses.sumOf { it.percent.toDouble() / 100.0 }
        val average = (totalPercent / habits.size.toDouble()).coerceIn(0.0, 1.0)
        return GoalProgressDetails((average * 100).toInt(), habitProgresses)
    }

    fun getGoalProgressMapFlow(today: LocalDate = LocalDate.now()): Flow<Map<Long, GoalProgressDetails>> {
        return combine(
            goalDao.getGoals(),
            dao.getHabits(),
            dao.getAllCompletionsFlow()
        ) { goals, habits, completions ->
            goals.associate { goal ->
                val goalHabits = habits.filter { it.goalId == goal.goalId }
                goal.goalId to calculateGoalProgress(goal, goalHabits, completions, today)
            }
        }.distinctUntilChanged()
    }

    private fun calculateGoalProgress(
        goal: GoalEntity,
        habits: List<HabitEntity>,
        completions: List<HabitCompletionEntity>,
        today: LocalDate
    ): GoalProgressDetails {
        if (habits.isEmpty()) return GoalProgressDetails(0, emptyList())

        val startDate = goal.startDate
        val endDate = goal.deadline ?: today
        
        val habitProgresses = habits.map { habit ->
            val totalOccurrences = scheduledOccurrencesInRange(habit, startDate, endDate)
            
            val completed = if (totalOccurrences > 0) {
                completions.count {
                    it.habitId == habit.id && 
                    !it.completionDate.isBefore(startDate) &&
                    !it.completionDate.isAfter(today)
                }
            } else 0
            
            val percent = if (totalOccurrences > 0) {
                (completed.toDouble() / totalOccurrences.toDouble() * 100).toInt().coerceIn(0, 100)
            } else 0
            
            HabitProgress(habit, completed, totalOccurrences, percent)
        }

        val totalPercent = habitProgresses.sumOf { it.percent.toDouble() / 100.0 }
        val average = (totalPercent / habits.size.toDouble()).coerceIn(0.0, 1.0)
        return GoalProgressDetails((average * 100).toInt(), habitProgresses)
    }

    private fun scheduledOccurrencesInRange(
        habit: HabitEntity,
        start: LocalDate,
        end: LocalDate
    ): Int {
        if (end.isBefore(start)) return 0
        val daysBetween = ChronoUnit.DAYS.between(start, end).toInt()

        var count = 0
        var date = start
        repeat(daysBetween + 1) {
            if (habit.isScheduledOn(date)) count += 1
            date = date.plusDays(1)
        }
        return count
    }

    private fun previousScheduledDate(habit: HabitEntity, today: LocalDate): LocalDate? {
        var date = today.minusDays(1)
        repeat(7) {
            if (habit.isScheduledOn(date)) return date
            date = date.minusDays(1)
        }
        return null
    }

    private fun missedScheduledDay(habit: HabitEntity, lastCompleted: LocalDate, today: LocalDate): Boolean {
        val yesterday = today.minusDays(1)
        if (lastCompleted.isAfter(yesterday) || lastCompleted == yesterday) return false

        val daysBetween = ChronoUnit.DAYS.between(lastCompleted, yesterday).toInt()
        for (i in 1..daysBetween) {
            val date = lastCompleted.plusDays(i.toLong())
            if (habit.isScheduledOn(date)) return true
        }
        return false
    }
}

data class HabitProgress(
    val habit: HabitEntity,
    val completed: Int,
    val expected: Int,
    val percent: Int
)

data class GoalProgressDetails(
    val overallPercent: Int,
    val habitProgresses: List<HabitProgress>
)
