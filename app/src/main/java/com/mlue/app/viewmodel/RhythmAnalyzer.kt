package com.mlue.app.viewmodel

import com.mlue.app.data.HabitCompletionEntity
import com.mlue.app.data.HabitEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Lightweight rhythm detection — pure functions, no IO, no suspend.
 * Runs inside combine() lambdas on background dispatchers.
 *
 * Philosophy:
 *  - Minimum data thresholds prevent false patterns on sparse data.
 *  - Language is always soft possibility ("seem", "tend") — never prescriptive.
 *  - Observations are about the user's behavior, not about the user as a person.
 *  - Max 2 observations to respect visual restraint on the Insights screen.
 *
 * Sprint 3B: Adaptive Intelligence Layer
 */

/**
 * Detect rhythm observations from completions and habits over the last 28 days.
 * Returns null (not an empty RhythmInsight) when data is insufficient to avoid
 * showing noise observations that would feel random or untrue.
 *
 * Minimum thresholds:
 *  - At least 14 days of history (habit older than 14 days)
 *  - At least 10 total completions in the window
 *  - At least 1 active, non-paused habit
 */
internal fun detectRhythm(
    completions: List<HabitCompletionEntity>,
    habits: List<HabitEntity>,
    today: LocalDate
): RhythmInsight? {
    val activeHabits = habits.filter { !it.paused }
    if (activeHabits.isEmpty()) return null

    // Only habits with at least 14 days of history contribute
    val establishedHabits = activeHabits.filter {
        ChronoUnit.DAYS.between(it.createdDate, today) >= 14
    }
    if (establishedHabits.isEmpty()) return null

    val recent = completions.filter { !it.completionDate.isBefore(today.minusDays(27)) }
    if (recent.size < 10) return null

    val observations = mutableListOf<String>()

    // ── Observation 1: Weekday cluster ─────────────────────────────────────────
    // Find the 3-weekday window with the highest average completion rate.
    // Only surface if the best window is notably stronger than the weakest (≥ 25% gap).
    weekdayClusterObservation(recent, establishedHabits, today)
        ?.let { observations.add(it) }

    // ── Observation 2: Recovery speed ──────────────────────────────────────────
    // If the user typically returns within 1–2 days after a miss, note this softly.
    if (observations.size < 2) {
        recoverySpeedObservation(recent, today)
            ?.let { observations.add(it) }
    }

    // ── Observation 3: Load sustainability (fallback if earlier didn't fire) ───
    if (observations.size < 2) {
        loadObservation(recent, establishedHabits, today)
            ?.let { observations.add(it) }
    }

    return if (observations.isEmpty()) null else RhythmInsight(observations.take(2))
}

/**
 * Detects a midweek or end-of-week clustering pattern.
 * Only fires when the top-3 weekday cluster is ≥ 25% better than the bottom-2,
 * which ensures observations feel meaningfully true rather than marginal noise.
 */
private fun weekdayClusterObservation(
    recent: List<HabitCompletionEntity>,
    habits: List<HabitEntity>,
    today: LocalDate
): String? {
    // Completion count by day of week
    val completedByDow = (1..7).associateWith { dow ->
        recent.count { it.completionDate.dayOfWeek.value == dow }
    }
    // Scheduled count by day of week (sum of habits scheduled on each specific past day)
    val scheduledByDow = (1..7).associateWith { dow ->
        (0..27).sumOf { offset ->
            val date = today.minusDays(offset.toLong())
            if (date.dayOfWeek.value == dow) {
                habits.count { h -> h.isScheduledOn(date) }
            } else 0
        }
    }

    // Rate per day-of-week, skipping days with zero scheduled
    val rateByDow = (1..7).mapNotNull { dow ->
        val scheduled = scheduledByDow[dow] ?: 0
        if (scheduled == 0) null
        else dow to ((completedByDow[dow] ?: 0).toFloat() / scheduled)
    }.toMap()

    if (rateByDow.size < 5) return null // Need data for most weekdays

    val sortedByRate = rateByDow.entries.sortedByDescending { it.value }
    val top3Rate = sortedByRate.take(3).map { it.value }.average()
    val bottom2Rate = sortedByRate.takeLast(2).map { it.value }.average()

    if (top3Rate - bottom2Rate < 0.25) return null // Gap too small — don't call it a pattern

    // Build a human phrase for the top cluster
    val topDows = sortedByRate.take(3).map { DayOfWeek.of(it.key) }.sortedBy { it.value }
    return buildWeekdayClusterPhrase(topDows)
}

