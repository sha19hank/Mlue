package com.mlue.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mlue.app.data.HabitDao
import com.mlue.app.data.HabitEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(
    private val context: Context,
    private val habitDao: HabitDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules (or cancels) alarms for ALL habits.
     * Call on: app start, boot recovery, bulk updates.
     */
    suspend fun scheduleAll() {
        val habits = habitDao.getHabitsOnce()
        habits.forEach { scheduleHabit(it) }
    }

    /**
     * Cancels all currently scheduled habit reminder alarms.
     * Call before a full teardown/re-schedule to avoid duplicates.
     */
    suspend fun cancelAll() {
        val habits = habitDao.getHabitsOnce()
        habits.forEach { cancelHabit(it.id) }
    }

    /**
     * Schedules the next alarm for a single habit, or cancels it if reminders
     * are disabled or the habit is paused.
     *
     * Each habit uses its own unique PendingIntent (request code = habit.id.toInt())
     * so habits scheduled at the same time do NOT overwrite each other.
     */
    fun scheduleHabit(habit: HabitEntity) {
        if (!habit.reminderEnabled || habit.reminderTime == null || habit.paused) {
            cancelHabit(habit.id)
            return
        }

        val nextTime = nextOccurrence(habit, LocalDate.now(), habit.reminderTime)
        val triggerAtMillis = nextTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Always available below API 31
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(ReminderReceiver.EXTRA_HABIT_NAME, habit.name)
            putExtra(ReminderReceiver.EXTRA_TARGET_TIME_MILLIS, triggerAtMillis)
            putExtra(ReminderReceiver.EXTRA_CAN_SCHEDULE_EXACT, canScheduleExact)
            putExtra(ReminderReceiver.EXTRA_SCHEDULING_METHOD, if (canScheduleExact) "setExactAndAllowWhileIdle" else "setAndAllowWhileIdle")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            // Request code is habit.id.toInt(). Since habit ID is a unique Long and reminders are exactly 1 per habit,
            // this guarantees a stable and unique ID that cleanly replaces any old instances via FLAG_UPDATE_CURRENT,
            // entirely eliminating "ghost alarms" from time edits without hash collisions.
            habit.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        android.util.Log.d("MlueReminder", "Scheduling habitId=${habit.id} target=$nextTime ms=$triggerAtMillis method=${if(canScheduleExact) "exact" else "inexact"}")
        setAlarm(triggerAtMillis, pendingIntent, canScheduleExact)
    }

    /**
     * Cancels the alarm for a specific habit by ID.
     * Safe to call even if no alarm was scheduled for this habit.
     */
    fun cancelHabit(habitId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
        }
        // FLAG_NO_CREATE: returns null if no matching PendingIntent exists (avoids ghost creation)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return // Nothing to cancel

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Chooses alarm method based on Android version and available permissions.
     * - Android 12+ with SCHEDULE_EXACT_ALARM: uses setExactAndAllowWhileIdle (precise)
     * - Fallback: uses setAndAllowWhileIdle (may be delayed by Doze, but reliable enough)
     */
    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent, canScheduleExact: Boolean) {

        if (canScheduleExact) {
            android.util.Log.d("MlueReminder", "Using exact scheduling (setExactAndAllowWhileIdle)")
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            // Graceful fallback: inexact but still Doze-exempt
            android.util.Log.w("MlueReminder", "Fallback to inexact scheduling (setAndAllowWhileIdle). Exact permission denied/unavailable.")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Computes the next DateTime at which this habit's reminder should fire.
     * If the scheduled time for today has already passed, advances to the next
     * valid scheduled day (within a 7-day lookahead).
     */
    private fun nextOccurrence(habit: HabitEntity, today: LocalDate, time: LocalTime): LocalDateTime {
        val scheduledDays = habit.scheduledDays
        var candidate = LocalDateTime.of(today, time)

        // Defensive guard: if today's slot has already passed, start searching from tomorrow.
        // We use a 10-second tolerance (plusSeconds(10)) against LocalDateTime.now() to ensure that 
        // if an alarm fires perfectly on time (or milliseconds early), we don't accidentally evaluate
        // it as "in the future" and schedule an immediate double-fire loop.
        if (!candidate.isAfter(LocalDateTime.now().plusSeconds(10))) {
            candidate = LocalDateTime.of(today.plusDays(1), time)
        }

        // If no day constraints, fire on the next available slot
        if (scheduledDays.isEmpty()) return candidate

        // Find the next matching weekday within a 7-day window
        repeat(7) {
            val dayValue = candidate.toLocalDate().dayOfWeek.value
            if (scheduledDays.contains(dayValue)) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}
