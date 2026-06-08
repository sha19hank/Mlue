package com.mlue.app.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mlue.app.MainActivity
import com.mlue.app.R
import com.mlue.app.data.HabitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return

        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (intent.getStringExtra(EXTRA_HABIT_NAME) == null) return
        if (habitId <= 0L) return

        android.util.Log.d("MlueReminder", "ReminderReceiver: onReceive for habitId=$habitId at ${java.time.LocalDateTime.now()}")

        val targetTimeMillis = intent.getLongExtra(EXTRA_TARGET_TIME_MILLIS, -1L)
        val schedulingMethod = intent.getStringExtra(EXTRA_SCHEDULING_METHOD) ?: "unknown"
        val canScheduleExact = intent.getBooleanExtra(EXTRA_CAN_SCHEDULE_EXACT, false)

        if (targetTimeMillis != -1L) {
            val drift = System.currentTimeMillis() - targetTimeMillis
            android.util.Log.i(
                "MlueReminder",
                "DIAGNOSTIC: drift=${drift}ms | method=$schedulingMethod | exact_perm=$canScheduleExact | SDK=${android.os.Build.VERSION.SDK_INT} | OEM=${android.os.Build.MANUFACTURER} | Model=${android.os.Build.MODEL}"
            )
        }

        // goAsync() keeps the BroadcastReceiver alive past onReceive() return
        // while we do IO work, avoiding main-thread blocking / ANR risk
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = HabitDatabase.build(context).habitDao()
                val habit = dao.getHabitById(habitId)

                // Double-check: don't notify if habit was deleted, paused, or disabled
                if (habit == null || !habit.reminderEnabled || habit.paused) {
                    pendingResult.finish()
                    return@launch
                }

                // WS4: Smart Notification Suppression
                // Do not notify if the habit is already completed today.
                // We check the DB directly to be robust against partial completions or multiple events.
                val todayStr = java.time.LocalDate.now().toString()
                if (dao.hasCompletionForDate(habitId, todayStr) > 0) {
                    android.util.Log.d("MlueReminder", "Reminder suppressed: Habit $habitId already completed today.")
                    pendingResult.finish()
                    return@launch
                }

                // Build tap-to-open action — opens app on notification tap
                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val tapPendingIntent = PendingIntent.getActivity(
                    context,
                    habitId.toInt(),
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(habit.name)
                    .setContentText(context.getString(R.string.reminder_body))
                    .setAutoCancel(true)
                    .setContentIntent(tapPendingIntent)
                    // PRIORITY_HIGH ensures delivery through Doze and OEM battery savers
                    // without being "loud" — channel importance controls the actual UX
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(longArrayOf(0, 150, 100, 150)) // Subtle double-tap vibration
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .build()

                NotificationManagerCompat.from(context).notify(habitId.toInt(), notification)

                // Reschedule the next occurrence for this habit
                ReminderScheduler(context, dao).scheduleHabit(habit)
            } catch (e: Exception) {
                android.util.Log.e("MlueReminder", "ReminderReceiver: Caught exception during execution", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        // Explicit action ensures only intentional broadcasts trigger this receiver
        const val ACTION_REMINDER = "com.mlue.app.ACTION_HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_HABIT_NAME = "extra_habit_name"
        const val EXTRA_TARGET_TIME_MILLIS = "extra_target_time_millis"
        const val EXTRA_SCHEDULING_METHOD = "extra_scheduling_method"
        const val EXTRA_CAN_SCHEDULE_EXACT = "extra_can_schedule_exact"
        const val CHANNEL_ID = "habit_reminders"
    }
}