/**
 * Converts a sorted list of top weekdays into a calm cluster phrase.
 * Favors named clusters (midweek, weekdays, weekends) over listing days.
 */
private fun buildWeekdayClusterPhrase(topDows: List<DayOfWeek>): String? {
    val values = topDows.map { it.value }.toSet()
    return when {
        // Midweek: Tue, Wed, Thu
        values == setOf(2, 3, 4) ->
            "Consistency seems to flow more naturally in the middle of the week."
        // Weekend: Sat, Sun + one other
        values.containsAll(listOf(6, 7)) ->
            "Routines seem to hold steadier toward the weekend."
        // Weekday cluster: Mon-Fri contained
        values.all { it in 1..5 } ->
            "Weekdays tend to be where your rhythm stays most steady."
        // End of week: Thu, Fri, Sat
        values.containsAll(listOf(4, 5, 6)) ->
            "Late-week days seem to be where your rhythm is strongest."
        // Single strongest day mention (top 1 is significantly better)
        else -> {
            val strongest = topDows.firstOrNull() ?: return null
            val name = strongest.name.lowercase().replaceFirstChar { it.uppercase() }
            "${name}s seem to be a particularly consistent day for your routines."
        }
    }
}

/**
 * Detects recovery speed — if the user typically returns within 1–2 days after gaps.
 * Only fires when there are at least 2 measurable gaps and the average recovery is quick.
 */
private fun recoverySpeedObservation(
    recent: List<HabitCompletionEntity>,
    @Suppress("UNUSED_PARAMETER") today: LocalDate
): String? {
    val sortedDates = recent.map { it.completionDate }.distinct().sorted()
    if (sortedDates.size < 5) return null

    val gaps = mutableListOf<Long>()
    for (i in 1 until sortedDates.size) {
        val gap = ChronoUnit.DAYS.between(sortedDates[i - 1], sortedDates[i])
        if (gap >= 2) gaps.add(gap) // Only real gaps (≥ 2 days)
    }

    if (gaps.size < 2) return null

    val avgGap = gaps.average()
    return when {
        avgGap <= 2.5 ->
            "Short breaks don't seem to interrupt your rhythm for long."
        avgGap <= 4.0 ->
            "After a quieter day or two, you tend to find your way back steadily."
        else -> null // Gap too large — not a "quick recovery" pattern
    }
}

/**
 * Detects load sustainability — whether a smaller habit list correlates with
 * better consistency. Phrases as soft possibility, never as advice.
 */
private fun loadObservation(
    recent: List<HabitCompletionEntity>,
    habits: List<HabitEntity>,
    today: LocalDate
): String? {
    if (habits.size > 6) return null // Only relevant for focused lists

    // Compare last 14 days vs prior 14 days completion rate
    val last14 = recent.filter { !it.completionDate.isBefore(today.minusDays(13)) }
    val prior14 = recent.filter {
        !it.completionDate.isBefore(today.minusDays(27)) &&
        it.completionDate.isBefore(today.minusDays(13))
    }

    if (last14.isEmpty() || prior14.isEmpty()) return null

    val recentRate = last14.size.toFloat() / 14
    val priorRate = prior14.size.toFloat() / 14

    // If consistent completion rate is maintained with a small list, note it softly
    return if (recentRate >= 0.6f && habits.size <= 4) {
        "A focused routine seems to suit your rhythm well."
    } else if (recentRate > priorRate * 1.2f && habits.size <= 5) {
        "Smaller routines seem easier to sustain consistently."
    } else null
}
